const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

function setup() {
  const messages = [], requests = []
  const source = fs.readFileSync(path.join(__dirname, '../src/views/TextToVoice.vue'), 'utf8')
    .match(/<script lang="ts">([\s\S]*?)<\/script>/)[1]
  const code = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020
  } }).outputText
  const exports = {}
  const modules = {
    vue: { ref: value => ({ value }), computed: fn => ({ get value() { return fn() } }),
      defineComponent: x => x, onBeforeUnmount() {} },
    'vue-router': { useRouter: () => ({ push() {} }) },
    'element-plus': { ElMessage: Object.fromEntries(['error', 'warning', 'success', 'info']
      .map(name => [name, message => messages.push(message)])) },
    '@/store': { default: { state: { token: 'fixture', user: { isLogin: true, _id: 1 } } } },
    '@/api/tts': { synthesizeTextToVoice: async data => {
      requests.push(data); return { code: 503, msg: '语音合成服务忙，请稍后手动重试' }
    } }
  }
  vm.runInNewContext(code, { exports, require: name => modules[name] || {},
    FormData: class { append() {} }, URL, console })
  return { page: exports.default.setup(), requests, messages }
}

test('counts code points and rejects long text before sending', async () => {
  const f = setup()
  f.page.text.value = '😀'.repeat(500)
  assert.equal(f.page.textLength.value, 500)
  f.page.text.value += '中'
  await f.page.convertToVoice()
  assert.equal(f.requests.length, 0)
  assert.match(f.messages.at(-1), /500字符/)
})
test('rejects empty and oversized reference audio', () => {
  const f = setup()
  for (const size of [0, 20 * 1024 * 1024]) {
    f.page.handleAudioChange({ raw: { size, name: 'a.wav' } })
    assert.equal(f.page.audioFileName.value, '')
    assert.match(f.messages.at(-1), /20 MB/)
  }
})
test('displays safe busy message without retrying', async () => {
  const f = setup()
  f.page.text.value = '你好'
  f.page.handleAudioChange({ raw: { size: 100, name: 'a.wav' } })
  await f.page.convertToVoice()
  assert.equal(f.requests.length, 1)
  assert.match(f.messages.at(-1), /服务忙.*手动重试/)
  assert.equal(f.page.status.value, 'error')
})
test('ignores duplicate submit while already processing', async () => {
  const f = setup()
  f.page.status.value = 'processing'
  await f.page.convertToVoice()
  assert.equal(f.requests.length, 0)
})
