import { createServer } from 'node:http'

const host = '127.0.0.1'
const port = 18091
const dimensions = 1024

const server = createServer(async (request, response) => {
  if (request.method === 'GET' && request.url === '/health') {
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end('{"status":"UP"}')
    return
  }

  if (request.method === 'POST' && request.url === '/v1/embeddings') {
    const body = await readJson(request)
    const inputs = Array.isArray(body.input) ? body.input : [body.input]
    const embedding = Array.from({ length: dimensions }, (_, index) => index === 0 ? 1 : 0)
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end(JSON.stringify({
      object: 'list',
      data: inputs.map((_, index) => ({ object: 'embedding', index, embedding })),
      model: body.model ?? 'text-embedding-v4',
      usage: { prompt_tokens: inputs.length, total_tokens: inputs.length },
    }))
    return
  }

  if (request.method === 'POST' && request.url === '/v1/chat/completions') {
    const body = await readJson(request)
    process.stdout.write(`BrainOS mock AI chat request: model=${body.model ?? 'unknown'}, stream=${body.stream}\n`)
    response.writeHead(200, {
      'content-type': 'text/event-stream; charset=utf-8',
      'cache-control': 'no-cache',
      connection: 'close',
      'x-accel-buffering': 'no',
    })
    response.flushHeaders()
    const chunks = [
      '根据员工手册，正式员工每个自然年享有 ',
      '**5 天带薪年假**。',
      '员工需至少提前 2 个工作日在 OA 系统提交申请，',
      '填写请假日期、天数和工作交接人，由直属主管审批后生效。[来源1]',
    ]
    for (const [index, content] of chunks.entries()) {
      response.write(sseChunk(index === 0 ? { role: 'assistant', content } : { content }, null))
    }
    response.write(sseChunk({}, 'stop'))
    response.end('data: [DONE]\n\n')
    process.stdout.write('BrainOS mock AI chat response completed\n')
    return
  }

  response.writeHead(404, { 'content-type': 'application/json' })
  response.end('{"error":"not_found"}')
})

server.listen(port, host, () => {
  process.stdout.write(`BrainOS mock AI listening on http://${host}:${port}\n`)
})

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => server.close(() => process.exit(0)))
}

function sseChunk(delta, finishReason) {
  return `data: ${JSON.stringify({
    id: 'chatcmpl-brainos-e2e',
    object: 'chat.completion.chunk',
    created: 1_784_044_800,
    model: 'qwen-plus',
    choices: [{ index: 0, delta, logprobs: null, finish_reason: finishReason }],
  })}\n\n`
}

async function readJson(request) {
  let raw = ''
  for await (const chunk of request) raw += chunk
  return raw ? JSON.parse(raw) : {}
}
