import {
  CALLBACK_STATE_TTL_SECONDS,
  HANDOFF_PAYLOAD_TTL_SECONDS,
  type AuthStartRequest,
  type SignedAuthState,
  type WorkerContext,
  asErrorMessage,
  buildAppCallback,
  encryptPayload,
  importDevicePublicKey,
  requireBinding,
  resolveAllowedHttpsCallbackPrefixes,
  resolveSeconds,
  signState,
  validateCallbackUrl,
  verifyState,
} from './authFlow'
import { signLastfmParams } from './lastfmSigning'

const LASTFM_API_URL = 'https://ws.audioscrobbler.com/2.0/'
const LASTFM_AUTHORIZE_URL = 'https://www.last.fm/api/auth/'

type LastfmSignedAuthState = SignedAuthState<'lastfm'>

interface LastfmHandoffPayload {
  expires_at: string
  issued_at: string
  provider: 'lastfm'
  session_key: string
  username: string
}

interface LastfmSessionResponse {
  error?: number
  message?: string
  session?: {
    key?: string
    name?: string
  }
}

class CallbackRedirectError extends Error {
  constructor(
    readonly code: string,
    message: string,
  ) {
    super(message)
  }
}

export async function handleLastfmAuthStart(c: WorkerContext) {
  let payload: AuthStartRequest

  try {
    payload = await c.req.json<AuthStartRequest>()
  } catch {
    return jsonError(c, 400, 'invalid_payload', 'The auth request body must be valid JSON.')
  }

  if (payload.platform !== 'ios' && payload.platform !== 'android') {
    return jsonError(c, 400, 'invalid_platform', 'The auth request must declare either ios or android.')
  }

  let callbackUrl: URL

  try {
    callbackUrl = validateCallbackUrl(
      payload.callback_url,
      'lastfm',
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
    const apiKey = requireBinding(c, 'LASTFM_API_KEY')
    const stateSecret = requireBinding(c, 'AUTH_STATE_SECRET')
    const now = new Date()
    const state: LastfmSignedAuthState = {
      callback_url: callbackUrl.toString(),
      device_public_key: payload.device_public_key,
      expires_at: new Date(
        now.getTime() + resolveSeconds(c, 'AUTH_STATE_TTL_SECONDS', CALLBACK_STATE_TTL_SECONDS) * 1000,
      ).toISOString(),
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
  } catch {
    return jsonError(
      c,
      500,
      'auth_start_failed',
      'Discrobble could not bootstrap the Last.fm auth flow.',
    )
  }
}

export async function handleLastfmAuthCallback(c: WorkerContext) {
  const stateToken = c.req.query('state')

  if (!stateToken) {
    return jsonError(c, 400, 'invalid_state', 'The Last.fm callback is missing the signed auth state.')
  }

  let state: LastfmSignedAuthState

  try {
    state = await verifyState<LastfmSignedAuthState>(
      stateToken,
      requireBinding(c, 'AUTH_STATE_SECRET'),
      'lastfm',
    )
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
