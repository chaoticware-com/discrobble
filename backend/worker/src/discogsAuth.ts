import {
  CALLBACK_STATE_TTL_SECONDS,
  HANDOFF_PAYLOAD_TTL_SECONDS,
  type AuthStartRequest,
  type SignedAuthState,
  type WorkerContext,
  asErrorMessage,
  buildAppCallback,
  decodeBase64Url,
  encryptPayload,
  encodeBase64Url,
  importDevicePublicKey,
  requireBinding,
  resolveAllowedHttpsCallbackPrefixes,
  resolveSeconds,
  signState,
  toArrayBuffer,
  validateCallbackUrl,
  verifyState,
} from './authFlow'
import { buildDiscogsAuthorizationHeader, DISCOGS_USER_AGENT } from './discogsOAuth'

const DISCOGS_ACCESS_TOKEN_URL = 'https://api.discogs.com/oauth/access_token'
const DISCOGS_AUTHORIZE_URL = 'https://www.discogs.com/oauth/authorize'
const DISCOGS_REQUEST_TOKEN_URL = 'https://api.discogs.com/oauth/request_token'
const AUTH_CONTEXT_COOKIE_NAME = 'discrobble_discogs_auth'
const COOKIE_KEY_INFO = new TextEncoder().encode('discrobble-discogs-auth-cookie:v1')
const COOKIE_KEY_SALT = new TextEncoder().encode('discrobble-discogs-auth-cookie-salt:v1')

type DiscogsSignedAuthState = SignedAuthState<'discogs'>

interface DiscogsAccessTokenPayload {
  expires_at: string
  issued_at: string
  oauth_token: string
  oauth_token_secret: string
  provider: 'discogs'
  username: string
}

interface DiscogsAuthContextCookie {
  expires_at: string
  request_token: string
  request_token_secret: string
}

interface DiscogsOAuthTokenResponse {
  oauth_callback_confirmed?: string
  oauth_token?: string
  oauth_token_secret?: string
  oauth_problem?: string
  oauth_problem_advice?: string
  username?: string
}

class CallbackRedirectError extends Error {
  constructor(
    readonly code: string,
    message: string,
  ) {
    super(message)
  }
}

export async function handleDiscogsAuthStart(c: WorkerContext) {
  const stateSecret = requireBinding(c, 'AUTH_STATE_SECRET')

  const payload: AuthStartRequest = {
    callback_url: c.req.query('callback_url')?.trim() ?? '',
    device_public_key: c.req.query('device_public_key')?.trim() ?? '',
    platform: (c.req.query('platform')?.trim().toLowerCase() ?? '') as AuthStartRequest['platform'],
  }

  if (payload.platform !== 'ios' && payload.platform !== 'android') {
    return jsonError(c, 400, 'invalid_platform', 'The auth request must declare either ios or android.')
  }

  let callbackUrl: URL

  try {
    callbackUrl = validateCallbackUrl(
      payload.callback_url,
      'discogs',
      resolveAllowedHttpsCallbackPrefixes(c),
    )
  } catch (error) {
    return jsonError(c, 400, 'invalid_callback_url', asErrorMessage(error))
  }

  try {
    await importDevicePublicKey(payload.device_public_key)
  } catch {
    return jsonError(
      c,
      400,
      'invalid_device_public_key',
      'The auth request must include a valid P-256 public key in PEM format.',
    )
  }

  try {
    const now = new Date()
    const state: DiscogsSignedAuthState = {
      callback_url: callbackUrl.toString(),
      device_public_key: payload.device_public_key,
      expires_at: new Date(
        now.getTime() + resolveSeconds(c, 'AUTH_STATE_TTL_SECONDS', CALLBACK_STATE_TTL_SECONDS) * 1000,
      ).toISOString(),
      issued_at: now.toISOString(),
      platform: payload.platform,
      provider: 'discogs',
    }
    const signedState = await signState(state, stateSecret)
    const callback = new URL('/auth/discogs/callback', c.req.url)
    callback.searchParams.set('state', signedState)
    const requestToken = await requestDiscogsRequestToken({
      callbackUrl: callback.toString(),
      consumerKey: requireBinding(c, 'DISCOGS_CONSUMER_KEY'),
      consumerSecret: requireBinding(c, 'DISCOGS_CONSUMER_SECRET'),
    })
    const authorizeUrl = new URL(DISCOGS_AUTHORIZE_URL)
    authorizeUrl.searchParams.set('oauth_token', requestToken.oauth_token)

    c.header(
      'Set-Cookie',
      await createAuthContextCookie(
        c,
        stateSecret,
        {
          expires_at: state.expires_at,
          request_token: requestToken.oauth_token,
          request_token_secret: requestToken.oauth_token_secret,
        },
      ),
    )
    c.header('Cache-Control', 'no-store')

    return c.redirect(authorizeUrl.toString(), 302)
  } catch (error) {
    return jsonError(
      c,
      502,
      'request_token_failed',
      asErrorMessage(error) || 'Discrobble could not obtain a Discogs request token.',
    )
  }
}

