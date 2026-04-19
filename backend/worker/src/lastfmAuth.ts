import type { Context } from 'hono'
import type { WorkerBindings } from './index'
import { signLastfmParams } from './lastfmSigning'

const CALLBACK_STATE_TTL_SECONDS = 15 * 60
const HANDOFF_PAYLOAD_TTL_SECONDS = 60
const HANDOFF_INFO = new TextEncoder().encode('discrobble-auth-handoff:v1')
const LASTFM_AUTHORIZE_URL = 'https://www.last.fm/api/auth/'
const LASTFM_API_URL = 'https://ws.audioscrobbler.com/2.0/'

interface LastfmAuthStartRequest {
  callback_url: string
  device_public_key: string
  platform: 'ios' | 'android'
}

interface SignedAuthState {
  callback_url: string
  device_public_key: string
  expires_at: string
  issued_at: string
  platform: 'ios' | 'android'
  provider: 'lastfm'
}

interface LastfmSessionResponse {
  session?: {
    key?: string
    name?: string
  }
  error?: number
  message?: string
}

interface LastfmEncryptedEnvelope {
  alg: 'ECDH-P256+HKDF-SHA256+A256GCM'
  ciphertext: string
  epk: string
  iv: string
  salt: string
}

interface LastfmHandoffPayload {
  expires_at: string
  issued_at: string
  provider: 'lastfm'
  session_key: string
  username: string
}

type WorkerContext = Context<{ Bindings: WorkerBindings }>

class CallbackRedirectError extends Error {
  constructor(
    readonly code: string,
    message: string,
  ) {
    super(message)
  }
}

export async function handleLastfmAuthStart(c: WorkerContext) {
  const apiKey = requireBinding(c, 'LASTFM_API_KEY')
  const stateSecret = requireBinding(c, 'AUTH_STATE_SECRET')

  let payload: LastfmAuthStartRequest

  try {
    payload = await c.req.json<LastfmAuthStartRequest>()
  } catch {
    return jsonError(c, 400, 'auth_start_failed', 'The auth request body must be valid JSON.')
  }

  if (payload.platform !== 'ios' && payload.platform !== 'android') {
    return jsonError(c, 400, 'invalid_platform', 'The auth request must declare either ios or android.')
  }

  let callbackUrl: URL

  try {
    callbackUrl = validateCallbackUrl(payload.callback_url)
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

  const now = new Date()
  const state: SignedAuthState = {
    callback_url: callbackUrl.toString(),
    device_public_key: payload.device_public_key,
    expires_at: new Date(now.getTime() + resolveSeconds(c, 'AUTH_STATE_TTL_SECONDS', CALLBACK_STATE_TTL_SECONDS) * 1000).toISOString(),
    issued_at: now.toISOString(),
    platform: payload.platform,
    provider: 'lastfm',
  }
  const signedState = await signState(state, stateSecret)
  const callback = new URL('/auth/lastfm/callback', c.req.url)
  callback.searchParams.set('state', signedState)

  const authorizeUrl = new URL(LASTFM_AUTHORIZE_URL)
  authorizeUrl.searchParams.set('api_key', apiKey)
  authorizeUrl.searchParams.set('cb', callback.toString())

  return c.json(
    {
      authorize_url: authorizeUrl.toString(),
    },
    200,
    {
      'Cache-Control': 'no-store',
    },
  )
}

export async function handleLastfmAuthCallback(c: WorkerContext) {
  const stateToken = c.req.query('state')

  if (!stateToken) {
    return jsonError(c, 400, 'invalid_state', 'The Last.fm callback is missing the signed auth state.')
  }

  let state: SignedAuthState

  try {
    state = await verifyState(stateToken, requireBinding(c, 'AUTH_STATE_SECRET'))
  } catch (error) {
    return jsonError(c, 400, 'invalid_state', asErrorMessage(error))
  }

  try {
    const token = c.req.query('token')

    if (!token) {
      throw new CallbackRedirectError(
        'provider_denied',
        'Last.fm did not return an auth token. Retry the browser approval flow.',
      )
    }

    const session = await exchangeLastfmSession({
      apiKey: requireBinding(c, 'LASTFM_API_KEY'),
      apiSecret: requireBinding(c, 'LASTFM_API_SECRET'),
      token,
    })
    const now = new Date()
    const payload: LastfmHandoffPayload = {
      expires_at: new Date(
        now.getTime() + resolveSeconds(c, 'AUTH_PAYLOAD_TTL_SECONDS', HANDOFF_PAYLOAD_TTL_SECONDS) * 1000,
      ).toISOString(),
      issued_at: now.toISOString(),
      provider: 'lastfm',
      session_key: session.key,
      username: session.name,
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
        error_code: 'provider_exchange_failed',
        error_message: 'Discrobble could not exchange the Last.fm auth token for a session.',
      }),
      302,
    )
  }
}

