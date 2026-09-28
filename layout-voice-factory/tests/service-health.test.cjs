const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm'), ts = require('typescript')
function setup(fetch = async () => ({})) {
  const exports = {}
  vm.runInNewContext(ts.transpileModule(fs.readFileSync(path.join(__dirname, '../src/libs/service-health.ts'), 'utf8'),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText,
  { exports, require: () => ({ getRuntimeHttpBaseUrl: () => 'http://local' }), fetch, AbortController, setTimeout, clearTimeout })
  return exports
}
test('wrapper success never implies a model is available', () => {
  const api = setup()
  for (const data of [{ code: 200, data: { httpReachable: false } }, { status: 'success' }, null])
    assert.ok(api.parseServiceHealth(data).services.every(s => s.status === 'unknown'))
})
test('each actual probe controls its own status; missing and duplicate probes are unknown', () => {
  const { services } = setup().parseServiceHealth({ services: [
    { name: 'tts', status: 'DOWN' }, { name: 'mysql', status: 'UP' }, { name: 'funasr-http', status: 'UNKNOWN' },
    { name: 'minio', status: 'UP' }, { name: 'minio', status: 'DOWN' }, null
  ] })
  assert.equal(services.find(s => s.key === 'tts').status, 'offline')
  assert.equal(services.find(s => s.key === 'mysql').status, 'online')
  assert.ok(services.filter(s => !['tts', 'mysql'].includes(s.key)).every(s => s.status === 'unknown'))
})
test('health fetch uses no-store and fails on a backend HTTP error', async () => {
  let request
  const api = setup(async (url, opts) => { request = { url, ...opts }; return { ok: false } })
  await assert.rejects(api.fetchServiceHealth())
  assert.equal(request.url, 'http://local/api/system/status'); assert.equal(request.cache, 'no-store')
})
