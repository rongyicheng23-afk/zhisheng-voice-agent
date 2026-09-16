const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const tick = () => new Promise(resolve => setImmediate(resolve))

function load(file, globals) {
  const exports = {}
  const code = ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/libs', file), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText
  vm.runInNewContext(code, { exports, URL, AbortController, setTimeout, clearTimeout,
    console: { log() {}, error() {} }, ...globals })
  return exports
}

function setup(fetchImpl) {
  const sockets = [], requests = [], states = [], messages = []
  class Player {
    async start() {}
    stop() {}
    play() {}
    completeSegment() {}
    markTurnCompleted() {}
  }
  class Socket {
    static OPEN = 1
    readyState = 0
    bufferedAmount = 0
    constructor(url) { this.url = url; sockets.push(this) }
    send(data) { this.sent = data }
    close() { this.readyState = 3 }
    open() { this.readyState = 1; this.onopen({}); this.onmessage({ data: '{"event":"session.ready"}' }) }
  }
  const module = load('websocket-client.ts', {
    require: id => id.includes('pcm-turn-player')
      ? { PcmTurnPlayer: Player }
      : {
          getRealtimeGatewayWsBaseUrl: () => 'ws://127.0.0.1:18081',
          getRuntimeHttpBaseUrl: () => 'http://127.0.0.1:18080'
        },
    WebSocket: Socket, window: { WebSocket: Socket, setTimeout, clearTimeout },
    crypto: { randomUUID: () => '11111111-1111-4111-8111-111111111111' },
    localStorage: { getItem: () => 'LONG_LOGIN_JWT' }, sessionStorage: { getItem: () => null },
    fetch: async (url, options) => {
      requests.push({ url, options })
      return fetchImpl ? fetchImpl() : { status: 200, ok: true, json: async () => ({ ticket: 'a'.repeat(43) }) }
    }
  })
  const client = module.WebSocketConnectMethod({ stateHandle: state => states.push(state), msgHandle: msg => messages.push(msg) })
  return { client, sockets, requests, states, messages }
}

test('JWT only goes in HTTP header; actual open resolves connection', async () => {
  const f = setup()
  const connection = f.client.wsStart()
  await tick()
  assert.equal(f.requests[0].options.headers.Authorization, 'Bearer LONG_LOGIN_JWT')
  assert.equal(new URL(f.sockets[0].url).searchParams.get('token'), null)
  assert.equal(new URL(f.sockets[0].url).searchParams.get('save_audio'), null)
  assert.equal(new URL(f.sockets[0].url).searchParams.get('ticket'), 'a'.repeat(43))
  f.sockets[0].open()
  assert.equal(await connection, undefined)
  assert.ok(!JSON.stringify(f.client.getConnectionStatus()).includes('ticket'))
  f.client.wsStop()
})
test('stop while ticket request is pending prevents socket creation', async () => {
  let release
  const f = setup(() => new Promise(resolve => { release = resolve }))
  const connection = f.client.wsStart()
  f.client.wsStop()
  release({ status: 200, ok: true, json: async () => ({ ticket: 'a'.repeat(43) }) })
  assert.equal(await connection, undefined)
  assert.equal(f.sockets.length, 0)
})
test('realtime gateway URL carries only the short-lived ticket', async () => {
  const f = setup()
  const connection = f.client.wsStart()
  await tick()
  assert.equal(new URL(f.sockets[0].url).searchParams.get('save_audio'), null)
  assert.equal(new URL(f.sockets[0].url).searchParams.get('token'), null)
  f.sockets[0].open()
  await connection
  f.client.wsStop()
})
test('old messages and close callback cannot affect a new connection', async () => {
  const f = setup()
  let connection = f.client.wsStart()
  await tick()
  const old = f.sockets[0]
  old.open()
  await connection
  f.client.wsStop()
  connection = f.client.wsStart()
  await tick()
  f.sockets[1].open()
  await connection
  old.onmessage({ data: 'late' })
  old.onclose({})
  assert.equal(f.messages.length, 0)
  assert.deepEqual(f.states, [0, 0])
  f.client.wsStop()
})
test('failed ticket never creates a WebSocket', async () => {
  const f = setup(() => ({ status: 503, ok: false }))
  await assert.rejects(f.client.wsStart(), /HTTP 503/)
  assert.equal(f.sockets.length, 0)
})
test('WebSocket open establishes gateway readiness', async () => {
  const f = setup()
  let resolved = false
  const connection = f.client.wsStart().then(result => { resolved = true; return result })
  await tick()
  const socket = f.sockets[0]
  socket.readyState = 1
  socket.onopen({})
  await tick()
  assert.equal(resolved, true)
  assert.equal(f.client.isConnected(), true)
  assert.equal(await connection, undefined)
  f.client.wsStop()
})
test('late microphone permission grant after stop releases tracks', async () => {
  let release, stopped = 0
  const { AudioRecorder } = load('audio-recorder.ts', {
    navigator: { mediaDevices: { getUserMedia: () => new Promise(resolve => { release = resolve }) } },
    AudioContext: class { constructor() { throw new Error('Must not create audio graph after stop') } }
  })
  const recorder = new AudioRecorder()
  const starting = recorder.startRecording(() => assert.fail('No audio after stop'))
  recorder.stopRecording()
  release({ getTracks: () => [{ stop: () => stopped++ }] })
  await starting
  assert.equal(stopped, 1)
  assert.equal(recorder.isRecordingActive(), false)
})
