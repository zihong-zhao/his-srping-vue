<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">病历中心</h1>
        <p class="page-sub">按患者或就诊检索病历，完成创建、编辑、提交、签名与归档。</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新建病历
      </el-button>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar">
        <el-input v-model="filters.patientId" placeholder="患者 ID" clearable style="width: 130px" @keyup.enter="load" />
        <el-input v-model="filters.visitId" placeholder="就诊 ID" clearable style="width: 130px" @keyup.enter="load" />
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option v-for="(item, key) in DOC_STATUS" :key="key" :label="item.label" :value="key" />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="load">查询</el-button>
        <span class="spacer" />
        <span class="text-muted" style="font-size: 12px">患者 ID 与就诊 ID 至少填写一项</span>
      </div>

      <el-table v-loading="loading" :data="documents" stripe empty-text="暂无病历，请先按患者 ID 查询或新建">
        <el-table-column prop="id" label="病历 ID" width="150">
          <template #default="{ row }"><span class="mono">{{ row.id }}</span></template>
        </el-table-column>
        <el-table-column prop="patientId" label="患者" width="90" />
        <el-table-column prop="visitId" label="就诊" width="90">
          <template #default="{ row }">{{ row.visitId || '-' }}</template>
        </el-table-column>
        <el-table-column label="文书类型" width="120">
          <template #default="{ row }">{{ DOC_TYPE[row.docType] || row.docType }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentVersion" label="版本" width="70" />
        <el-table-column prop="authorName" label="作者" width="100" />
        <el-table-column label="创建时间" width="165">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="$router.push(`/documents/${row.id}`)">打开</el-button>
            <el-button v-if="row.status === 'SIGNED'" link type="success" @click="archive(row)">归档</el-button>
            <el-button link type="warning" @click="openSeal(row)">
              {{ row.status === 'ARCHIVED' ? '封存/解封' : '封存' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="新建病历" width="520px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="92px">
        <el-form-item label="患者 ID" prop="patientId">
          <el-input v-model="createForm.patientId" placeholder="例如 1001" />
        </el-form-item>
        <el-form-item label="就诊 ID">
          <el-input v-model="createForm.visitId" placeholder="住院病历建议填写" />
        </el-form-item>
        <el-form-item label="文书类型" prop="docType">
          <el-select v-model="createForm.docType" style="width: 100%" @change="loadTemplates">
            <el-option v-for="(item, key) in DOC_TYPE" :key="key" :label="item" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="模板" prop="templateId">
          <el-select v-model="createForm.templateId" style="width: 100%" placeholder="请选择模板" :loading="templateLoading">
            <el-option v-for="tpl in templates" :key="tpl.id" :label="`${tpl.name}（v${tpl.version}）`" :value="tpl.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="createForm.title" placeholder="留空则使用模板名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建并打开</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sealVisible" :title="sealMode === 'seal' ? '封存病历' : '解封病历'" width="460px">
      <el-alert
        :title="sealMode === 'seal' ? '封存后病历内容不可修改，仅可凭理由解封。' : '解封将记录审计日志。'"
        :type="sealMode === 'seal' ? 'warning' : 'info'"
        :closable="false"
        show-icon
        style="margin-bottom: 14px"
      />
      <el-form label-width="70px">
        <el-form-item label="原因" required>
          <el-input v-model="sealReason" type="textarea" :rows="3" placeholder="请填写封存或解封原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="sealVisible = false">取消</el-button>
        <el-button type="warning" :loading="sealing" @click="submitSeal">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
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

const filters = reactive({ patientId: '1001', visitId: '', status: '' })
const documents = ref([])
const loading = ref(false)

const createVisible = ref(false)
const creating = ref(false)
const templateLoading = ref(false)
const templates = ref([])
const createFormRef = ref()
const createForm = reactive({ patientId: '1001', visitId: '', docType: 'OUTPATIENT_NOTE', templateId: null, title: '' })
const createRules = {
  patientId: [{ required: true, message: '请输入患者 ID', trigger: 'blur' }],
  docType: [{ required: true, message: '请选择文书类型', trigger: 'change' }],
  templateId: [{ required: true, message: '请选择模板', trigger: 'change' }]
}

const sealVisible = ref(false)
const sealMode = ref('seal')
const sealReason = ref('')
const sealing = ref(false)
const sealTarget = ref(null)

function statusLabel(status) {
  return DOC_STATUS[status]?.label || status
}
function statusType(status) {
  return DOC_STATUS[status]?.type || 'info'
}
function fmt(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function load() {
  if (!filters.patientId && !filters.visitId) {
    ElMessage.warning('请至少填写患者 ID 或就诊 ID')
    return
  }
  loading.value = true
  try {
    documents.value = await http.get('/emr/documents', { params: { ...filters } })
  } catch {
    documents.value = []
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  createForm.patientId = filters.patientId || '1001'
  createForm.visitId = filters.visitId || ''
  createForm.docType = 'OUTPATIENT_NOTE'
  createForm.templateId = null
  createForm.title = ''
  createVisible.value = true
  await loadTemplates()
}

async function loadTemplates() {
  templateLoading.value = true
  try {
    templates.value = await http.get('/emr/templates', { params: { docType: createForm.docType } })
    if (templates.value.length && !templates.value.some((t) => t.id === createForm.templateId)) {
      createForm.templateId = templates.value[0].id
    }
  } catch {
    templates.value = []
  } finally {
    templateLoading.value = false
  }
}

async function submitCreate() {
  await createFormRef.value.validate()
  creating.value = true
  try {
    const doc = await http.post('/emr/documents', {
      patientId: Number(createForm.patientId),
      visitId: createForm.visitId ? Number(createForm.visitId) : null,
      docType: createForm.docType,
      templateId: createForm.templateId,
      title: createForm.title || null
    })
    ElMessage.success('病历已创建')
    createVisible.value = false
    documents.value.unshift(doc)
    window.location.href = `/documents/${doc.id}`
  } catch {
    /* 拦截器已提示 */
  } finally {
    creating.value = false
  }
}

async function archive(row) {
  try {
    await ElMessageBox.confirm('归档后病历将不可修改，确认归档？', '归档病历', {
      confirmButtonText: '归档',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await http.post(`/emr/documents/${row.id}/archive`)
    ElMessage.success('已归档')
    await load()
  } catch {
    /* 取消或失败 */
  }
}

function openSeal(row) {
  sealTarget.value = row
  sealMode.value = row.status === 'ARCHIVED' ? 'unseal' : 'seal'
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
    await http.post(`/emr/documents/${sealTarget.value.id}/${sealMode.value}`, { reason: sealReason.value })
    ElMessage.success(sealMode.value === 'seal' ? '已封存' : '已解封')
    sealVisible.value = false
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    sealing.value = false
  }
}

onMounted(load)
</script>
