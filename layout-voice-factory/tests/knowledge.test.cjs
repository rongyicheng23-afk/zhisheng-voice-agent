const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript')

test('ambiguous save retries reuse request ID; changed content uses a new ID', async () => {
  const { page, requests } = setup(async () => { throw new Error('network lost') })
  await page.saveDraft(); await page.saveDraft()
  assert.equal(requests[0].body.requestId, requests[1].body.requestId)
  page.form.content += '已更新'
  await page.saveDraft()
  assert.notEqual(requests[1].body.requestId, requests[2].body.requestId)
})

test('replayed save does not duplicate a document already loaded by refresh', async () => {
  const { page } = setup(async () => ({ id: 'existing', status: 'DRAFT' }))
  page.documents.value = [{ id: 'existing' }]
  await page.saveDraft()
  assert.equal(page.documents.value.length, 1)
})
function setup(handler = async () => ({})) {
  const requests = [], exports = {}
  const script = fs.readFileSync(path.join(__dirname, '../src/views/Knowledge.vue'), 'utf8').match(/<script lang="ts">([\s\S]*?)<\/script>/)[1]
  const code = ts.transpileModule(script, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText
  vm.runInNewContext(code, { exports, TextDecoder, crypto: require('node:crypto').webcrypto, require: name => name === 'vue'
    ? { defineComponent: x => x, reactive: x => x, ref: value => ({ value }), onMounted() {}, onBeforeUnmount() {} }
    : { knowledgeRequest: async (url, body) => { requests.push({ url, body }); return handler(url, body) } } })
  const page = exports.default.setup()
  Object.assign(page.form, { title: '通知', publisher: '发布方', sourceVersion: 'v1', validFrom: '2026-09-01', validUntil: '2026-10-01', content: '报名材料需学生证。' })
  return { page, requests }
}
test('saving creates a draft only and prevents duplicate submits', async () => {
  const { page, requests } = setup(async () => ({ id: 'doc', status: 'DRAFT' }))
  await Promise.all([page.saveDraft(), page.saveDraft()])
  assert.equal(requests.length, 1)
  assert.equal(requests[0].url, '/documents')
  assert.equal(page.documents.value[0].status, 'DRAFT')
  assert.match(page.status.value, /核对/)
})
test('failed save preserves draft', async () => {
  const { page } = setup(async () => { throw new Error('暂不可用') })
  await page.saveDraft()
  assert.equal(page.form.content, '报名材料需学生证。')
  assert.equal(page.busy.value, false)
})
test('invalid dates and required fields stop before networking', async () => {
  const { page, requests } = setup()
  page.form.validUntil = '2020-01-01'
  await page.saveDraft()
  assert.equal(requests.length, 0)
})
test('publishing sends revision and successful mutation is not retried after refresh failure', async () => {
  const { page, requests } = setup(async url => { if (url === '/documents') throw new Error('refresh'); return {} })
  await page.changeStatus({ id: 'doc', revision: 8 }, 'PUBLISHED')
  assert.equal(requests[0].body.revision, 8)
  assert.equal(requests.length, 2)
  assert.match(page.status.value, /已保存.*刷新失败/)
})
test('new version preserves series without adding immutable identifiers to form', () => {
  const { page } = setup()
  page.newVersion({ ...page.form, id: 'old', seriesId: 'series', revision: 4 })
  assert.equal(page.form.seriesId, 'series')
  assert.equal(page.form.sourceVersion, '')
  assert.equal(page.form.id, undefined)
})
test('failed search removes old citations instead of presenting them as a new answer', async () => {
  const { page } = setup(async () => { throw new Error('search failed') })
  page.question.value = '报名材料'
  page.hits.value = [{ id: 'old' }]
  await page.search()
  assert.equal(page.hits.value.length, 0)
})
test('oversized text imports are rejected without reading', async () => {
  const { page } = setup()
  await page.importText({ target: { files: [{ name: 'a.txt', size: 200000, arrayBuffer() { throw new Error('must not read') } }] } })
  assert.match(page.status.value, /100 KB/)
  assert.equal(page.form.content, '报名材料需学生证。')
})
