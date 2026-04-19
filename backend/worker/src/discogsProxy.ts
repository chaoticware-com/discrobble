import { type WorkerContext, requireBinding } from './authFlow'
import { buildDiscogsAuthorizationHeader, DISCOGS_USER_AGENT } from './discogsOAuth'

const DISCOGS_COLLECTION_ROOT = 'https://api.discogs.com/users'
const DISCOGS_IDENTITY_URL = 'https://api.discogs.com/oauth/identity'
const DISCOGS_RELEASES_ROOT = 'https://api.discogs.com/releases'
const DISCOGS_SEARCH_URL = 'https://api.discogs.com/database/search'

interface DiscogsBasicArtist {
  name?: string
}

interface DiscogsBasicFormat {
  descriptions?: string[]
  name?: string
}

interface DiscogsCollectionItem {
  basic_information?: {
    artists?: DiscogsBasicArtist[]
    cover_image?: string
    formats?: DiscogsBasicFormat[]
    id?: number
    title?: string
    year?: number | string
  }
  id?: number
  instance_id?: number
}

interface DiscogsCollectionResponse {
  pagination?: {
    page?: number
    pages?: number
    per_page?: number
  }
  releases?: DiscogsCollectionItem[]
}

interface DiscogsCredentials {
  token: string
  tokenSecret: string
}

interface DiscogsIdentityResponse {
  id?: number
  resource_url?: string
  username?: string
}

interface DiscogsReleaseDetailResponse {
  identifiers?: Array<{
    type?: string
    value?: string
  }>
  tracklist?: Array<{
    artists?: DiscogsBasicArtist[]
    duration?: string
    position?: string
    title?: string
  }>
}

interface DiscogsSearchResponse {
  results?: DiscogsSearchResult[]
}

interface DiscogsSearchResult {
  barcode?: string[]
  id?: number
  title?: string
  type?: string
  user_data?: {
    in_collection?: boolean
  }
  year?: number | string
}

type DiscogsProxyErrorCode =
  | 'discogs_auth_invalid'
  | 'discogs_rate_limited'
  | 'discogs_unavailable'
  | 'missing_discogs_credentials'
  | 'missing_search_input'

export async function handleDiscogsMe(c: WorkerContext) {
  const credentials = readDiscogsCredentials(c)

  if ('response' in credentials) {
    return credentials.response
  }

  const identity = await fetchDiscogsIdentity(c, credentials.value)

  if ('response' in identity) {
    return identity.response
  }

  return c.json(
    {
      id: identity.value.id,
      resource_url: identity.value.resource_url,
      username: identity.value.username,
    },
    200,
    {
      'Cache-Control': 'no-store',
    },
  )
}

export async function handleDiscogsCollection(c: WorkerContext) {
  const credentials = readDiscogsCredentials(c)

  if ('response' in credentials) {
    return credentials.response
  }

  const page = parsePositiveInteger(c.req.query('page'), 1)
  const perPage = clampPositiveInteger(c.req.query('per_page'), 50, 100)
  const folderId = parseNonNegativeInteger(c.req.query('folder_id'), 0)
  const identity = await fetchDiscogsIdentity(c, credentials.value)

  if ('response' in identity) {
    return identity.response
  }

  const collectionUrl = new URL(
    `${DISCOGS_COLLECTION_ROOT}/${encodeURIComponent(identity.value.username)}/collection/folders/${folderId}/releases`,
  )
  collectionUrl.searchParams.set('page', `${page}`)
  collectionUrl.searchParams.set('per_page', `${perPage}`)

  const response = await signedDiscogsFetch(c, collectionUrl.toString(), credentials.value)

  if (!response.ok) {
    return await mapDiscogsFailure(c, response, 'Discogs could not fetch the collection page.')
  }

  const payload = await response.json() as DiscogsCollectionResponse
  const fetchedAt = new Date().toISOString()
  const items = await Promise.all(
    (payload.releases ?? []).map(async (item) => {
      const releaseId = item.basic_information?.id ?? item.id
      const releaseDetail = releaseId
        ? await fetchReleaseDetail(c, credentials.value, releaseId)
        : null

      return normalizeCollectionItem(item, releaseDetail, fetchedAt)
    }),
  )

  return c.json(
    {
      items,
      page: payload.pagination?.page ?? page,
      pages: payload.pagination?.pages ?? 1,
      per_page: payload.pagination?.per_page ?? perPage,
    },
    200,
    {
      'Cache-Control': 'no-store',
    },
  )
}