export async function handleDiscogsAuthCallback(c: WorkerContext) {
  const stateToken = c.req.query('state')

  if (!stateToken) {
    return jsonError(c, 400, 'invalid_state', 'The Discogs callback is missing the signed auth state.')
  }

  let state: DiscogsSignedAuthState

  try {
    state = await verifyState<DiscogsSignedAuthState>(
      stateToken,
      requireBinding(c, 'AUTH_STATE_SECRET'),
      'discogs',
    )
  } catch (error) {
    return jsonError(c, 400, 'invalid_state', asErrorMessage(error))
  }

  c.header('Set-Cookie', clearAuthContextCookie(c))
  c.header('Cache-Control', 'no-store')

  try {
    if (c.req.query('denied')) {
      throw new CallbackRedirectError(
        'provider_denied',
        'Discogs approval was denied. Retry the browser approval flow.',
      )
    }

    const oauthToken = c.req.query('oauth_token')
    const oauthVerifier = c.req.query('oauth_verifier')

    if (!oauthToken || !oauthVerifier) {
      throw new CallbackRedirectError(
        'provider_denied',
        'Discogs did not return an authorized request token and verifier.',
      )
    }

    const authContext = await readAuthContextCookie(
      c,
      requireBinding(c, 'AUTH_STATE_SECRET'),
    )

    if (!authContext || authContext.request_token !== oauthToken) {
      throw new CallbackRedirectError(
        'invalid_auth_context',
        'The Discogs auth flow no longer has a valid request-token secret. Restart auth from the app.',
      )
    }

    if (Date.parse(authContext.expires_at) <= Date.now()) {
      throw new CallbackRedirectError(
        'invalid_auth_context',
        'The Discogs auth flow expired before completion. Restart auth from the app.',
      )
    }

    const accessToken = await exchangeDiscogsAccessToken({
      consumerKey: requireBinding(c, 'DISCOGS_CONSUMER_KEY'),
      consumerSecret: requireBinding(c, 'DISCOGS_CONSUMER_SECRET'),
      oauthToken,
      oauthTokenSecret: authContext.request_token_secret,
      oauthVerifier,
    })
    const now = new Date()
    const payload: DiscogsAccessTokenPayload = {
      expires_at: new Date(
        now.getTime() + resolveSeconds(c, 'AUTH_PAYLOAD_TTL_SECONDS', HANDOFF_PAYLOAD_TTL_SECONDS) * 1000,
      ).toISOString(),
      issued_at: now.toISOString(),
      oauth_token: accessToken.oauth_token,
      oauth_token_secret: accessToken.oauth_token_secret,
      provider: 'discogs',
      username: accessToken.username,
    }
    const encryptedPayload = await encryptPayload(
      JSON.stringify(payload),
      state.device_public_key,
    )

    return c.redirect(buildAppCallback(state.callback_url, { payload: encryptedPayload }), 302)
  } catch (error) {
    if (error instanceof CallbackRedirectError) {
      return c.redirect(
        buildAppCallback(state.callback_url, {
          error_code: error.code,
          error_message: error.message,
        }),
        302,
      )
    }

    return c.redirect(
      buildAppCallback(state.callback_url, {
        error_code: 'access_token_failed',
        error_message: 'Discrobble could not exchange the Discogs request token for an access token.',
      }),
      302,
    )
  }
}

