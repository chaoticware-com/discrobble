import type { Context } from 'hono'
import type { WorkerBindings } from './index'
import { signLastfmParams } from './lastfmSigning'

const LASTFM_API_URL = 'https://ws.audioscrobbler.com/2.0/'

interface LastfmNowPlayingRequest {
  album?: string
  album_artist?: string
  artist: string
  chosen_by_user?: boolean
  duration_seconds?: number
  release_id?: number
  track: string
  track_number?: number
}

interface LastfmScrobbleRequest extends LastfmNowPlayingRequest {
  timestamp_started_at: number
}

type WorkerContext = Context<{ Bindings: WorkerBindings }>

type WriteErrorCode =
  | 'invalid_payload'
  | 'lastfm_session_invalid'
  | 'lastfm_unavailable'
  | 'missing_lastfm_session'
  | 'scrobble_not_yet_eligible'

type LastfmErrorStatus = 400 | 401 | 409 | 502

interface ParsedLastfmError {
  code: number
  message: string
}

type ParsedLastfmResponse =
  | {
    kind: 'error'
    value: ParsedLastfmError
  }
  | {
    kind: 'ok'
    value: string
  }

export async function handleLastfmNowPlaying(c: WorkerContext) {
  const sessionKey = c.req.header('X-Lastfm-Session-Key')?.trim()

  if (!sessionKey) {
    return jsonError(c, 401, 'missing_lastfm_session', 'The Last.fm session key header is required.')
  }

  let payload: LastfmNowPlayingRequest

  try {
    payload = validateNowPlayingRequest(await c.req.json<unknown>())
  } catch (error) {
    return jsonError(c, 400, 'invalid_payload', asErrorMessage(error))
  }

  try {
    const xmlResponse = await postLastfmWrite(
      c,
      'track.updateNowPlaying',
      sessionKey,
      buildNowPlayingParams(payload),
    )
    const parsed = parseLastfmResponse(xmlResponse)

    if (parsed.kind === 'error') {
      const mapped = mapLastfmWriteError(parsed.value)
      return jsonError(c, mapped.status, mapped.code, mapped.message)
    }

    return c.json(
      {
        ignored: extractIgnoredCount(parsed.value) > 0,
        status: 'ok',
      },
      200,
      {
        'Cache-Control': 'no-store',
      },
    )
  } catch (error) {
    return jsonError(
      c,
      502,
      'lastfm_unavailable',
      'Discrobble could not reach Last.fm for the now playing request.',
    )
  }
}

export async function handleLastfmScrobble(c: WorkerContext) {
  const sessionKey = c.req.header('X-Lastfm-Session-Key')?.trim()

  if (!sessionKey) {
    return jsonError(c, 401, 'missing_lastfm_session', 'The Last.fm session key header is required.')
  }

  let payload: LastfmScrobbleRequest

  try {
    payload = validateScrobbleRequest(await c.req.json<unknown>())
  } catch (error) {
    return jsonError(c, 400, 'invalid_payload', asErrorMessage(error))
  }

  const eligibilityError = validateScrobbleEligibility(payload)

  if (eligibilityError) {
    return jsonError(c, 409, 'scrobble_not_yet_eligible', eligibilityError)
  }

  try {
    const xmlResponse = await postLastfmWrite(
      c,
      'track.scrobble',
      sessionKey,
      buildScrobbleParams(payload),
    )
    const parsed = parseLastfmResponse(xmlResponse)

    if (parsed.kind === 'error') {
      const mapped = mapLastfmWriteError(parsed.value)
      return jsonError(c, mapped.status, mapped.code, mapped.message)
    }

    return c.json(
      {
        accepted: extractAcceptedCount(parsed.value),
        ignored: extractIgnoredCount(parsed.value),
        retryable: false,
        status: 'ok',
      },
      200,
      {
        'Cache-Control': 'no-store',
      },
    )
  } catch {
    return jsonError(
      c,
      502,
      'lastfm_unavailable',
      'Discrobble could not reach Last.fm for the scrobble request.',
    )
  }
}

