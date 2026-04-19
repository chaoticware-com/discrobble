import { md5 } from 'js-md5'

export function signLastfmParams(
  params: Record<string, string>,
  apiSecret: string,
): string {
  const signatureBase = Object.entries(params)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}${value}`)
    .join('')

  return md5(`${signatureBase}${apiSecret}`)
}
