<template>
  <div class="page">
    <div class="page-head">
      <div>
        <el-button link @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <h1 class="page-title" style="margin-top: 8px">{{ doc?.title || '病历详情' }}</h1>
        <p class="page-sub">
          {{ DOC_TYPE[doc?.docType] || doc?.docType }} · 患者 {{ doc?.patientId }} · 就诊 {{ doc?.visitId || '-' }}
          · 作者 {{ doc?.authorName || '-' }}
        </p>
      </div>
      <div class="head-actions">
        <el-tag :type="statusType(doc?.status)" size="large">{{ statusLabel(doc?.status) }}</el-tag>
        <el-tag v-if="doc?.sealed" type="danger" effect="dark">已封存</el-tag>
        <el-tag v-else type="info" effect="plain">未封存</el-tag>
      </div>
    </div>

    <div class="toolbar panel panel-pad" style="margin-bottom: 16px">
      <el-button type="primary" :disabled="!isDraft" :loading="saving" @click="save">保存草稿</el-button>
      <el-button :disabled="!isDraft" @click="submitDoc">提交质控</el-button>
      <el-button type="success" :disabled="doc?.status !== 'SUBMITTED'" @click="signDoc">签名</el-button>
      <el-button type="warning" :disabled="doc?.status !== 'SIGNED'" @click="archiveDoc">归档</el-button>
      <el-button :type="doc?.sealed ? 'danger' : 'warning'" :disabled="doc?.status !== 'ARCHIVED'" @click="openSeal">
        {{ doc?.sealed ? '解封' : '封存' }}
      </el-button>
      <span class="spacer" />
      <el-button @click="runQc">运行质控</el-button>
      <el-button @click="verify">验真</el-button>
      <el-button @click="openVersions">版本与对比</el-button>
    </div>

    <div class="editor-grid">
      <div class="panel panel-pad">
        <div class="section-title">
          <span>病历正文</span>
          <span class="text-muted" style="font-size: 12px; font-weight: 400">v{{ doc?.currentVersion || 1 }}</span>
        </div>
        <el-form label-position="top" class="doc-form">
          <div v-for="(section, index) in sections" :key="section.code || index" class="section-block">
            <div class="section-head">
              <span class="section-name">{{ section.title || section.code }}</span>
              <span v-if="section.required" class="required-mark">*</span>
              <span class="section-type">{{ section.type === 'icd' ? 'ICD 编码' : '文本' }}</span>
            </div>

            <template v-if="section.type === 'icd'">
              <div class="icd-rows">
                <div v-for="(item, i) in section.value" :key="i" class="icd-row">
                  <el-input v-model="item.code" placeholder="ICD 编码" style="width: 150px" />
                  <el-input v-model="item.name" placeholder="诊断名称" />
                  <el-checkbox v-model="item.main" :disabled="item.main">主诊断</el-checkbox>
                  <el-button link type="danger" @click="section.value.splice(i, 1)">删除</el-button>
                </div>
                <el-button link type="primary" @click="addIcd(section)">
                  <el-icon><Plus /></el-icon>
                  添加诊断
                </el-button>
              </div>
            </template>
            <el-input
              v-else
              v-model="section.value"
              type="textarea"
              :rows="4"
              :disabled="!isDraft || doc?.sealed"
              placeholder="请输入内容"
            />
          </div>
          <el-empty v-if="!sections.length" description="该模板没有可编辑章节" :image-size="70" />
        </el-form>
      </div>

      <div class="side-col">
        <div class="panel panel-pad">
          <div class="section-title"><span>流程状态</span></div>
          <el-steps direction="vertical" :active="stepActive" finish-status="success" process-status="process">
            <el-step title="创建草稿" :description="fmt(doc?.createdAt)" />
            <el-step title="提交质控" description="提交前自动运行质控规则" />
            <el-step title="医师签名" :description="doc?.signedAt ? fmt(doc.signedAt) : '待签名'" />
            <el-step title="归档封存" :description="doc?.archivedAt ? fmt(doc.archivedAt) : '待归档'" />
          </el-steps>
        </div>

        <div class="panel panel-pad">
          <div class="section-title"><span>归档信息</span></div>
          <div class="kv"><span>归档时间</span><b>{{ fmt(doc?.archivedAt) }}</b></div>
          <div class="kv"><span>归档哈希</span><b class="mono hash">{{ shortHash(doc?.archiveHash) }}</b></div>
          <div class="kv"><span>封存状态</span><b>{{ doc?.sealed ? '已封存' : '未封存' }}</b></div>
        </div>

        <div class="panel panel-pad tip">
          <div class="section-title"><span>提示</span></div>
          <p>签名后内容不可直接修改；如需归档后变更，请按“解封 → 修改 → 重新签名归档”流程操作，全程留痕。</p>
        </div>
      </div>
    </div>

    <el-dialog v-model="sealVisible" :title="sealMode === 'seal' ? '封存病历' : '解封病历'" width="460px">
      <el-alert
        :title="sealMode === 'seal' ? '封存后病历内容不可修改。' : '解封将写入审计日志。'"
        :type="sealMode === 'seal' ? 'warning' : 'info'"
        :closable="false"
        show-icon
        style="margin-bottom: 14px"
      />
      <el-input v-model="sealReason" type="textarea" :rows="3" placeholder="请填写原因" />
      <template #footer>
        <el-button @click="sealVisible = false">取消</el-button>
        <el-button type="warning" :loading="sealing" @click="submitSeal">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="qcVisible" title="质控结果" width="720px">
      <el-alert
        :title="qcResult?.blocked ? '存在阻断级缺陷，提交将被拦截' : '质控已通过，可以提交'"
        :type="qcResult?.blocked ? 'error' : 'success'"
        :closable="false"
        show-icon
        style="margin-bottom: 14px"
      />
      <el-table :data="qcResult?.issues || []" size="small" empty-text="未发现缺陷">
        <el-table-column prop="ruleName" label="规则" width="160" />
        <el-table-column prop="severity" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.severity === 'BLOCK' ? 'danger' : 'warning'" size="small">{{ row.severity }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="defectDesc" label="缺陷描述" />
      </el-table>
    </el-dialog>

    <el-dialog v-model="verifyVisible" title="签名验真" width="620px">
      <el-result
        :icon="verifyData?.valid ? 'success' : 'error'"
        :title="verifyData?.valid ? '验签通过' : '验签失败'"
        :sub-title="verifyData?.message"
      />
      <div class="verify-grid">
        <div class="kv"><span>版本</span><b>v{{ verifyData?.versionNo }}</b></div>
        <div class="kv"><span>签名人</span><b>{{ verifyData?.signerName || '-' }}</b></div>
        <div class="kv"><span>证书序列号</span><b class="mono">{{ verifyData?.certSn || '-' }}</b></div>
        <div class="kv"><span>签名时间</span><b>{{ fmt(verifyData?.signedAt) }}</b></div>
      </div>
    </el-dialog>

    <el-drawer v-model="versionsVisible" title="版本记录与对比" size="620px">
      <div class="version-toolbar">
        <el-select v-model="diffFrom" placeholder="起始版本" style="width: 130px">
          <el-option v-for="v in versions" :key="`f-${v.versionNo}`" :label="`v${v.versionNo}`" :value="v.versionNo" />
        </el-select>
        <el-select v-model="diffTo" placeholder="目标版本" style="width: 130px">
          <el-option v-for="v in versions" :key="`t-${v.versionNo}`" :label="`v${v.versionNo}`" :value="v.versionNo" />
        </el-select>
        <el-button type="primary" plain :loading="diffLoading" @click="loadDiff">对比</el-button>
      </div>

      <el-timeline style="margin-top: 12px">
        <el-timeline-item
          v-for="version in versions"
          :key="version.versionNo"
          :timestamp="`v${version.versionNo} · ${fmt(version.createdAt)} · ${version.createdByName}`"
          placement="top"
        >
          <div class="version-card">
            <div class="version-summary">{{ version.changeSummary || '内容变更' }}</div>
            <el-collapse>
              <el-collapse-item title="查看该版本内容" :name="version.versionNo">
                <pre class="mono json-view">{{ pretty(version.content) }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </el-timeline-item>
      </el-timeline>

      <template v-if="diffResult">
        <el-divider content-position="left">v{{ diffResult.fromVersion }} → v{{ diffResult.toVersion }}</el-divider>
        <el-table :data="diffResult.changes" size="small" empty-text="两个版本内容一致">
          <el-table-column prop="title" label="章节" width="120" />
          <el-table-column prop="changeType" label="变更" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.changeType === 'ADDED' ? 'success' : row.changeType === 'REMOVED' ? 'danger' : 'warning'">
                {{ row.changeType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="before" label="变更前" show-overflow-tooltip />
          <el-table-column prop="after" label="变更后" show-overflow-tooltip />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'

const DOC_TYPE = {
  OUTPATIENT_NOTE: '门诊病历',
  ADMISSION_RECORD: '入院记录',
  FIRST_COURSE: '首次病程',
  DISCHARGE_RECORD: '出院记录'
}
const DOC_STATUS = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '待签名', type: 'warning' },
  SIGNED: { label: '已签名', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'primary' }
}

const route = useRoute()
const router = useRouter()
const doc = ref(null)
const sections = ref([])
const saving = ref(false)

const sealVisible = ref(false)
const sealMode = ref('seal')
const sealReason = ref('')
const sealing = ref(false)

const qcVisible = ref(false)
const qcResult = ref(null)
const verifyVisible = ref(false)
const verifyData = ref(null)

const versionsVisible = ref(false)
const versions = ref([])
const diffFrom = ref(null)
const diffTo = ref(null)
const diffResult = ref(null)
const diffLoading = ref(false)

const isDraft = computed(() => doc.value?.status === 'DRAFT' && !doc.value?.sealed)

const stepActive = computed(() => {
  if (doc.value?.status === 'ARCHIVED') return 4
  if (doc.value?.status === 'SIGNED') return 3
  if (doc.value?.status === 'SUBMITTED') return 2
  return 1
})

function statusLabel(status) {
  return DOC_STATUS[status]?.label || status || '-'
}
function statusType(status) {
  return DOC_STATUS[status]?.type || 'info'
}
function fmt(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}
function shortHash(value) {
  if (!value) return '-'
  return value.length > 18 ? `${value.slice(0, 10)}…${value.slice(-6)}` : value
}
function pretty(value) {
  return JSON.stringify(value, null, 2)
}

function normalizeSections(content) {
  const raw = content?.sections
  if (!Array.isArray(raw)) return []
  return raw.map((section) => {
    let value = section.value
    if (section.type === 'icd') {
      if (Array.isArray(value)) {
        value = value.map((item) =>
          typeof item === 'string' ? { code: item, name: '', main: false } : { main: false, ...item }
        )
      } else {
        value = []
      }
    } else if (value === null || value === undefined) {
      value = ''
    } else if (typeof value !== 'string') {
      value = JSON.stringify(value)
    }
    return { ...section, value }
  })
}

function addIcd(section) {
  const hasMain = section.value.some((item) => item.main)
  section.value.push({ code: '', name: '', main: !hasMain })
}

async function loadDoc() {
  try {
    const data = await http.get(`/emr/documents/${route.params.id}`)
    doc.value = data
    sections.value = normalizeSections(data.content)
  } catch {
    /* 拦截器已提示 */
  }
}

async function save() {
  if (!isDraft.value) {
    ElMessage.warning('仅草稿状态可保存')
    return
  }
  saving.value = true
  try {
    const content = { sections: sections.value.map((section) => ({ ...section })) }
    const updated = await http.put(`/emr/documents/${doc.value.id}`, {
      content,
      changeSummary: '工作台保存'
    })
    doc.value = updated
    sections.value = normalizeSections(updated.content)
    ElMessage.success('已保存')
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}

async function submitDoc() {
  try {
    await http.post(`/emr/documents/${doc.value.id}/submit`)
    ElMessage.success('已提交，等待签名')
    await loadDoc()
  } catch {
    /* 拦截器已提示 */
  }
}

async function signDoc() {
  try {
    await ElMessageBox.confirm('签名后病历内容将锁定，确认签名？', '医师签名', {
      confirmButtonText: '签名',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await http.post(`/emr/documents/${doc.value.id}/sign`)
    ElMessage.success('签名成功')
    await loadDoc()
  } catch {
    /* 取消或失败 */
  }
}

async function archiveDoc() {
  try {
    await ElMessageBox.confirm('确认归档该病历？', '归档', {
      confirmButtonText: '归档',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await http.post(`/emr/documents/${doc.value.id}/archive`)
    ElMessage.success('已归档')
    await loadDoc()
  } catch {
    /* 取消或失败 */
  }
}

function openSeal() {
  sealMode.value = doc.value?.sealed ? 'unseal' : 'seal'
  sealReason.value = ''
  sealVisible.value = true
}

async function submitSeal() {
  if (!sealReason.value.trim()) {
    ElMessage.warning('请填写原因')
    return
  }
  sealing.value = true
  try {
    await http.post(`/emr/documents/${doc.value.id}/${sealMode.value}`, { reason: sealReason.value })
    ElMessage.success(sealMode.value === 'seal' ? '已封存' : '已解封')
    sealVisible.value = false
    await loadDoc()
  } catch {
    /* 拦截器已提示 */
  } finally {
    sealing.value = false
  }
}

async function runQc() {
  try {
    qcResult.value = await http.post(`/emr/documents/${doc.value.id}/qc/run`)
    qcVisible.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

async function verify() {
  try {
    verifyData.value = await http.get(`/emr/documents/${doc.value.id}/verify`)
    verifyVisible.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

async function openVersions() {
  try {
    versions.value = await http.get(`/emr/documents/${doc.value.id}/versions`)
    diffResult.value = null
    if (versions.value.length >= 2) {
      diffFrom.value = versions.value[versions.value.length - 2].versionNo
      diffTo.value = versions.value[versions.value.length - 1].versionNo
    } else if (versions.value.length === 1) {
      diffFrom.value = versions.value[0].versionNo
      diffTo.value = versions.value[0].versionNo
    }
    versionsVisible.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

async function loadDiff() {
  if (!diffFrom.value || !diffTo.value) {
    ElMessage.warning('请选择两个版本')
    return
  }
  diffLoading.value = true
  try {
    diffResult.value = await http.get(`/emr/documents/${doc.value.id}/versions/diff`, {
      params: { from: diffFrom.value, to: diffTo.value }
    })
  } catch {
    /* 拦截器已提示 */
  } finally {
    diffLoading.value = false
  }
}

onMounted(loadDoc)
</script>

<style scoped>
.head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
}

.section-block {
  padding: 14px 0 16px;
  border-bottom: 1px dashed #e3ebed;
}

.section-block:last-child {
  border-bottom: none;
}

.section-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.section-name {
  font-weight: 700;
  font-size: 14px;
}

.required-mark {
  color: #b91c1c;
  font-weight: 700;
}

.section-type {
  margin-left: auto;
  font-size: 11px;
  color: var(--his-muted);
  background: #f3f8f8;
  border-radius: 20px;
  padding: 2px 8px;
}

.icd-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.icd-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.side-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.kv {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 0;
  font-size: 12px;
  border-bottom: 1px dashed #edf3f3;
}

.kv:last-child {
  border-bottom: none;
}

.kv span {
  color: var(--his-muted);
}

.kv b {
  font-weight: 600;
  text-align: right;
  word-break: break-all;
}

.hash {
  font-size: 11px;
}

.tip p {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--his-muted);
}

.verify-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 18px;
  padding: 0 8px;
}

.version-toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.version-card {
  padding: 10px 12px;
  border-radius: 10px;
  background: #f8fbfb;
  border: 1px solid var(--his-line);
}

.version-summary {
  font-size: 13px;
  font-weight: 600;
}

.json-view {
  margin: 4px 0 0;
  max-height: 260px;
  overflow: auto;
  font-size: 11px;
  line-height: 1.6;
  background: #ffffff;
  border-radius: 8px;
  padding: 10px;
  border: 1px solid var(--his-line);
}

@media (max-width: 1100px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }
}
</style>
