const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

// Execute the real TypeScript player and its embedded processor. The fake
// browser only transports MessagePort messages and supplies render blocks.
function harness(rate = 16000) {
  const nodes = []
  const messages = []
  let Processor
  let source
  class Context {
    sampleRate = rate
    destination = {}
    audioWorklet = { addModule: async () => {
      vm.runInNewContext(source, {
        sampleRate: rate, Float32Array,
        AudioWorkletProcessor: class {
          constructor() { this.port = { postMessage: data => messages.push(() => this.main.onmessage({ data })) } }
        },
        registerProcessor: (_, value) => { Processor = value }
      })
    } }
    async resume() {}
    async close() {}
  }
  class Node {
    constructor() {
      this.processor = new Processor()
      this.port = { postMessage: data => messages.push(() => this.processor.port.onmessage({ data })) }
      this.processor.main = this.port
      nodes.push(this)
    }
    connect() {}
    disconnect() {}
  }
  const exports = {}
  const input = fs.readFileSync(path.join(__dirname, '../src/libs/pcm-turn-player.ts'), 'utf8')
  const code = ts.transpileModule(input, { compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS } }).outputText
  vm.runInNewContext(code, {
    exports, AudioContext: Context, AudioWorkletNode: Node,
    Blob: class { constructor(parts) { source = parts.join('') } },
    URL: { createObjectURL: () => 'test', revokeObjectURL() {} },
    atob: text => Buffer.from(text, 'base64').toString('binary'),
    Float32Array, Uint8Array, Int16Array, DataView
  })
  const metrics = []
  const ended = []
  const errors = []
  const player = new exports.PcmTurnPlayer({ onMetric: x => metrics.push(x), onTurnPlaybackEnded: x => ended.push(x), onError: (...x) => errors.push(x) })
  const flush = () => { while (messages.length) messages.shift()() }
  const render = (blocks, node = nodes[nodes.length - 1]) => {
    const result = []
    flush()
    for (let i = 0; i < blocks; i++) {
      const output = new Float32Array(128)
      node.processor.process([], [[output]])
      result.push(...output)
      flush()
    }
    return result
  }
  return { player, render, flush, nodes, metrics, ended, errors }
}

function pcm(count, offset = 0) {
  const bytes = Buffer.alloc(count * 2)
  const expected = []
  for (let i = 0; i < count; i++) {
    const value = ((i + offset) % 30000) + 1
    bytes.writeInt16LE(value, i * 2)
    expected.push(value / 32768)
  }
  return { base64: bytes.toString('base64'), expected }
}

test('24 seconds arriving before playback retain every sample, including the opening', async () => {
  const h = harness()
  await h.player.start('long')
  const audio = pcm(16000 * 24)
  h.player.play('long', audio.base64, 16000, 1, 0, 0)
  h.player.completeSegment('long', 0, 1)
  h.player.markTurnCompleted('long')
  const output = h.render(3001)
  for (let i = 0; i < audio.expected.length; i++) assert.equal(output[i], audio.expected[i], `sample ${i}`)
  assert.equal(h.ended.length, 1)
  assert.equal(h.errors.length, 0)
})

test('future segments, out-of-order chunks and duplicate packets play once in order', async () => {
  const h = harness()
  await h.player.start('ordered')
  const parts = Array.from({ length: 4 }, (_, i) => pcm(8000, i * 8000))
  h.player.play('ordered', parts[3].base64, 16000, 1, 2, 0)
  h.player.completeSegment('ordered', 2, 1)
  h.player.play('ordered', parts[2].base64, 16000, 1, 1, 0)
  h.player.play('ordered', parts[2].base64, 16000, 1, 1, 0)
  h.player.completeSegment('ordered', 1, 1)
  h.player.play('ordered', parts[1].base64, 16000, 1, 0, 1)
  h.player.completeSegment('ordered', 0, 2)
  h.player.play('ordered', parts[0].base64, 16000, 1, 0, 0)
  h.player.markTurnCompleted('ordered')
  const output = h.render(251)
  const expected = parts.flatMap(part => part.expected)
  for (let i = 0; i < expected.length; i++) assert.equal(output[i], expected[i], `sample ${i}`)
  assert.equal(h.ended.length, 1)
})