function buildAppCallback(
  callbackUrl: string,
  fragmentValues: Record<string, string>,
): string {
  const redirect = new URL(callbackUrl)
  redirect.hash = new URLSearchParams(fragmentValues).toString()
  return redirect.toString()
}

async function encryptPayload(
  plainText: string,
  devicePublicKeyPem: string,
): Promise<string> {
  const devicePublicKey = await importDevicePublicKey(devicePublicKeyPem)
  const ephemeralKeyPair = await crypto.subtle.generateKey(
    {
      name: 'ECDH',
      namedCurve: 'P-256',
    },
    true,
    ['deriveBits'],
  )
  const sharedSecret = await crypto.subtle.deriveBits(
    {
      name: 'ECDH',
      public: devicePublicKey,
    },
    ephemeralKeyPair.privateKey,
    256,
  )
  const salt = crypto.getRandomValues(new Uint8Array(32))
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const aesKey = await deriveAesKey(sharedSecret, salt)
  const encrypted = await crypto.subtle.encrypt(
    {
      name: 'AES-GCM',
      iv,
    },
    aesKey,
    new TextEncoder().encode(plainText),
  )
  const exportedPublicKey = await crypto.subtle.exportKey('spki', ephemeralKeyPair.publicKey)
  const envelope: LastfmEncryptedEnvelope = {
    alg: 'ECDH-P256+HKDF-SHA256+A256GCM',
    ciphertext: encodeBase64Url(new Uint8Array(encrypted)),
    epk: formatPublicKeyPem(exportedPublicKey),
    iv: encodeBase64Url(iv),
    salt: encodeBase64Url(salt),
  }

  return JSON.stringify(envelope)
}

async function exchangeLastfmSession(input: {
  apiKey: string
  apiSecret: string
  token: string
}): Promise<{ key: string; name: string }> {
  const params = new URLSearchParams({
    api_key: input.apiKey,
    api_sig: signLastfmParams({
      api_key: input.apiKey,
      method: 'auth.getSession',
      token: input.token,
    }, input.apiSecret),
    format: 'json',
    method: 'auth.getSession',
    token: input.token,
  })
  const response = await fetch(LASTFM_API_URL, {
    body: params.toString(),
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    method: 'POST',
  })

  if (!response.ok) {
    throw new CallbackRedirectError(
      'provider_exchange_failed',
      'Last.fm did not accept the auth exchange request.',
    )
  }

  const body = await response.json() as LastfmSessionResponse

  if (body.error || !body.session?.key || !body.session.name) {
    const errorCode = body.error

    if (errorCode === 4 || errorCode === 14 || errorCode === 15) {
      throw new CallbackRedirectError(
        'provider_denied',
        body.message ?? 'Last.fm declined the requested auth token.',
      )
    }

    throw new CallbackRedirectError(
      'provider_exchange_failed',
      body.message ?? 'Last.fm did not return a usable session.',
    )
  }

  return {
    key: body.session.key,
    name: body.session.name,
  }
}

function formatPublicKeyPem(spki: ArrayBuffer): string {
  const base64 = encodeBase64(new Uint8Array(spki))
  const wrapped = base64.match(/.{1,64}/g)?.join('\n') ?? base64
  return `-----BEGIN PUBLIC KEY-----\n${wrapped}\n-----END PUBLIC KEY-----`
}

function jsonError(
  c: WorkerContext,
  status: 400 | 401 | 500 | 502,
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

async function deriveAesKey(
  sharedSecret: ArrayBuffer,
  salt: Uint8Array,
): Promise<CryptoKey> {
  const hkdfKey = await crypto.subtle.importKey(
    'raw',
    sharedSecret,
    'HKDF',
    false,
    ['deriveKey'],
  )

  return crypto.subtle.deriveKey(
    {
      name: 'HKDF',
      hash: 'SHA-256',
      info: HANDOFF_INFO,
      salt: toArrayBuffer(salt),
    },
    hkdfKey,
    {
      name: 'AES-GCM',
      length: 256,
    },
    false,
    ['encrypt'],
  )
}

async function importDevicePublicKey(devicePublicKeyPem: string): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    'spki',
    parsePublicKeyPem(devicePublicKeyPem),
    {
      name: 'ECDH',
      namedCurve: 'P-256',
    },
    true,
    [],
  )
}

