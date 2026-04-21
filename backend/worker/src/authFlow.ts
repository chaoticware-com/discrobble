import type { Context } from 'hono'
import type { WorkerBindings } from './index'

export const CALLBACK_STATE_TTL_SECONDS = 15 * 60
export const HANDOFF_PAYLOAD_TTL_SECONDS = 60

const HANDOFF_INFO = new TextEncoder().encode('discrobble-auth-handoff:v1')

export interface AuthStartRequest {
  callback_url: string
  device_public_key: string
  platform: 'ios' | 'android'
}

export interface EncryptedAuthEnvelope {
  alg: 'ECDH-P256+HKDF-SHA256+A256GCM'
  ciphertext: string
  epk: string
  iv: string
  salt: string
}

export interface SignedAuthState<TProvider extends string = string> {
  callback_url: string
  device_public_key: string
  expires_at: string
  issued_at: string
  platform: 'ios' | 'android'
  provider: TProvider
}

export type WorkerContext = Context<{ Bindings: WorkerBindings }>

export function buildAppCallback(
  callbackUrl: string,
  fragmentValues: Record<string, string>,
): string {
  const redirect = new URL(callbackUrl)
  redirect.hash = new URLSearchParams(fragmentValues).toString()
  return redirect.toString()
}

export async function encryptPayload(
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
  const envelope: EncryptedAuthEnvelope = {
    alg: 'ECDH-P256+HKDF-SHA256+A256GCM',
    ciphertext: encodeBase64Url(new Uint8Array(encrypted)),
    epk: formatPublicKeyPem(exportedPublicKey),
    iv: encodeBase64Url(iv),
    salt: encodeBase64Url(salt),
  }

  return JSON.stringify(envelope)
}

export async function signState<TState extends SignedAuthState>(
  state: TState,
  secret: string,
): Promise<string> {
  const payloadSegment = encodeBase64Url(new TextEncoder().encode(JSON.stringify(state)))
  const signature = await signStatePayload(payloadSegment, secret)
  return `${payloadSegment}.${signature}`
}

export async function verifyState<TState extends SignedAuthState>(
  stateToken: string,
  secret: string,
  provider: TState['provider'],
): Promise<TState> {
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
  ) as TState

  if (payload.provider !== provider) {
    throw new Error('The auth state targets an unsupported provider.')
  }

  if (Date.parse(payload.expires_at) <= Date.now()) {
    throw new Error(`The auth state has expired. Restart the ${providerLabel(provider)} auth flow.`)
  }

  return payload
}

export function validateCallbackUrl(
  rawCallbackUrl: string,
  provider: SignedAuthState['provider'],
  allowedHttpsCallbackPrefixes: URL[] = [],
): URL {
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

    if (callbackUrl.hostname.toLowerCase() !== 'auth' || providerPath !== provider) {
      throw new Error(
        `The callback URL must target discrobble://auth/${provider} for the ${providerLabel(provider)} spike.`,
      )
    }

    return callbackUrl
  }

  if (callbackUrl.protocol !== 'https:') {
    throw new Error('The callback URL must use either the discrobble or https scheme.')
  }

  if (!allowedHttpsCallbackPrefixes.some((prefix) => matchesAllowedHttpsCallbackPrefix(callbackUrl, prefix))) {
    throw new Error(
      `The callback URL must target discrobble://auth/${provider} or a configured first-party HTTPS callback prefix for the ${providerLabel(provider)} spike.`,
    )
  }

  return callbackUrl
}

export function resolveAllowedHttpsCallbackPrefixes(c: WorkerContext): URL[] {
  return (c.env.AUTH_ALLOWED_HTTPS_CALLBACK_PREFIXES ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
    .flatMap((value) => parseAllowedHttpsCallbackPrefix(value))
}

export async function importDevicePublicKey(devicePublicKeyPem: string): Promise<CryptoKey> {
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

export function requireBinding(
  c: WorkerContext,
  key: keyof WorkerBindings,
): string {
  const value = c.env[key]

  if (!value) {
    throw new Error(`Missing Worker binding: ${key}`)
  }

  return value
}

export function resolveSeconds(
  c: WorkerContext,
  key: 'AUTH_STATE_TTL_SECONDS' | 'AUTH_PAYLOAD_TTL_SECONDS',
  fallback: number,
): number {
  const raw = c.env[key]
  const value = raw ? Number.parseInt(raw, 10) : fallback
  return Number.isFinite(value) && value > 0 ? value : fallback
}

export function asErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'An unexpected error occurred.'
}

export function encodeBase64Url(bytes: Uint8Array): string {
  return encodeBase64(bytes)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '')
}

export function decodeBase64Url(base64Url: string): Uint8Array {
  const normalized = base64Url
    .replace(/-/g, '+')
    .replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')

  return decodeBase64(padded)
}

export function toArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
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

function decodeBase64(base64: string): Uint8Array {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)

  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index)
  }

  return bytes
}

function parseAllowedHttpsCallbackPrefix(rawPrefix: string): URL[] {
  try {
    const prefix = new URL(rawPrefix)

    if (prefix.protocol !== 'https:' || prefix.search || prefix.hash) {
      return []
    }

    return [prefix]
  } catch {
    return []
  }
}

function matchesAllowedHttpsCallbackPrefix(
  callbackUrl: URL,
  allowedPrefix: URL,
): boolean {
  if (callbackUrl.origin !== allowedPrefix.origin) {
    return false
  }

  return hasPathPrefix(callbackUrl.pathname, allowedPrefix.pathname)
}

function hasPathPrefix(
  candidatePath: string,
  prefixPath: string,
): boolean {
  if (prefixPath === '/') {
    return true
  }

  if (candidatePath === prefixPath) {
    return true
  }

  const normalizedPrefix = prefixPath.endsWith('/') ? prefixPath : `${prefixPath}/`
  return candidatePath.startsWith(normalizedPrefix)
}

function encodeBase64(bytes: Uint8Array): string {
  let binary = ''

  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }

  return btoa(binary)
}

function formatPublicKeyPem(spki: ArrayBuffer): string {
  const base64 = encodeBase64(new Uint8Array(spki))
  const wrapped = base64.match(/.{1,64}/g)?.join('\n') ?? base64
  return `-----BEGIN PUBLIC KEY-----\n${wrapped}\n-----END PUBLIC KEY-----`
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

function providerLabel(provider: SignedAuthState['provider']): string {
  return provider === 'lastfm' ? 'Last.fm' : 'Discogs'
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
