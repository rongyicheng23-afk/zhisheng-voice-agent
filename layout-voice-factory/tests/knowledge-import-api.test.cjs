const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript')
function setup(fetch, token = 'fixture-token') {
  const exports = {}
  const script = fs.readFileSync(path.join(__dirname, '../src/api/knowledge.ts'), 'utf8')
  vm.runInNewContext(ts.transpileModule(script, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText,
    { exports, require: () => ({ getRuntimeHttpBaseUrl: () => 'http://127.0.0.1:18080' }),
      localStorage: { getItem: () => token }, sessionStorage: { getItem: () => null },
      fetch, FormData, AbortController, setTimeout, clearTimeout, Error })
  return exports
}
test('import sends authenticated multipart and lets the browser set its boundary', async () => {
  let request
  const api = setup(async (url, options) => { request = { url, ...options }; return { ok: true, json: async () => ({ content: '正文' }) } })
  const file = new File(['正文'], 'notice.txt', { type: 'text/plain' })
  assert.equal((await api.knowledgeImport(file)).content, '正文')
  assert.equal(request.url, 'http://127.0.0.1:18080/api/knowledge/import-preview')
  assert.equal(request.headers.Authorization, 'Bearer fixture-token')
  assert.equal(request.headers['Content-Type'], undefined)
  assert.equal(request.body.get('file').name, 'notice.txt'); assert.equal(request.cache, 'no-store')
})
test('missing login stops upload before sending a file', async () => {
  let sent = false
  const api = setup(async () => { sent = true }, null)
  await assert.rejects(api.knowledgeImport(new File(['x'], 'x.txt')), /请先登录/)
  assert.equal(sent, false)
})
test('expected extraction errors retain useful messages while server faults stay generic', async () => {
  const file = new File(['x'], 'x.pdf')
  await assert.rejects(setup(async () => ({ ok: false, status: 422, json: async () => ({ message: '扫描页请先做 OCR' }) })).knowledgeImport(file), /OCR/)
  await assert.rejects(setup(async () => ({ ok: false, status: 500, json: async () => ({ message: 'secret stack trace' }) })).knowledgeImport(file), /文件提取失败/)
})