test('a short reply below startup reserve still plays and completes', async () => {
  const h = harness()
  await h.player.start('short')
  const part = pcm(1600)
  h.player.play('short', part.base64, 16000, 1, 0, 0)
  assert.ok(h.render(4).every(x => x === 0))
  h.player.completeSegment('short', 0, 1)
  h.player.markTurnCompleted('short')
  const output = h.render(14)
  for (let i = 0; i < part.expected.length; i++) assert.equal(output[i], part.expected[i])
  assert.equal(h.ended.length, 1)
})

test('network pause retains the next opening sample and reports one underrun per pause', async () => {
  const h = harness()
  await h.player.start('pause')
  const first = pcm(16000)
  const next = pcm(16000, 16000)
  h.player.play('pause', first.base64, 16000, 1, 0, 0)
  const before = h.render(130)
  for (let i = 0; i < first.expected.length; i++) assert.equal(before[i], first.expected[i])
  h.player.play('pause', next.base64, 16000, 1, 0, 1)
  h.player.completeSegment('pause', 0, 2)
  h.player.markTurnCompleted('pause')
  const after = h.render(126)
  for (let i = 0; i < next.expected.length; i++) assert.equal(after[i], next.expected[i])
  assert.equal(h.metrics.filter(x => x.event === 'playback.buffer_underrun').length, 1)
  assert.equal(h.ended.length, 1)
})

test('stopping with a full backlog clears old audio and rejects late packets', async () => {
  const h = harness()
  await h.player.start('old')
  h.player.play('old', pcm(400000).base64, 16000, 1, 0, 0)
  h.flush()
  const oldNode = h.nodes[0]
  h.player.stop()
  await h.player.start('new')
  h.player.play('old', pcm(16000).base64, 16000, 1, 1, 0)
  const next = pcm(1600, 999)
  h.player.play('new', next.base64, 16000, 1, 0, 0)
  h.player.completeSegment('new', 0, 1)
  h.player.markTurnCompleted('new')
  assert.ok(h.render(4, oldNode).every(x => x === 0))
  const output = h.render(14)
  for (let i = 0; i < next.expected.length; i++) assert.equal(output[i], next.expected[i])
  assert.deepEqual(h.ended, ['new'])
})

test('backlog beyond the memory limit fails explicitly instead of dropping old speech', async () => {
  const h = harness()
  await h.player.start('limit')
  h.player.play('limit', pcm(16000 * 181).base64, 16000, 1, 0, 0)
  assert.equal(h.errors.length, 1)
  assert.equal(h.errors[0][0], 'limit')
  assert.ok(h.render(4).every(x => x === 0))
})

test('a missing chunk at end of turn surfaces an error rather than a silent skipped sentence', async () => {
  const h = harness()
  await h.player.start('missing')
  h.player.play('missing', pcm(16000).base64, 16000, 1, 0, 1)
  h.player.completeSegment('missing', 0, 2)
  h.player.markTurnCompleted('missing')
  assert.equal(h.errors.length, 1)
  assert.ok(h.render(4).every(x => x === 0))
})

test('48 kHz output preserves duration and each original sample on 3x upsampling', async () => {
  const h = harness(48000)
  await h.player.start('rate')
  const part = pcm(16000)
  h.player.play('rate', part.base64, 16000, 1, 0, 0)
  h.player.completeSegment('rate', 0, 1)
  h.player.markTurnCompleted('rate')
  const output = h.render(376)
  for (let i = 0; i < part.expected.length; i++) assert.equal(output[i * 3], part.expected[i])
  assert.equal(output[48000], 0)
  assert.equal(h.ended.length, 1)
})
