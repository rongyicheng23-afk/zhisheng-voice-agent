const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const vue = require('vue')

function setup(t) {
  const calls = [], stops = []
  const source = fs.readFileSync(path.join(__dirname, '../src/components/VoiceReplyPanel.vue'), 'utf8')
    .match(/<script lang="ts">([\s\S]*?)<\/script>/)[1]
  const code = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020
  } }).outputText
  const exports = {}
  const scope = vue.effectScope()
  t.after(() => scope.stop())
  vm.runInNewContext(code, { exports, require: name => name === 'vue'
    ? { ...vue, onBeforeUnmount() {} }
    : { VoiceReply: class {
      start(text) { calls.push(text); return Promise.resolve() }
      stop() { stops.push(true) }
    } } })
  const props = vue.reactive({ prompt: '本次提问', completion: 0, recording: false, recognizing: false })
  const panel = scope.run(() => exports.default.setup(props))
  return { props, panel, calls, stops }
}

test('automatic sending is opt-in and requires a new completion', async t => {
  const f = setup(t)
  f.props.completion++
  await vue.nextTick()
  assert.equal(f.calls.length, 0)
  f.panel.autoReply.value = true
  f.props.prompt = '新问题'
  await vue.nextTick()
  assert.equal(f.calls.length, 0)
  f.props.completion++
  await vue.nextTick()
  assert.deepEqual(f.calls, ['新问题'])
})

test('recording interrupts synchronously and unfinished recognition cannot auto-send', async t => {
  const f = setup(t)
  f.panel.autoReply.value = true
  f.props.recording = true
  assert.equal(f.stops.length, 1)
  f.props.completion++
  await vue.nextTick()
  f.props.recording = false
  f.props.recognizing = true
  f.props.completion++
  await vue.nextTick()
  assert.equal(f.calls.length, 0)
})

test('empty recognition cannot send an old question', async t => {
  const f = setup(t)
  f.panel.autoReply.value = true
  f.panel.question.value = '旧问题'
  f.props.prompt = ' '
  f.props.completion++
  await vue.nextTick()
  assert.equal(f.calls.length, 0)
})