export async function handleDiscogsSearch(c: WorkerContext) {
  const credentials = readDiscogsCredentials(c)

  if ('response' in credentials) {
    return credentials.response
  }

  const query = c.req.query('query')?.trim()
  const barcode = c.req.query('barcode')?.trim()

  if (!query && !barcode) {
    return jsonError(
      c,
      400,
      'missing_search_input',
      'Discogs search requires either a query string or a barcode value.',
    )
  }

  const perPage = clampPositiveInteger(c.req.query('per_page'), 10, 100)
  const searchUrl = new URL(DISCOGS_SEARCH_URL)
  searchUrl.searchParams.set('type', 'release')
  searchUrl.searchParams.set('per_page', `${perPage}`)

  if (query) {
    searchUrl.searchParams.set('q', query)
  }

  if (barcode) {
    searchUrl.searchParams.set('barcode', barcode)
  }

  const response = await signedDiscogsFetch(c, searchUrl.toString(), credentials.value)

  if (!response.ok) {
    return await mapDiscogsFailure(c, response, 'Discogs could not complete the search request.')
  }

  const payload = await response.json() as DiscogsSearchResponse
  const results = (payload.results ?? [])
    .filter((result) => result.type === 'release' && typeof result.id === 'number')
    .map((result) => normalizeSearchResult(result, barcode))

  return c.json(
    {
      results,
    },
    200,
    {
      'Cache-Control': 'no-store',
    },
  )
}

async function fetchDiscogsIdentity(
  c: WorkerContext,
  credentials: DiscogsCredentials,
): Promise<
  | { response: Response }
  | { value: Required<Pick<DiscogsIdentityResponse, 'id' | 'resource_url' | 'username'>> }
> {
  const response = await signedDiscogsFetch(c, DISCOGS_IDENTITY_URL, credentials)

  if (!response.ok) {
    return {
      response: await mapDiscogsFailure(c, response, 'Discogs could not resolve the authenticated identity.'),
    }
  }

  const payload = await response.json() as DiscogsIdentityResponse

  if (!payload.username || typeof payload.id !== 'number' || !payload.resource_url) {
    return {
      response: jsonError(
        c,
        502,
        'discogs_unavailable',
        'Discogs returned an unexpected identity payload.',
      ),
    }
  }

  return {
    value: {
      id: payload.id,
      resource_url: payload.resource_url,
      username: payload.username,
    },
  }
}

async function fetchReleaseDetail(
  c: WorkerContext,
  credentials: DiscogsCredentials,
  releaseId: number,
): Promise<DiscogsReleaseDetailResponse | null> {
  const response = await signedDiscogsFetch(
    c,
    `${DISCOGS_RELEASES_ROOT}/${releaseId}`,
    credentials,
  )

  if (!response.ok) {
    return null
  }

  return response.json() as Promise<DiscogsReleaseDetailResponse>
}

function joinArtistNames(artists: DiscogsBasicArtist[] | undefined): string {
  return artists
    ?.map((artist) => artist.name?.trim())
    .filter((artist): artist is string => Boolean(artist))
    .join(', ')
    ?? ''
}

function clampPositiveInteger(rawValue: string | undefined, fallback: number, max: number): number {
  const parsed = parsePositiveInteger(rawValue, fallback)
  return Math.min(parsed, max)
}