function parsePublicKeyPem(pem: string): ArrayBuffer {
  const normalized = pem
    .replace(/-----BEGIN PUBLIC KEY-----/g, '')
    .replace(/-----END PUBLIC KEY-----/g, '')
    .replace(/\s+/g, '')

  if (!normalized) {
    throw new Error('The auth request must include a valid P-256 public key in PEM format.')
  }

  return toArrayBuffer(decodeBase64(normalized))
}

function requireBinding(
  c: WorkerContext,
  key: keyof WorkerBindings,
): string {
  const value = c.env[key]

  if (!value) {
    throw new Error(`Missing Worker binding: ${key}`)
  }

  return value
}

function resolveSeconds(
  c: WorkerContext,
  key: 'AUTH_STATE_TTL_SECONDS' | 'AUTH_PAYLOAD_TTL_SECONDS',
  fallback: number,
): number {
  const raw = c.env[key]
  const value = raw ? Number.parseInt(raw, 10) : fallback
  return Number.isFinite(value) && value > 0 ? value : fallback
}

async function signState(
  state: SignedAuthState,
  secret: string,
): Promise<string> {
  const payloadSegment = encodeBase64Url(new TextEncoder().encode(JSON.stringify(state)))
  const signingKey = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    {
      name: 'HMAC',
      hash: 'SHA-256',
    },
    false,
    ['sign'],
  )
  const signature = await crypto.subtle.sign(
    'HMAC',
    signingKey,
    new TextEncoder().encode(payloadSegment),
  )

  return `${payloadSegment}.${encodeBase64Url(new Uint8Array(signature))}`
}

function validateCallbackUrl(rawCallbackUrl: string): URL {
  let callbackUrl: URL

  try {
    callbackUrl = new URL(rawCallbackUrl)
  } catch {
    throw new Error('The auth request must include a valid callback URL.')
  }

  if (callbackUrl.hash) {
    throw new Error('The callback URL must not include a fragment.')
  }

  if (callbackUrl.protocol === 'discrobble:') {
    const providerPath = callbackUrl.pathname.replace(/^\/+/, '').toLowerCase()

    if (callbackUrl.hostname.toLowerCase() !== 'auth' || providerPath !== 'lastfm') {
      throw new Error('The callback URL must target discrobble://auth/lastfm for the Last.fm spike.')
    }

    return callbackUrl
  }

  if (callbackUrl.protocol !== 'https:') {
    throw new Error('The callback URL must use either the discrobble or https scheme.')
  }

  return callbackUrl
}

async function verifyState(
  stateToken: string,
  secret: string,
): Promise<SignedAuthState> {
  const [payloadSegment, signatureSegment] = stateToken.split('.')

  if (!payloadSegment || !signatureSegment) {
    throw new Error('The auth state is malformed.')
  }

  const expected = await signStatePayload(payloadSegment, secret)

  if (!timingSafeEqual(signatureSegment, expected)) {
    throw new Error('The auth state signature is invalid.')
  }

  const payload = JSON.parse(
    new TextDecoder().decode(decodeBase64Url(payloadSegment)),
  ) as SignedAuthState

  if (payload.provider !== 'lastfm') {
    throw new Error('The auth state targets an unsupported provider.')
  }

  if (Date.parse(payload.expires_at) <= Date.now()) {
    throw new Error('The auth state has expired. Restart the Last.fm auth flow.')
  }

  return payload
}

async function signStatePayload(
  payloadSegment: string,
  secret: string,
): Promise<string> {
  const signingKey = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    {
      name: 'HMAC',
      hash: 'SHA-256',
    },
    false,
    ['sign'],
  )
  const signature = await crypto.subtle.sign(
    'HMAC',
    signingKey,
    new TextEncoder().encode(payloadSegment),
  )

  return encodeBase64Url(new Uint8Array(signature))
}

function timingSafeEqual(left: string, right: string): boolean {
  if (left.length !== right.length) {
    return false
  }

  let difference = 0

  for (let index = 0; index < left.length; index += 1) {
    difference |= left.charCodeAt(index) ^ right.charCodeAt(index)
  }

  return difference === 0
}

function asErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'An unexpected error occurred.'
}

function encodeBase64(bytes: Uint8Array): string {
  let binary = ''

  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }

  return btoa(binary)
}

function encodeBase64Url(bytes: Uint8Array): string {
  return encodeBase64(bytes)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
}

function decodeBase64(base64: string): Uint8Array {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)

  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }

  return bytes
}

function decodeBase64Url(base64Url: string): Uint8Array {
  const normalized = base64Url
    .replace(/-/g, '+')
    .replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')

  return decodeBase64(padded)
}

function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
}