function buildNowPlayingParams(payload: LastfmNowPlayingRequest): Record<string, string> {
  return compactRecord({
    album: payload.album,
    albumArtist: payload.album_artist,
    artist: payload.artist,
    duration: formatOptionalNumber(payload.duration_seconds),
    track: payload.track,
    trackNumber: formatOptionalNumber(payload.track_number),
  })
}

function buildScrobbleParams(payload: LastfmScrobbleRequest): Record<string, string> {
  return compactRecord({
    album: payload.album,
    albumArtist: payload.album_artist,
    artist: payload.artist,
    chosenByUser: formatOptionalBoolean(payload.chosen_by_user),
    duration: formatOptionalNumber(payload.duration_seconds),
    timestamp: payload.timestamp_started_at.toString(),
    track: payload.track,
    trackNumber: formatOptionalNumber(payload.track_number),
  })
}

function compactRecord(input: Record<string, string | undefined>): Record<string, string> {
  const output: Record<string, string> = {}

  for (const [key, value] of Object.entries(input)) {
    if (value !== undefined) {
      output[key] = value
    }
  }

  return output
}

function extractAcceptedCount(xml: string): number {
  const match = xml.match(/<scrobbles\b[^>]*accepted=['"](\d+)['"]/i)
  return Number.parseInt(match?.[1] ?? '0', 10)
}

function extractIgnoredCount(xml: string): number {
  const scrobbleMatch = xml.match(/<scrobbles\b[^>]*ignored=['"](\d+)['"]/i)

  if (scrobbleMatch?.[1]) {
    return Number.parseInt(scrobbleMatch[1], 10)
  }

  const nowPlayingMatch = xml.match(/<ignoredmessage\b[^>]*code=['"](\d+)['"]/i)
  return Number.parseInt(nowPlayingMatch?.[1] ?? '0', 10)
}

function formatOptionalBoolean(value: boolean | undefined): string | undefined {
  if (value === undefined) {
    return undefined
  }

  return value ? '1' : '0'
}

function formatOptionalNumber(value: number | undefined): string | undefined {
  if (value === undefined) {
    return undefined
  }

  return value.toString()
}

function jsonError(
  c: WorkerContext,
  status: LastfmErrorStatus,
  code: WriteErrorCode,
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

function mapLastfmWriteError(error: ParsedLastfmError): {
  code: WriteErrorCode
  message: string
  status: LastfmErrorStatus
} {
  if (error.code === 4 || error.code === 9) {
    return {
      code: 'lastfm_session_invalid',
      message: 'The Last.fm session key is invalid. Re-authenticate and retry.',
      status: 401,
    }
  }

  if (
    error.code === 2
    || error.code === 3
    || error.code === 8
    || error.code === 10
    || error.code === 11
    || error.code === 13
    || error.code === 16
    || error.code === 26
    || error.code === 29
  ) {
    return {
      code: 'lastfm_unavailable',
      message: decodeXmlEntities(error.message) || 'Last.fm is temporarily unavailable.',
      status: 502,
    }
  }

  return {
    code: 'invalid_payload',
    message: decodeXmlEntities(error.message) || 'Last.fm rejected the request payload.',
    status: 400,
  }
}

function parseLastfmResponse(xml: string): ParsedLastfmResponse {
  const status = xml.match(/<lfm\b[^>]*status=['"]([^'"]+)['"]/i)?.[1]?.toLowerCase()

  if (status === 'ok') {
    return {
      kind: 'ok',
      value: xml,
    }
  }

  const errorMatch = xml.match(/<error\b[^>]*code=['"](\d+)['"]>([\s\S]*?)<\/error>/i)

  if (status === 'failed' && errorMatch?.[1]) {
    return {
      kind: 'error',
      value: {
        code: Number.parseInt(errorMatch[1], 10),
        message: decodeXmlEntities(errorMatch[2].trim()),
      },
    }
  }

  return {
    kind: 'error',
    value: {
      code: 8,
      message: 'Last.fm returned an unexpected response body.',
    },
  }
}

async function postLastfmWrite(
  c: WorkerContext,
  method: 'track.scrobble' | 'track.updateNowPlaying',
  sessionKey: string,
  params: Record<string, string>,
): Promise<string> {
  const apiKey = requireBinding(c, 'LASTFM_API_KEY')
  const apiSecret = requireBinding(c, 'LASTFM_API_SECRET')
  const signedParams = {
    ...params,
    api_key: apiKey,
    method,
    sk: sessionKey,
  }
  const form = new URLSearchParams({
    ...signedParams,
    api_sig: signLastfmParams(signedParams, apiSecret),
  })
  const response = await fetch(LASTFM_API_URL, {
    body: form.toString(),
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded; charset=utf-8',
    },
    method: 'POST',
  })

  return response.text()
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

function validateNowPlayingRequest(input: unknown): LastfmNowPlayingRequest {
  if (!isRecord(input)) {
    throw new Error('The now playing request body must be a JSON object.')
  }

  return {
    album: optionalTrimmedString(input.album, 'album'),
    album_artist: optionalTrimmedString(input.album_artist, 'album_artist'),
    artist: requiredTrimmedString(input.artist, 'artist'),
    chosen_by_user: optionalBoolean(input.chosen_by_user, 'chosen_by_user'),
    duration_seconds: optionalPositiveInteger(input.duration_seconds, 'duration_seconds'),
    release_id: optionalPositiveInteger(input.release_id, 'release_id'),
    track: requiredTrimmedString(input.track, 'track'),
    track_number: optionalPositiveInteger(input.track_number, 'track_number'),
  }
}

function validateScrobbleEligibility(payload: LastfmScrobbleRequest): string | null {
  const nowSeconds = Math.floor(Date.now() / 1000)

  if (payload.timestamp_started_at > nowSeconds) {
    return 'Scrobbles cannot be submitted before playback has started.'
  }

  if (payload.duration_seconds !== undefined) {
    if (payload.duration_seconds <= 30) {
      return 'Tracks shorter than or equal to 30 seconds must not be scrobbled.'
    }

    const minimumPlayedSeconds = Math.min(Math.ceil(payload.duration_seconds / 2), 240)
    const elapsedSeconds = nowSeconds - payload.timestamp_started_at

    if (elapsedSeconds < minimumPlayedSeconds) {
      return 'The track has not yet reached the Last.fm scrobble timing threshold.'
    }
  }

  return null
}

function validateScrobbleRequest(input: unknown): LastfmScrobbleRequest {
  if (!isRecord(input)) {
    throw new Error('The scrobble request body must be a JSON object.')
  }

  return {
    ...validateNowPlayingRequest(input),
    timestamp_started_at: requiredUnixTimestamp(input.timestamp_started_at),
  }
}

function requiredUnixTimestamp(value: unknown): number {
  if (!Number.isInteger(value) || typeof value !== 'number' || value <= 0) {
    throw new Error('The scrobble request must include a positive integer timestamp_started_at value.')
  }

  return value
}

function requiredTrimmedString(value: unknown, field: string): string {
  if (typeof value !== 'string' || value.trim().length === 0) {
    throw new Error(`The request must include a non-empty ${field} field.`)
  }

  return value.trim()
}

function optionalBoolean(value: unknown, field: string): boolean | undefined {
  if (value === undefined || value === null) {
    return undefined
  }

  if (typeof value !== 'boolean') {
    throw new Error(`The ${field} field must be a boolean when provided.`)
  }

  return value
}

function optionalPositiveInteger(value: unknown, field: string): number | undefined {
  if (value === undefined || value === null) {
    return undefined
  }

  if (typeof value !== 'number' || !Number.isInteger(value) || value <= 0) {
    throw new Error(`The ${field} field must be a positive integer when provided.`)
  }

  return value
}

function optionalTrimmedString(value: unknown, field: string): string | undefined {
  if (value === undefined || value === null) {
    return undefined
  }

  if (typeof value !== 'string') {
    throw new Error(`The ${field} field must be a string when provided.`)
  }

  const trimmed = value.trim()
  return trimmed.length > 0 ? trimmed : undefined
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function asErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'An unexpected error occurred.'
}

function decodeXmlEntities(value: string): string {
  return value
    .replace(/&amp;/g, '&')
    .replace(/&apos;/g, "'")
    .replace(/&gt;/g, '>')
    .replace(/&lt;/g, '<')
    .replace(/&quot;/g, '"')
}
