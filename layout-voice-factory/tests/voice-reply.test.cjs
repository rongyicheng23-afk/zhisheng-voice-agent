const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

test('citation highlight waits for playback and clears on interrupt', async () => {
  const f = fixture()
  try {
    await f.reply.start('资料', 'knowledge'); f.ready()
    const receive = event => f.receive({ sessionId: 'session', turnId: 'turn', ...event })
    receive({ event: 'turn.sources', sequence: 2, citations: [{ id: 'source' }] })
    receive({ event: 'segment.ready', sequence: 3, segmentSequence: 1, citationIds: ['source'] })
    assert.ok(!f.updates.some(x => x.activeCitationIds?.includes('source')))
    f.players[0].port.onmessage({ data: { event: 'playback.segment', turnId: 'turn', segmentSequence: 1 } })
    assert.equal(f.updates.at(-1).activeCitationIds[0], 'source')
    f.reply.stop()
    assert.ok(f.updates.filter(x => x.activeCitationIds).at(-1).activeCitationIds.length === 0)
  } finally { f.reply.stop(false) }
})

test('unknown citation fails closed before playback', async () => {
  const f = fixture()
  await f.reply.start('资料', 'knowledge'); f.ready()
  f.receive({ event: 'turn.sources', sessionId: 'session', turnId: 'turn', sequence: 2, citations: [] })
  f.receive({ event: 'segment.ready', sessionId: 'session', turnId: 'turn', sequence: 3, segmentSequence: 1, citationIds: ['invented'] })
  assert.equal(f.sockets[0].readyState, 3)
})

function fixture(fetchOverride) {
  const sockets = [], players = [], requests = [], updates = []
  class Socket {
    static OPEN = 1
    readyState = 1
    sent = []
    constructor(url) { this.url = url; sockets.push(this) }
    send(data) { this.sent.push(JSON.parse(data)) }
    close() { this.readyState = 3 }
  }
  class Context {
    audioWorklet = { addModule: async () => {} }
    resume() { return Promise.resolve() }
    close() { return Promise.resolve() }
  }
  class Player {
    posted = []
    port = { postMessage: data => this.posted.push(data) }
    constructor() { players.push(this) }
    connect() {}
    disconnect() { this.disconnected = true }
  }
  const exports = {}
  const source = fs.readFileSync(path.join(__dirname, '../src/libs/voice-reply.ts'), 'utf8')
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
  vm.runInNewContext(code, {
    exports, require: () => ({ getRuntimeHttpBaseUrl: () => 'http://localhost:18080' }),
    localStorage: { getItem: () => 'LOGIN_TOKEN' }, sessionStorage: { getItem: () => null },
    location: { hostname: 'localhost', port: '8081', protocol: 'http:', host: 'localhost:8081' },
    process: { env: {} }, performance: { now: () => 10 }, URL, AbortController, atob, Uint8Array,
    setTimeout, clearTimeout, AudioContext: Context, AudioWorkletNode: Player, WebSocket: Socket,
    fetch: async (url, options) => {
      requests.push({ url, options })
      return fetchOverride ? fetchOverride() : { ok: true, json: async () => ({ ticket: 'a'.repeat(43) }) }
    }
  })
  const reply = new exports.VoiceReply(value => updates.push(value))
  const receive = event => sockets.at(-1).onmessage({ data: JSON.stringify(event) })
  const ready = () => {
    receive({ event: 'session.ready', sessionId: 'session', sampleRate: 24000 })
    receive({ event: 'turn.started', sessionId: 'session', turnId: 'turn', sequence: 1 })
  }
  return { reply, sockets, players, requests, updates, receive, ready }
}

test('voice reply uses header auth and waits for ready before sending prompt', async () => {
  const f = fixture()
  try {
    await f.reply.start('测试')
    assert.equal(f.requests[0].options.headers.Authorization, 'Bearer LOGIN_TOKEN')
    assert.ok(!f.sockets[0].url.includes('LOGIN_TOKEN'))
    assert.equal(f.sockets[0].sent.length, 0)
    f.ready()
    assert.equal(f.sockets[0].sent[0].prompt, '测试')
  } finally { f.reply.stop(false) }
})

