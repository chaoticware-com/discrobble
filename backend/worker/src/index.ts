import { Hono } from 'hono'

const app = new Hono()

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

export default app
