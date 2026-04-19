import { encodeBase64Url } from './authFlow'

export const DISCOGS_USER_AGENT = 'Discrobble/0.1 +https://github.com/chaoticware-com/discrobble'

export async function buildDiscogsAuthorizationHeader(input: {
  consumerKey: string
  consumerSecret: string
  extraOauthParams?: Record<string, string>
  method?: 'GET' | 'POST'
  token?: string
  tokenSecret?: string
  url: string
}): Promise<string> {
  const oauthParams: Record<string, string> = {
    oauth_consumer_key: input.consumerKey,
    oauth_nonce: randomNonce(),
    oauth_signature_method: 'HMAC-SHA1',
    oauth_timestamp: `${Math.floor(Date.now() / 1000)}`,
    oauth_version: '1.0',
    ...(input.token ? { oauth_token: input.token } : {}),
    ...(input.extraOauthParams ?? {}),
  }
  const signatureParameters = [
    ...Object.entries(oauthParams),
    ...collectQueryParameterEntries(input.url),
  ]
  const oauthSignature = await signOauthRequest({
    consumerSecret: input.consumerSecret,
    method: input.method ?? 'POST',
    parameters: signatureParameters,
    tokenSecret: input.tokenSecret,
    url: input.url,
  })
  const headerParams = {
    ...oauthParams,
    oauth_signature: oauthSignature,
  }

  return `OAuth ${Object.entries(headerParams)
    .sort(([leftKey, leftValue], [rightKey, rightValue]) => {
      const keyComparison = leftKey.localeCompare(rightKey)
      return keyComparison !== 0 ? keyComparison : leftValue.localeCompare(rightValue)
    })
    .map(([key, value]) => `${percentEncode(key)}="${percentEncode(value)}"`)
    .join(', ')}`
}

function normalizeUrl(inputUrl: string): string {
  const url = new URL(inputUrl)
  url.hash = ''
  url.search = ''
  return url.toString()
}

function percentEncode(value: string): string {
  return encodeURIComponent(value).replace(/[!'()*]/g, (character) =>
    `%${character.charCodeAt(0).toString(16).toUpperCase()}`)
}

function randomNonce(): string {
  return encodeBase64Url(crypto.getRandomValues(new Uint8Array(16)))
}

async function signOauthRequest(input: {
  consumerSecret: string
  method: 'GET' | 'POST'
  parameters: Array<[string, string]>
  tokenSecret?: string
  url: string
}): Promise<string> {
  const normalizedParameters = input.parameters
    .map(([key, value]) => [percentEncode(key), percentEncode(value)] as const)
    .sort(([leftKey, leftValue], [rightKey, rightValue]) => {
      const keyComparison = leftKey.localeCompare(rightKey)
      return keyComparison !== 0 ? keyComparison : leftValue.localeCompare(rightValue)
    })
    .map(([key, value]) => `${key}=${value}`)
    .join('&')
  const baseString = [
    input.method,
    percentEncode(normalizeUrl(input.url)),
    percentEncode(normalizedParameters),
  ].join('&')
  const signingKey = `${percentEncode(input.consumerSecret)}&${percentEncode(input.tokenSecret ?? '')}`
  const cryptoKey = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(signingKey),
    {
      name: 'HMAC',
      hash: 'SHA-1',
    },
    false,
    ['sign'],
  )
  const signature = await crypto.subtle.sign(
    'HMAC',
    cryptoKey,
    new TextEncoder().encode(baseString),
  )

  return btoa(String.fromCharCode(...new Uint8Array(signature)))
}

function collectQueryParameterEntries(inputUrl: string): Array<[string, string]> {
  const url = new URL(inputUrl)
  const entries: Array<[string, string]> = []
  url.searchParams.forEach((value, key) => {
    entries.push([key, value])
  })
  return entries
}