test('knowledge mode is explicit and citations cannot leak from a stopped reply', async () => {
  const f = fixture()
  try {
    await f.reply.start('报名材料', 'knowledge'); f.ready()
    assert.equal(f.sockets[0].sent[0].answerMode, 'knowledge')
    f.receive({ event: 'turn.sources', sessionId: 'session', turnId: 'turn', sequence: 2, citations: [{ id: 'source' }] })
    assert.equal(f.updates.at(-1).citations[0].id, 'source')
    const old = f.sockets[0]
    await f.reply.start('新问题', 'general'); f.ready()
    const count = f.updates.length
    old.onmessage({ data: JSON.stringify({ event: 'turn.sources', sessionId: 'session', turnId: 'turn', sequence: 3, citations: [{ id: 'old' }] }) })
    assert.equal(f.updates.length, count)
    assert.ok(f.updates.some(u => Array.isArray(u.citations) && u.citations.length === 0))
  } finally { f.reply.stop(false) }
})

test('knowledge question length is checked before connecting', async () => {
  const f = fixture()
  await f.reply.start('中'.repeat(1001), 'knowledge')
  assert.equal(f.sockets.length, 0)
  assert.match(f.updates.at(-1).status, /1000/)
})

test('stopping during ticket fetch cannot create a late socket', async () => {
  let release
  const f = fixture(() => new Promise(resolve => { release = resolve }))
  const pending = f.reply.start('测试')
  await new Promise(resolve => setImmediate(resolve))
  f.reply.stop()
  release({ ok: true, json: async () => ({ ticket: 'a'.repeat(43) }) })
  await pending
  assert.equal(f.sockets.length, 0)
})

test('interrupt disconnects audio and ignores old callbacks after a new reply', async () => {
  const f = fixture()
  try {
    await f.reply.start('旧问题'); f.ready()
    const old = f.sockets[0]
    await f.reply.start('新问题'); f.ready()
    const count = f.updates.length
    old.onmessage({ data: JSON.stringify({ event: 'llm.delta', sessionId: 'session', turnId: 'turn', sequence: 2, text: '旧回答' }) })
    old.onclose()
    assert.equal(f.updates.length, count)
    assert.equal(f.players[0].disconnected, true)
    assert.equal(old.sent.at(-1).event, 'turn.interrupt')
  } finally { f.reply.stop(false) }
})

test('audio acknowledgement waits for available playback buffer', async () => {
  const f = fixture()
  try {
    await f.reply.start('测试'); f.ready()
    f.receive({ event: 'audio.chunk', sessionId: 'session', turnId: 'turn', sequence: 2,
      segmentSequence: 1, chunkSequence: 1, pcm: 'AAA=', codec: 'pcm16', channels: 1, sampleRate: 24000 })
    const player = f.players[0], socket = f.sockets[0]
    assert.equal(player.posted.at(-1).pcm.byteLength, 2)
    player.port.onmessage({ data: { event: 'playback.buffer', turnId: 'turn', bufferedFrames: 50000 } })
    assert.equal(socket.sent.length, 1)
    player.port.onmessage({ data: { event: 'playback.buffer', turnId: 'turn', bufferedFrames: 40000 } })
    assert.equal(socket.sent.at(-1).event, 'audio.ack')
    assert.equal(socket.sent.at(-1).sequence, 2)
  } finally { f.reply.stop(false) }
})

test('event sequence gap fails closed and stops audio', async () => {
  const f = fixture()
  await f.reply.start('测试'); f.ready()
  f.receive({ event: 'llm.delta', sessionId: 'session', turnId: 'turn', sequence: 3, text: 'gap' })
  assert.equal(f.sockets[0].readyState, 3)
  assert.equal(f.players[0].disconnected, true)
  assert.equal(f.updates.at(-1).busy, false)
})