function parsePositiveInteger(rawValue: string | undefined, fallback: number): number {
  const parsed = rawValue ? Number.parseInt(rawValue, 10) : fallback
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function parseNonNegativeInteger(rawValue: string | undefined, fallback: number): number {
  const parsed = rawValue ? Number.parseInt(rawValue, 10) : fallback
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
}

function collectBarcodeValues(detail: DiscogsReleaseDetailResponse | null): string[] {
  return detail?.identifiers
    ?.filter((identifier) => identifier.type?.toLowerCase().includes('barcode'))
    .map((identifier) => identifier.value?.trim())
    .filter((value): value is string => Boolean(value))
    ?? []
}

function normalizeCollectionItem(
  item: DiscogsCollectionItem,
  detail: DiscogsReleaseDetailResponse | null,
  fetchedAt: string,
) {
  const basicInformation = item.basic_information

  return {
    artist: joinArtistNames(basicInformation?.artists),
    barcode_values: collectBarcodeValues(detail),
    cover_image: basicInformation?.cover_image ?? null,
    fetched_at: fetchedAt,
    formats: normalizeFormats(basicInformation?.formats),
    instance_id: item.instance_id ?? null,
    release_id: basicInformation?.id ?? item.id ?? 0,
    title: basicInformation?.title ?? '',
    tracklist: normalizeTracklist(detail?.tracklist),
    year: normalizeYear(basicInformation?.year),
  }
}

function normalizeFormats(formats: DiscogsBasicFormat[] | undefined): string[] {
  const values = new Set<string>()

  for (const format of formats ?? []) {
    if (format.name?.trim()) {
      values.add(format.name.trim())
    }

    for (const description of format.descriptions ?? []) {
      if (description.trim()) {
        values.add(description.trim())
      }
    }
  }

  return [...values]
}

function normalizeSearchResult(
  result: DiscogsSearchResult,
  barcodeQuery: string | undefined,
) {
  const [artist, title] = splitSearchTitle(result.title ?? '')

  return {
    artist,
    barcode_values: result.barcode ?? [],
    in_user_collection: Boolean(result.user_data?.in_collection),
    match_reason: barcodeQuery ? 'barcode' : 'query',
    release_id: result.id ?? 0,
    title,
    year: normalizeYear(result.year),
  }
}

function normalizeTracklist(
  tracklist: DiscogsReleaseDetailResponse['tracklist'],
) {
  return (tracklist ?? [])
    .filter((track) => Boolean(track.title?.trim()))
    .map((track) => ({
      duration: track.duration?.trim() || null,
      position: track.position?.trim() || '',
      title: track.title?.trim() || '',
    }))
}

function normalizeYear(year: number | string | undefined): number | null {
  if (typeof year === 'number' && Number.isFinite(year) && year > 0) {
    return year
  }

  if (typeof year === 'string') {
    const parsed = Number.parseInt(year, 10)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null
  }

  return null
}

async function signedDiscogsFetch(
  c: WorkerContext,
  url: string,
  credentials: DiscogsCredentials,
): Promise<Response> {
  const authorization = await buildDiscogsAuthorizationHeader({
    consumerKey: requireBinding(c, 'DISCOGS_CONSUMER_KEY'),
    consumerSecret: requireBinding(c, 'DISCOGS_CONSUMER_SECRET'),
    method: 'GET',
    token: credentials.token,
    tokenSecret: credentials.tokenSecret,
    url,
  })

  return fetch(url, {
    headers: {
      Authorization: authorization,
      'User-Agent': DISCOGS_USER_AGENT,
    },
    method: 'GET',
  })
}

function splitSearchTitle(rawTitle: string): [string, string] {
  const separator = rawTitle.indexOf(' - ')

  if (separator < 0) {
    return ['', rawTitle]
  }

  return [
    rawTitle.slice(0, separator).trim(),
    rawTitle.slice(separator + 3).trim(),
  ]
}

function readDiscogsCredentials(
  c: WorkerContext,
):
  | { response: Response }
  | { value: DiscogsCredentials } {
  const token = c.req.header('X-Discogs-OAuth-Token')?.trim()
  const tokenSecret = c.req.header('X-Discogs-OAuth-Secret')?.trim()

  if (!token || !tokenSecret) {
    return {
      response: jsonError(
        c,
        401,
        'missing_discogs_credentials',
        'Discogs requests require both OAuth token headers.',
      ),
    }
  }

  return {
    value: {
      token,
      tokenSecret,
    },
  }
}

async function mapDiscogsFailure(
  c: WorkerContext,
  response: Response,
  fallbackMessage: string,
) {
  const status = response.status
  const bodyText = await response.text()

  if (status === 401 || status === 403) {
    return jsonError(
      c,
      401,
      'discogs_auth_invalid',
      extractDiscogsMessage(bodyText) || 'The Discogs OAuth token pair is invalid. Re-authenticate and retry.',
    )
  }

  if (status === 429) {
    return jsonError(
      c,
      429,
      'discogs_rate_limited',
      extractDiscogsMessage(bodyText) || 'Discogs rate-limited the request. Retry later.',
    )
  }

  return jsonError(
    c,
    502,
    'discogs_unavailable',
    extractDiscogsMessage(bodyText) || fallbackMessage,
  )
}

function extractDiscogsMessage(bodyText: string): string | null {
  try {
    const payload = JSON.parse(bodyText) as { message?: string }
    return payload.message?.trim() || null
  } catch {
    return bodyText.trim() || null
  }
}

function jsonError(
  c: WorkerContext,
  status: 400 | 401 | 429 | 502,
  code: DiscogsProxyErrorCode,
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
