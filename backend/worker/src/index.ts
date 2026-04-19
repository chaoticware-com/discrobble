import { Hono } from 'hono'
import { handleLastfmAuthCallback, handleLastfmAuthStart } from './lastfmAuth'
import { handleLastfmNowPlaying, handleLastfmScrobble } from './lastfmWrites'

export interface WorkerBindings {
  AUTH_PAYLOAD_TTL_SECONDS?: string
  AUTH_STATE_SECRET: string
  AUTH_STATE_TTL_SECONDS?: string
  LASTFM_API_KEY: string
  LASTFM_API_SECRET: string
}

const app = new Hono<{ Bindings: WorkerBindings }>()

app.get('/', (c) =>
  c.json({
    service: 'discrobble-worker',
    status: 'ok',
  }),
)

app.get('/health', (c) =>
  c.json({
    status: 'ok',
  }),
)

app.post('/auth/lastfm/start', handleLastfmAuthStart)
app.get('/auth/lastfm/callback', handleLastfmAuthCallback)
app.post('/lastfm/now-playing', handleLastfmNowPlaying)
app.post('/lastfm/scrobble', handleLastfmScrobble)

export default app