async function createAuthContextCookie(
  c: WorkerContext,
  secret: string,
  context: DiscogsAuthContextCookie,
): Promise<string> {
  const cookieValue = await encryptCookiePayload(JSON.stringify(context), secret)

  return serializeCookie(AUTH_CONTEXT_COOKIE_NAME, cookieValue, {
    httpOnly: true,
    maxAge: resolveSeconds(c, 'AUTH_STATE_TTL_SECONDS', CALLBACK_STATE_TTL_SECONDS),
    path: '/auth/discogs/callback',
    sameSite: 'Lax',
    secure: shouldUseSecureCookies(c),
  })
}

function clearAuthContextCookie(c: WorkerContext): string {
  return serializeCookie(AUTH_CONTEXT_COOKIE_NAME, '', {
    httpOnly: true,
    maxAge: 0,
    path: '/auth/discogs/callback',
    sameSite: 'Lax',
    secure: shouldUseSecureCookies(c),
  })
}

async function decryptCookiePayload(
  value: string,
  secret: string,
): Promise<string> {
  const [ivSegment, ciphertextSegment] = value.split('.')

  if (!ivSegment || !ciphertextSegment) {
    throw new Error('The encrypted cookie is malformed.')
  }

  const key = await deriveCookieKey(secret)
  const decrypted = await crypto.subtle.decrypt(
    {
      name: 'AES-GCM',
      iv: toArrayBuffer(decodeBase64Url(ivSegment)),
    },
    key,
    toArrayBuffer(decodeBase64Url(ciphertextSegment)),
  )

  return new TextDecoder().decode(decrypted)
}

async function deriveCookieKey(secret: string): Promise<CryptoKey> {
  const hkdfKey = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    'HKDF',
    false,
    ['deriveKey'],
  )

  return crypto.subtle.deriveKey(
    {
      name: 'HKDF',
      hash: 'SHA-256',
      info: toArrayBuffer(COOKIE_KEY_INFO),
      salt: toArrayBuffer(COOKIE_KEY_SALT),
    },
    hkdfKey,
    {
      name: 'AES-GCM',
      length: 256,
    },
    false,
    ['decrypt', 'encrypt'],
  )
}

async function encryptCookiePayload(
  plainText: string,
  secret: string,
): Promise<string> {
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const key = await deriveCookieKey(secret)
  const encrypted = await crypto.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv,
    },
    key,
    new TextEncoder().encode(plainText),
  )

  return `${encodeBase64Url(iv)}.${encodeBase64Url(new Uint8Array(encrypted))}`
}

async function exchangeDiscogsAccessToken(input: {
  consumerKey: string
  consumerSecret: string
  oauthToken: string
  oauthTokenSecret: string
  oauthVerifier: string
}): Promise<Required<Pick<DiscogsOAuthTokenResponse, 'oauth_token' | 'oauth_token_secret' | 'username'>>> {
  const authorization = await buildDiscogsAuthorizationHeader({
    consumerKey: input.consumerKey,
    consumerSecret: input.consumerSecret,
    extraOauthParams: {
      oauth_verifier: input.oauthVerifier,
    },
    method: 'POST',
    token: input.oauthToken,
    tokenSecret: input.oauthTokenSecret,
    url: DISCOGS_ACCESS_TOKEN_URL,
  })
  const response = await fetch(DISCOGS_ACCESS_TOKEN_URL, {
    headers: {
      Authorization: authorization,
      'User-Agent': DISCOGS_USER_AGENT,
    },
    method: 'POST',
  })
  const responseText = await response.text()
  const parsed = parseDiscogsTokenResponse(responseText)

  if (!response.ok || !parsed.oauth_token || !parsed.oauth_token_secret || !parsed.username) {
    throw new CallbackRedirectError(
      'access_token_failed',
      discogsFailureMessage(
        parsed,
        'Discogs did not return a usable access token pair.',
      ),
    )
  }

  return {
    oauth_token: parsed.oauth_token,
    oauth_token_secret: parsed.oauth_token_secret,
    username: parsed.username,
  }
}

