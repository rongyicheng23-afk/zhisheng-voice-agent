const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript')
const vue = require('vue')

test('long diff renders only a page and replacing comparison resets paging', async () => {
  const exports = {}
  const source = fs.readFileSync(path.join(__dirname, '../src/components/KnowledgeDiff.vue'), 'utf8').match(/<script lang="ts">([\s\S]*?)<\/script>/)[1]
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
  vm.runInNewContext(code, { exports, require: () => vue })
  const props = vue.reactive({ value: { lines: Array.from({ length: 201 }, (_, i) => ({ text: String(i) })) } })
  const scope = vue.effectScope()
  try {
    const page = scope.run(() => exports.default.setup(props))
    assert.equal(page.pageCount.value, 3); assert.equal(page.visibleLines.value.length, 100)
    page.page.value = 2; assert.equal(page.visibleLines.value.length, 1); assert.equal(page.visibleLines.value[0].text, '200')
    props.value = { lines: [{ text: 'new' }] }; await vue.nextTick()
    assert.equal(page.page.value, 0); assert.equal(page.visibleLines.value[0].text, 'new')
  } finally { scope.stop() }
})
