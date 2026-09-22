// Unit harness only: no browser or microphone; not a latency measurement.
const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')

test('segment marker queue is bounded even for tiny audio segments', () => {
  const f = fixture()
  for (let i = 1; i <= 257; i++) f.send(f.chunk({ sequence: i, segmentSequence: i }))
  assert.equal(f.messages.at(-1).code, 'SEGMENT_MARKER_OVERFLOW')
  assert.equal(f.player.size, 0)
})

test('source markers follow rendered frames, not queued audio, and are flushed on interrupt', () => {
  const f = fixture()
  f.send(f.chunk({ pcm: new Int16Array(256).buffer }))
  f.send(f.chunk({ sequence: 2, segmentSequence: 2, pcm: new Int16Array(128).buffer }))
  const markers = () => f.messages.filter(x => x.event === 'playback.segment').map(x => x.segmentSequence)
  assert.deepEqual(markers(), [])
  f.render(); assert.deepEqual(markers(), [1])
  f.render(); assert.deepEqual(markers(), [1])
  f.render(); assert.deepEqual(markers(), [1, 2])
  f.send(f.chunk({ sequence: 3, segmentSequence: 3 }))
  f.send({ event: 'turn.interrupt', turnId: 'current' })
  f.render(); assert.deepEqual(markers(), [1, 2])
})

test('underflow and continuation chunks do not duplicate segment markers', () => {
  const f = fixture()
  f.send(f.chunk()); f.render(); f.render()
  f.send(f.chunk({ sequence: 2, chunkSequence: 2 })); f.render()
  f.send(f.chunk({ sequence: 3, segmentSequence: 2 })); f.render()
  assert.deepEqual(f.messages.filter(x => x.event === 'playback.segment').map(x => x.segmentSequence), [1, 2])
})

function fixture() {
  let Processor
  const messages = []
  const scope = {
    sampleRate: 24000, ArrayBuffer,
    AudioWorkletProcessor: class { constructor() { this.port = { postMessage: e => messages.push(e) } } },
    registerProcessor: (_name, cls) => { Processor = cls }
  }
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../public/audio/turn-pcm-player.js'), 'utf8'), scope)
  const player = new Processor()
  const send = event => player.receive(event)
  send({ event: 'turn.start', turnId: 'current' })
  const chunk = overrides => ({ event: 'audio.chunk', turnId: 'current', sequence: 1,
    segmentSequence: 1, chunkSequence: 1, codec: 'pcm16', channels: 1, sampleRate: 24000,
    pcm: new Int16Array([16384, -16384]).buffer, ...overrides })
  const render = () => { const output = new Float32Array(128); player.process([], [[output]]); return output }
  return { player, send, chunk, render, messages }
}

test('converts PCM16, reports start and drains before completion', () => {
  const f = fixture()
  f.send(f.chunk())
  f.send({ event: 'audio.completed', turnId: 'current' })
  assert.equal(f.messages.some(e => e.event === 'playback.completed'), false)
  assert.deepEqual(Array.from(f.render()).slice(0, 2), [0.5, -0.5])
  assert.equal(f.messages.at(-1).event, 'playback.completed')
})
test('interrupt flushes queued speech and rejects late old audio', () => {
  const f = fixture()
  f.send(f.chunk())
  f.send({ event: 'turn.interrupt', turnId: 'current' })
  f.send(f.chunk({ sequence: 2, chunkSequence: 2 }))
  assert.ok(f.render().every(x => x === 0))
  assert.equal(f.player.size, 0)
})
test('reports freed buffer space even without receiving new audio', () => {
  const f = fixture()
  f.send(f.chunk({ pcm: new Int16Array(4800).buffer }))
  for (let i = 0; i < 20; i++) f.render()
  const reports = f.messages.filter(e => e.event === 'playback.buffer')
  assert.ok(reports.length >= 2)
  assert.ok(reports.at(-1).bufferedFrames < reports[0].bufferedFrames)
})
test('duplicate and foreign turn chunks do not enter the buffer', () => {
  const f = fixture()
  f.send(f.chunk())
  f.send(f.chunk())
  f.send(f.chunk({ turnId: 'old', sequence: 2 }))
  assert.equal(f.player.size, 2)
})
test('gaps fail closed', () => {
  const f = fixture()
  f.send(f.chunk({ segmentSequence: 2 }))
  assert.equal(f.messages.at(-1).code, 'AUDIO_SEQUENCE_GAP')
  assert.ok(f.render().every(x => x === 0))
})
test('wrong codec or sample rate fails instead of playing distorted audio', () => {
  for (const override of [{ codec: 'opus' }, { sampleRate: 16000 }]) {
    const f = fixture()
    f.send(f.chunk(override))
    assert.equal(f.messages.at(-1).code, 'UNSUPPORTED_AUDIO_FORMAT')
  }
})
test('buffer overflow is explicit and bounded', () => {
  const f = fixture()
  f.send(f.chunk({ pcm: new ArrayBuffer(24000 * 4 * 2 + 2) }))
  assert.equal(f.messages.at(-1).code, 'AUDIO_BUFFER_OVERFLOW')
  assert.equal(f.player.size, 0)
})