function jsonError(
  c: WorkerContext,
  status: 400 | 401 | 502,
  code: string,
  message: string,
) {
  return c.json(
    {
      error: {
        code,
        message,
      },
    },
    status,
    {
      'Cache-Control': 'no-store',
    },
  )
}

function parseCookies(rawCookieHeader: string | undefined): Map<string, string> {
  const cookies = new Map<string, string>()

  for (const part of rawCookieHeader?.split(';') ?? []) {
    const separatorIndex = part.indexOf('=')

    if (separatorIndex <= 0) {
      continue
    }

    const name = part.slice(0, separatorIndex).trim()
    const value = part.slice(separatorIndex + 1).trim()

    if (name) {
      cookies.set(name, value)
    }
  }

  return cookies
}

function parseDiscogsTokenResponse(responseText: string): DiscogsOAuthTokenResponse {
  const params = new URLSearchParams(responseText)

  return {
    oauth_callback_confirmed: params.get('oauth_callback_confirmed') ?? undefined,
    oauth_problem: params.get('oauth_problem') ?? undefined,
    oauth_problem_advice: params.get('oauth_problem_advice') ?? undefined,
    oauth_token: params.get('oauth_token') ?? undefined,
    oauth_token_secret: params.get('oauth_token_secret') ?? undefined,
    username: params.get('username') ?? undefined,
  }
}

async function readAuthContextCookie(
  c: WorkerContext,
  secret: string,
): Promise<DiscogsAuthContextCookie | null> {
  const cookies = parseCookies(c.req.header('Cookie'))
  const rawValue = cookies.get(AUTH_CONTEXT_COOKIE_NAME)

  if (!rawValue) {
    return null
  }

  try {
    return JSON.parse(await decryptCookiePayload(rawValue, secret)) as DiscogsAuthContextCookie
  } catch {
    return null
  }
}

async function requestDiscogsRequestToken(input: {
  callbackUrl: string
  consumerKey: string
  consumerSecret: string
}): Promise<Required<Pick<DiscogsOAuthTokenResponse, 'oauth_token' | 'oauth_token_secret'>>> {
  const authorization = await buildDiscogsAuthorizationHeader({
    consumerKey: input.consumerKey,
    consumerSecret: input.consumerSecret,
    extraOauthParams: {
      oauth_callback: input.callbackUrl,
    },
    method: 'POST',
    url: DISCOGS_REQUEST_TOKEN_URL,
  })
  const response = await fetch(DISCOGS_REQUEST_TOKEN_URL, {
    headers: {
      Authorization: authorization,
      'User-Agent': DISCOGS_USER_AGENT,
    },
    method: 'POST',
  })
  const responseText = await response.text()
  const parsed = parseDiscogsTokenResponse(responseText)

  if (
    !response.ok
    || !parsed.oauth_token
    || !parsed.oauth_token_secret
    || parsed.oauth_callback_confirmed?.toLowerCase() !== 'true'
  ) {
    throw new Error(
      discogsFailureMessage(
        parsed,
        'Discrobble could not obtain a Discogs request token.',
      ),
    )
  }

  return {
    oauth_token: parsed.oauth_token,
    oauth_token_secret: parsed.oauth_token_secret,
  }
}

function serializeCookie(
  name: string,
  value: string,
  options: {
    httpOnly?: boolean
    maxAge?: number
    path?: string
    sameSite?: 'Lax' | 'Strict'
    secure?: boolean
  },
): string {
  const parts = [`${name}=${value}`]

  if (options.maxAge !== undefined) {
    parts.push(`Max-Age=${options.maxAge}`)
  }

  if (options.path) {
    parts.push(`Path=${options.path}`)
  }

  if (options.sameSite) {
    parts.push(`SameSite=${options.sameSite}`)
  }

  if (options.httpOnly) {
    parts.push('HttpOnly')
  }

  if (options.secure) {
    parts.push('Secure')
  }

  return parts.join('; ')
}

function shouldUseSecureCookies(c: WorkerContext): boolean {
  return new URL(c.req.url).protocol === 'https:'
}

function discogsFailureMessage(
  parsed: DiscogsOAuthTokenResponse,
  fallback: string,
): string {
  return parsed.oauth_problem_advice
    ?? parsed.oauth_problem
    ?? fallback
}
