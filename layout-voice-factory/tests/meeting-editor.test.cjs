const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

function setup({ failSave = false, failHistory = false, conflict = false } = {}) {
  const requests = [], messages = []
  const source = fs.readFileSync(path.join(__dirname, '../src/views/MeetingNoteDetail.vue'), 'utf8')
    .match(/<script lang="ts">([\s\S]*?)<\/script>/)[1]
  const code = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020
  } }).outputText
  const exports = {}
  const modules = {
    vue: { ref: value => ({ value }), reactive: value => value,
      computed: fn => ({ get value() { return fn() } }), defineComponent: x => x,
      onMounted() {}, onBeforeUnmount() {}, nextTick: async () => {} },
    'vue-router': { useRoute: () => ({ params: { meetingId: 1 } }), useRouter: () => ({ push() {} }) },
    'element-plus': { ElMessage: Object.fromEntries(['error', 'warning', 'success', 'info']
      .map(level => [level, text => messages.push({ level, text })])) },
    '@/store': { default: { state: { token: 'fixture', user: { isLogin: true, _id: 1 } } } },
    '@/api/meeting': {
      applyMeetingCorrection: async (id, payload) => {
        requests.push(payload)
        if (conflict) return { code: 409, msg: '纪要已被更新，请保留草稿', data: null }
        if (failSave) throw new Error('fixture failure')
        return { code: 200, data: { id, ...payload } }
      },
      getMeetingRevisions: async () => {
        if (failHistory) throw new Error('fixture history failure')
        return { code: 200, data: [] }
      }
    }
  }
  vm.runInNewContext(code, { exports, require: name => modules[name] || {}, URL, console })
  const page = exports.default.setup()
  Object.assign(page.detail, { title: '会议', status: 'SUCCESS', correctionToken: 'original-token', speakerSegments: [
    { id: 1, speakerName: '发言人1', transcript: '预算 A', startMs: 0, endMs: 1000, matchScore: .8 },
    { id: 2, speakerName: '发言人1', transcript: '时间 B', startMs: 1000, endMs: 2000 },
    { id: 3, speakerName: '发言人2', transcript: '预算 C', startMs: 2000, endMs: 3000 }
  ] })
  return { page, requests, messages }
}

test('search combines speaker and text filters without modifying data', () => {
  const { page } = setup()
  page.segmentQuery.value = '预算'
  page.speakerFilter.value = '发言人1'
  assert.equal(page.visibleSegments.value.length, 1)
  assert.equal(page.segmentEditorList.value.length, 3)
  page.segmentQuery.value = 'a'
  assert.equal(page.visibleSegments.value[0].id, 1)
})

test('bulk merge affects entire source group, only in draft, and updates timeline', () => {
  const { page, requests } = setup()
  page.enterEditMode()
  page.segmentQuery.value = '预算'
  page.bulkSource.value = '发言人1'
  page.bulkTarget.value = ' 发言人2 '
  assert.equal(page.bulkAffectedCount.value, 2)
  page.applyBulkSpeaker()
  assert.equal(page.speakerGroups.value.length, 1)
  assert.equal(page.speakerGroups.value[0].count, 3)
  assert.equal(page.timelineItems.value[0].speakerName, '发言人2')
  assert.equal(page.timelineLegend.value.length, 1)
  assert.equal(page.correctionForm.speakerSegments[0].matchScore, undefined)
  assert.equal(page.detail.speakerSegments[0].speakerName, '发言人1')
  assert.equal(requests.length, 0)
  page.cancelEditMode()
  assert.equal(page.speakerGroups.value.length, 2)
})

test('saving filtered view submits every segment and guards duplicate submit', async () => {
  const { page, requests } = setup()
  page.enterEditMode()
  page.segmentQuery.value = '预算'
  await Promise.all([page.saveCorrection(), page.saveCorrection()])
  assert.equal(requests.length, 1)
  assert.equal(requests[0].speakerSegments.length, 3)
  assert.equal(page.editMode.value, false)
})

test('failed save preserves draft for retry', async () => {
  const { page } = setup({ failSave: true })
  page.enterEditMode()
  page.bulkSource.value = '发言人1'
  page.bulkTarget.value = '人工确认组'
  page.applyBulkSpeaker()
  await page.saveCorrection()
  assert.equal(page.editMode.value, true)
  assert.equal(page.correctionForm.speakerSegments[0].speakerName, '人工确认组')
  assert.equal(page.saveLoading.value, false)
})

test('save sends the version captured on entry and conflict preserves every draft', async () => {
  const { page, requests, messages } = setup({ conflict: true })
  page.enterEditMode()
  page.detail.correctionToken = 'newer-page-token'
  page.correctionForm.speakerSegments[0].speakerName = '我的新名称'
  await page.saveCorrection()
  assert.equal(requests[0].correctionToken, 'original-token')
  assert.equal(page.editMode.value, true)
  assert.equal(page.correctionForm.speakerSegments[0].speakerName, '我的新名称')
  assert.match(messages.at(-1).text, /已被更新/)
})

test('detail without a correction version cannot start editing', () => {
  const { page, messages } = setup()
  page.detail.correctionToken = undefined
  page.enterEditMode()
  assert.equal(page.editMode.value, false)
  assert.match(messages.at(-1).text, /校正版本/)
})

test('history refresh failure after successful save does not invite duplicate writes', async () => {
  const { page, requests, messages } = setup({ failHistory: true })
  page.enterEditMode()
  await page.saveCorrection()
  await page.saveCorrection()
  assert.equal(requests.length, 1)
  assert.equal(page.editMode.value, false)
  assert.equal(messages.at(-1).level, 'warning')
  assert.match(messages.at(-1).text, /已保存/)
})

test('read-only and in-flight states cannot apply bulk edits or discard drafts', () => {
  const { page } = setup()
  page.bulkSource.value = '发言人1'
  page.bulkTarget.value = '新组'
  page.applyBulkSpeaker()
  assert.equal(page.detail.speakerSegments[0].speakerName, '发言人1')
  page.enterEditMode()
  page.bulkSource.value = '发言人1'
  page.bulkTarget.value = '新组'
  page.saveLoading.value = true
  page.applyBulkSpeaker()
  page.cancelEditMode()
  assert.equal(page.editMode.value, true)
  assert.equal(page.correctionForm.speakerSegments[0].speakerName, '发言人1')
})

test('invalid or unchanged target does not modify drafts', () => {
  const { page } = setup()
  page.enterEditMode()
  page.bulkSource.value = '发言人1'
  for (const target of ['', '   ', 'x'.repeat(65), '发言人1']) {
    page.bulkTarget.value = target
    page.applyBulkSpeaker()
    assert.equal(page.correctionForm.speakerSegments[0].speakerName, '发言人1')
  }
})

test('unchanged full transcript is omitted while explicit full-text edits are preserved', async () => {
  const { page, requests } = setup()
  page.detail.fullTranscript = '旧全文'
  page.enterEditMode()
  page.correctionForm.speakerSegments[0].transcript = '已校正片段'
  await page.saveCorrection()
  assert.equal(requests[0].fullTranscript, undefined)
  const other = setup()
  other.page.enterEditMode()
  other.page.correctionForm.fullTranscript = '手工全文'
  await other.page.saveCorrection()
  assert.equal(other.requests[0].fullTranscript, '手工全文')
})

test('unfinished or failed meeting cannot enter correction mode', () => {
  for (const status of ['PENDING', 'FAILED', undefined]) {
    const { page } = setup()
    page.detail.status = status
    page.enterEditMode()
    assert.equal(page.editMode.value, false)
  }
})
