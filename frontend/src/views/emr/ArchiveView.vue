<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">病案管理</h1>
        <p class="page-sub">病案借阅、复印审批与病案首页编码核对。</p>
      </div>
    </div>

    <el-tabs v-model="tab" class="panel panel-pad">
      <el-tab-pane label="借阅申请" name="borrow">
        <div class="toolbar">
          <el-select v-model="borrowFilter" placeholder="全部状态" clearable style="width: 150px" @change="loadBorrows">
            <el-option v-for="(item, key) in BORROW_STATUS" :key="key" :label="item.label" :value="key" />
          </el-select>
          <el-button type="primary" plain @click="loadBorrows">刷新</el-button>
          <span class="spacer" />
          <el-button type="primary" @click="openBorrowCreate">发起借阅</el-button>
        </div>
        <el-table v-loading="borrowLoading" :data="borrows" stripe empty-text="暂无借阅申请">
          <el-table-column prop="docTitle" label="病历" min-width="200" show-overflow-tooltip />
          <el-table-column prop="applicantName" label="申请人" width="110" />
          <el-table-column prop="reason" label="理由" min-width="180" show-overflow-tooltip />
          <el-table-column prop="days" label="天数" width="70" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status, 'borrow')">{{ statusLabel(row.status, 'borrow') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="到期" width="160">
            <template #default="{ row }">{{ fmt(row.dueAt) }}</template>
          </el-table-column>
          <el-table-column prop="approverName" label="审批人" width="100" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <template v-if="auth.isPrivileged()">
                <el-button v-if="row.status === 'PENDING'" link type="success" @click="decideBorrow(row, 'approve')">通过</el-button>
                <el-button v-if="row.status === 'PENDING'" link type="danger" @click="decideBorrow(row, 'reject')">驳回</el-button>
              </template>
              <el-button v-if="row.status === 'APPROVED'" link type="primary" @click="returnBorrow(row)">归还</el-button>
              <span v-if="row.status === 'RETURNED'" class="text-muted" style="font-size: 12px">{{ fmt(row.returnedAt) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="复印申请" name="copy">
        <div class="toolbar">
          <el-select v-model="copyFilter" placeholder="全部状态" clearable style="width: 150px" @change="loadCopies">
            <el-option v-for="(item, key) in COPY_STATUS" :key="key" :label="item.label" :value="key" />
          </el-select>
          <el-button type="primary" plain @click="loadCopies">刷新</el-button>
          <span class="spacer" />
          <el-button type="primary" @click="openCopyCreate">发起复印</el-button>
        </div>
        <el-table v-loading="copyLoading" :data="copies" stripe empty-text="暂无复印申请">
          <el-table-column prop="docTitle" label="病历" min-width="200" show-overflow-tooltip />
          <el-table-column prop="applicantName" label="申请人" width="110" />
          <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip />
          <el-table-column prop="copies" label="份数" width="70" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="statusType(row.status, 'copy')">{{ statusLabel(row.status, 'copy') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approverName" label="审批人" width="100" />
          <el-table-column label="完成时间" width="160">
            <template #default="{ row }">{{ fmt(row.completedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <template v-if="auth.isPrivileged()">
                <el-button v-if="row.status === 'PENDING'" link type="success" @click="decideCopy(row, 'approve')">通过</el-button>
                <el-button v-if="row.status === 'PENDING'" link type="danger" @click="decideCopy(row, 'reject')">驳回</el-button>
              </template>
              <el-button v-if="row.status === 'APPROVED'" link type="primary" @click="completeCopy(row)">完成复印</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="病案首页" name="front">
        <div class="toolbar">
          <el-input v-model="frontFilters.patientId" placeholder="患者 ID" clearable style="width: 140px" @keyup.enter="loadFrontPages" />
          <el-input v-model="frontFilters.visitId" placeholder="就诊 ID" clearable style="width: 140px" @keyup.enter="loadFrontPages" />
          <el-button type="primary" plain @click="loadFrontPages">查询</el-button>
          <span class="spacer" />
          <el-button type="primary" @click="openFrontCreate">新建首页</el-button>
        </div>
        <el-table v-loading="frontLoading" :data="frontPages" stripe empty-text="暂无病案首页">
          <el-table-column prop="id" label="ID" width="140">
            <template #default="{ row }"><span class="mono">{{ row.id }}</span></template>
          </el-table-column>
          <el-table-column prop="patientId" label="患者" width="90" />
          <el-table-column prop="visitId" label="就诊" width="90" />
          <el-table-column prop="inpatientNo" label="住院号" width="120" />
          <el-table-column prop="dischargeWay" label="离院方式" width="110" />
          <el-table-column label="诊断" width="70">
            <template #default="{ row }">{{ row.diagnoses?.length || 0 }} 条</template>
          </el-table-column>
          <el-table-column label="费用合计" width="110">
            <template #default="{ row }">{{ row.cost?.total ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'VERIFIED' ? 'success' : 'info'">
                {{ row.status === 'VERIFIED' ? '已核对' : '草稿' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="verifiedByName" label="核对人" width="100" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openFrontEdit(row)">编辑</el-button>
              <el-button v-if="auth.isPrivileged()" link type="success" @click="verifyFront(row)">核对</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="borrowCreateVisible" title="发起病案借阅" width="500px">
      <el-form label-width="90px">
        <el-form-item label="病历 ID" required>
          <el-input v-model="borrowForm.docId" placeholder="请输入病历 ID" />
        </el-form-item>
        <el-form-item label="借阅理由" required>
          <el-input v-model="borrowForm.reason" type="textarea" :rows="3" placeholder="例如：医疗纠纷调阅" />
        </el-form-item>
        <el-form-item label="借阅天数" required>
          <el-input-number v-model="borrowForm.days" :min="1" :max="30" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="borrowCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitBorrow">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="copyCreateVisible" title="发起病案复印" width="500px">
      <el-form label-width="90px">
        <el-form-item label="病历 ID" required>
          <el-input v-model="copyForm.docId" placeholder="请输入病历 ID" />
        </el-form-item>
        <el-form-item label="复印用途" required>
          <el-input v-model="copyForm.purpose" type="textarea" :rows="3" placeholder="例如：患者本人留存" />
        </el-form-item>
        <el-form-item label="复印份数" required>
          <el-input-number v-model="copyForm.copies" :min="1" :max="20" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="copyCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCopy">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="decisionVisible" :title="decisionTitle" width="480px">
      <el-input v-model="decisionNote" type="textarea" :rows="3" :placeholder="decisionMode === 'reject' ? '驳回原因必填' : '审批备注（可选）'" />
      <template #footer>
        <el-button @click="decisionVisible = false">取消</el-button>
        <el-button :type="decisionMode === 'reject' ? 'danger' : 'success'" :loading="submitting" @click="submitDecision">
          确认{{ decisionMode === 'reject' ? '驳回' : '通过' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="frontVisible" :title="frontForm.id ? '编辑病案首页' : '新建病案首页'" width="860px" top="5vh">
      <el-form label-width="92px" :model="frontForm">
        <div class="form-grid">
          <el-form-item label="患者 ID" required>
            <el-input v-model="frontForm.patientId" />
          </el-form-item>
          <el-form-item label="就诊 ID" required>
            <el-input v-model="frontForm.visitId" />
          </el-form-item>
          <el-form-item label="病历 ID">
            <el-input v-model="frontForm.docId" />
          </el-form-item>
          <el-form-item label="住院号">
            <el-input v-model="frontForm.inpatientNo" />
          </el-form-item>
          <el-form-item label="入院时间">
            <el-date-picker v-model="frontForm.admissionDate" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          </el-form-item>
          <el-form-item label="出院时间">
            <el-date-picker v-model="frontForm.dischargeDate" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
          </el-form-item>
          <el-form-item label="入院科室">
            <el-input v-model="frontForm.admissionDept" />
          </el-form-item>
          <el-form-item label="出院科室">
            <el-input v-model="frontForm.dischargeDept" />
          </el-form-item>
          <el-form-item label="离院方式" required>
            <el-select v-model="frontForm.dischargeWay" style="width: 100%" placeholder="请选择">
              <el-option v-for="way in DISCHARGE_WAYS" :key="way" :label="way" :value="way" />
            </el-select>
          </el-form-item>
          <el-form-item label="麻醉方式">
            <el-input v-model="frontForm.anesthesiaType" />
          </el-form-item>
          <el-form-item label="主治医师">
            <el-input v-model="frontForm.attendingDoctor" />
          </el-form-item>
        </div>

        <el-divider content-position="left">诊断（至少一条，主诊断唯一）</el-divider>
        <div class="icd-picker">
          <el-select
            v-model="diagnosisPick"
            filterable
            remote
            clearable
            placeholder="输入 ICD-10 编码或名称检索后添加"
            :remote-method="(q) => searchIcd(q, 'ICD10', 'diagnosis')"
            :loading="icdLoading"
            style="width: 100%"
            @change="addDiagnosis"
          >
            <el-option v-for="item in diagnosisOptions" :key="item.code" :label="`${item.code} · ${item.name}`" :value="item.code" />
          </el-select>
        </div>
        <div class="row-list">
          <div v-for="(item, index) in frontForm.diagnoses" :key="index" class="row-item">
            <span class="mono code">{{ item.code }}</span>
            <span class="row-name">{{ item.name }}</span>
            <el-checkbox v-model="item.main" @change="(val) => setMainDiagnosis(index, val)">主诊断</el-checkbox>
            <el-button link type="danger" @click="frontForm.diagnoses.splice(index, 1)">删除</el-button>
          </div>
          <el-empty v-if="!frontForm.diagnoses.length" description="尚未添加诊断" :image-size="56" />
        </div>

        <el-divider content-position="left">手术</el-divider>
        <div class="icd-picker">
          <el-select
            v-model="surgeryPick"
            filterable
            remote
            clearable
            placeholder="输入 ICD-9-CM-3 编码或名称检索后添加"
            :remote-method="(q) => searchIcd(q, 'ICD9', 'surgery')"
            :loading="icdLoading"
            style="width: 100%"
            @change="addSurgery"
          >
            <el-option v-for="item in surgeryOptions" :key="item.code" :label="`${item.code} · ${item.name}`" :value="item.code" />
          </el-select>
        </div>
        <div class="row-list">
          <div v-for="(item, index) in frontForm.surgeries" :key="index" class="row-item">
            <span class="mono code">{{ item.code }}</span>
            <span class="row-name">{{ item.name }}</span>
            <el-button link type="danger" @click="frontForm.surgeries.splice(index, 1)">删除</el-button>
          </div>
          <el-empty v-if="!frontForm.surgeries.length" description="无手术可留空" :image-size="56" />
        </div>

        <el-divider content-position="left">费用归类</el-divider>
        <div class="form-grid cost-grid">
          <el-form-item v-for="field in COST_FIELDS" :key="field.key" :label="field.label">
            <el-input-number v-model="frontForm.cost[field.key]" :min="0" :precision="2" :controls="false" style="width: 100%" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="frontVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitFront">保存首页</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'
import { auth } from '../../store/auth'

const BORROW_STATUS = {
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  RETURNED: { label: '已归还', type: 'info' }
}
const COPY_STATUS = {
  PENDING: { label: '待审批', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  COMPLETED: { label: '已完成', type: 'info' }
}
const DISCHARGE_WAYS = ['医嘱离院', '医嘱转院', '医嘱转社区', '非医嘱离院', '死亡', '其他']
const COST_FIELDS = [
  { key: 'total', label: '合计' },
  { key: 'drug', label: '药品费' },
  { key: 'material', label: '材料费' },
  { key: 'bed', label: '床位费' },
  { key: 'exam', label: '检查费' },
  { key: 'treatment', label: '治疗费' },
  { key: 'nursing', label: '护理费' },
  { key: 'other', label: '其他' }
]

const tab = ref('borrow')
const submitting = ref(false)

const borrows = ref([])
const borrowLoading = ref(false)
const borrowFilter = ref('')
const borrowCreateVisible = ref(false)
const borrowForm = reactive({ docId: '', reason: '', days: 7 })

const copies = ref([])
const copyLoading = ref(false)
const copyFilter = ref('')
const copyCreateVisible = ref(false)
const copyForm = reactive({ docId: '', purpose: '', copies: 1 })

const decisionVisible = ref(false)
const decisionMode = ref('approve')
const decisionNote = ref('')
const decisionTarget = ref(null)
const decisionKind = ref('borrow')

const frontPages = ref([])
const frontLoading = ref(false)
const frontFilters = reactive({ patientId: '', visitId: '' })
const frontVisible = ref(false)
const frontForm = ref(emptyFrontForm())
const diagnosisPick = ref('')
const surgeryPick = ref('')
const diagnosisOptions = ref([])
const surgeryOptions = ref([])
const icdLoading = ref(false)

function emptyFrontForm() {
  return {
    id: null,
    patientId: '1001',
    visitId: '',
    docId: '',
    inpatientNo: '',
    admissionDate: '',
    dischargeDate: '',
    admissionDept: '',
    dischargeDept: '',
    dischargeWay: '医嘱离院',
    anesthesiaType: '',
    attendingDoctor: '',
    diagnoses: [],
    surgeries: [],
    cost: { total: 0, drug: 0, material: 0, bed: 0, exam: 0, treatment: 0, nursing: 0, other: 0 }
  }
}

function statusLabel(status, kind) {
  const dict = kind === 'borrow' ? BORROW_STATUS : COPY_STATUS
  return dict[status]?.label || status
}
function statusType(status, kind) {
  const dict = kind === 'borrow' ? BORROW_STATUS : COPY_STATUS
  return dict[status]?.type || 'info'
}
function fmt(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadBorrows() {
  borrowLoading.value = true
  try {
    borrows.value = await http.get('/emr/borrow-requests', { params: { status: borrowFilter.value || undefined, limit: 100 } })
  } catch {
    borrows.value = []
  } finally {
    borrowLoading.value = false
  }
}

async function loadCopies() {
  copyLoading.value = true
  try {
    copies.value = await http.get('/emr/copy-requests', { params: { status: copyFilter.value || undefined, limit: 100 } })
  } catch {
    copies.value = []
  } finally {
    copyLoading.value = false
  }
}

function openBorrowCreate() {
  borrowForm.docId = ''
  borrowForm.reason = ''
  borrowForm.days = 7
  borrowCreateVisible.value = true
}

async function submitBorrow() {
  if (!borrowForm.docId || !borrowForm.reason) {
    ElMessage.warning('请填写病历 ID 与借阅理由')
    return
  }
  submitting.value = true
  try {
    await http.post('/emr/borrow-requests', { docId: Number(borrowForm.docId), reason: borrowForm.reason, days: borrowForm.days })
    ElMessage.success('借阅申请已提交')
    borrowCreateVisible.value = false
    await loadBorrows()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function openCopyCreate() {
  copyForm.docId = ''
  copyForm.purpose = ''
  copyForm.copies = 1
  copyCreateVisible.value = true
}

async function submitCopy() {
  if (!copyForm.docId || !copyForm.purpose) {
    ElMessage.warning('请填写病历 ID 与复印用途')
    return
  }
  submitting.value = true
  try {
    await http.post('/emr/copy-requests', { docId: Number(copyForm.docId), purpose: copyForm.purpose, copies: copyForm.copies })
    ElMessage.success('复印申请已提交')
    copyCreateVisible.value = false
    await loadCopies()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function decideBorrow(row, mode) {
  decisionKind.value = 'borrow'
  decisionMode.value = mode
  decisionTarget.value = row
  decisionNote.value = ''
  decisionTitle.value = `${mode === 'approve' ? '通过' : '驳回'}借阅申请`
  decisionVisible.value = true
}
function decideCopy(row, mode) {
  decisionKind.value = 'copy'
  decisionMode.value = mode
  decisionTarget.value = row
  decisionNote.value = ''
  decisionTitle.value = `${mode === 'approve' ? '通过' : '驳回'}复印申请`
  decisionVisible.value = true
}
const decisionTitle = ref('审批')

async function submitDecision() {
  if (decisionMode.value === 'reject' && !decisionNote.value.trim()) {
    ElMessage.warning('驳回必须填写原因')
    return
  }
  submitting.value = true
  try {
    const base = decisionKind.value === 'borrow' ? 'borrow-requests' : 'copy-requests'
    const action = decisionMode.value === 'approve' ? 'approve' : 'reject'
    const body = decisionMode.value === 'approve' ? { note: decisionNote.value || null } : { note: decisionNote.value }
    await http.post(`/emr/${base}/${decisionTarget.value.id}/${action}`, body)
    ElMessage.success('审批完成')
    decisionVisible.value = false
    if (decisionKind.value === 'borrow') await loadBorrows()
    else await loadCopies()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function returnBorrow(row) {
  try {
    await ElMessageBox.confirm('确认病案已归还？', '归还确认', { type: 'warning' })
    await http.post(`/emr/borrow-requests/${row.id}/return`)
    ElMessage.success('已归还')
    await loadBorrows()
  } catch {
    /* 取消或失败 */
  }
}

async function completeCopy(row) {
  try {
    await ElMessageBox.confirm('确认复印已完成？', '完成复印', { type: 'warning' })
    await http.post(`/emr/copy-requests/${row.id}/complete`)
    ElMessage.success('复印已完成')
    await loadCopies()
  } catch {
    /* 取消或失败 */
  }
}

async function loadFrontPages() {
  frontLoading.value = true
  try {
    frontPages.value = await http.get('/emr/front-pages', {
      params: {
        patientId: frontFilters.patientId || undefined,
        visitId: frontFilters.visitId || undefined,
        limit: 100
      }
    })
  } catch {
    frontPages.value = []
  } finally {
    frontLoading.value = false
  }
}

function openFrontCreate() {
  frontForm.value = emptyFrontForm()
  diagnosisOptions.value = []
  surgeryOptions.value = []
  frontVisible.value = true
}

function openFrontEdit(row) {
  frontForm.value = {
    id: row.id,
    patientId: String(row.patientId),
    visitId: String(row.visitId),
    docId: row.docId ? String(row.docId) : '',
    inpatientNo: row.inpatientNo || '',
    admissionDate: row.admissionDate || '',
    dischargeDate: row.dischargeDate || '',
    admissionDept: row.admissionDept || '',
    dischargeDept: row.dischargeDept || '',
    dischargeWay: row.dischargeWay || '医嘱离院',
    anesthesiaType: row.anesthesiaType || '',
    attendingDoctor: row.attendingDoctor || '',
    diagnoses: (row.diagnoses || []).map((item) => ({ ...item })),
    surgeries: (row.surgeries || []).map((item) => ({ ...item })),
    cost: { total: 0, drug: 0, material: 0, bed: 0, exam: 0, treatment: 0, nursing: 0, other: 0, ...(row.cost || {}) }
  }
  frontVisible.value = true
}

async function searchIcd(keyword, type, target) {
  if (!keyword) return
  icdLoading.value = true
  try {
    const list = await http.get('/emr/icd/codes', { params: { keyword, type, limit: 20 } })
    if (target === 'diagnosis') diagnosisOptions.value = list
    else surgeryOptions.value = list
  } catch {
    /* 拦截器已提示 */
  } finally {
    icdLoading.value = false
  }
}

function addDiagnosis(code) {
  const option = diagnosisOptions.value.find((item) => item.code === code)
  if (!option) return
  const hasMain = frontForm.value.diagnoses.some((item) => item.main)
  frontForm.value.diagnoses.push({ code: option.code, name: option.name, main: !hasMain })
  diagnosisPick.value = ''
}

function setMainDiagnosis(index, value) {
  if (value) {
    frontForm.value.diagnoses.forEach((item, i) => {
      if (i !== index) item.main = false
    })
  }
}

function addSurgery(code) {
  const option = surgeryOptions.value.find((item) => item.code === code)
  if (!option) return
  frontForm.value.surgeries.push({ code: option.code, name: option.name, surgeryDate: null })
  surgeryPick.value = ''
}

async function submitFront() {
  const form = frontForm.value
  if (!form.patientId || !form.visitId) {
    ElMessage.warning('请填写患者 ID 与就诊 ID')
    return
  }
  if (!form.diagnoses.length) {
    ElMessage.warning('至少填写一条诊断')
    return
  }
  if (!form.dischargeWay) {
    ElMessage.warning('请选择离院方式')
    return
  }
  if (form.diagnoses.filter((item) => item.main).length > 1) {
    ElMessage.warning('主诊断只能有一条')
    return
  }
  submitting.value = true
  try {
    await http.post('/emr/front-pages', {
      patientId: Number(form.patientId),
      visitId: Number(form.visitId),
      docId: form.docId ? Number(form.docId) : null,
      inpatientNo: form.inpatientNo || null,
      admissionDate: form.admissionDate || null,
      dischargeDate: form.dischargeDate || null,
      admissionDept: form.admissionDept || null,
      dischargeDept: form.dischargeDept || null,
      dischargeWay: form.dischargeWay,
      anesthesiaType: form.anesthesiaType || null,
      attendingDoctor: form.attendingDoctor || null,
      diagnoses: form.diagnoses,
      surgeries: form.surgeries,
      cost: form.cost
    })
    ElMessage.success('病案首页已保存')
    frontVisible.value = false
    await loadFrontPages()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function verifyFront(row) {
  try {
    const result = await http.post(`/emr/front-pages/${row.id}/verify`)
    if (result.verified) {
      ElMessage.success('编码核对通过，已置为已核对')
    } else {
      const issues = (result.issues || []).map((item) => `${item.item}：${item.message}`).join('；')
      ElMessageBox.alert(issues || '编码核对未通过', '编码核对问题', { type: 'warning' })
    }
    await loadFrontPages()
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(() => {
  loadBorrows()
  loadCopies()
  loadFrontPages()
})
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 18px;
}

.cost-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.icd-picker {
  margin-bottom: 10px;
}

.row-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px dashed #dbe7e8;
  background: #fafdfd;
  max-height: 180px;
  overflow-y: auto;
}

.row-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  padding: 4px 0;
  border-bottom: 1px dashed #edf3f3;
}

.row-item:last-child {
  border-bottom: none;
}

.code {
  color: #0f766e;
  font-weight: 700;
  width: 120px;
}

.row-name {
  flex: 1;
}

@media (max-width: 1000px) {
  .form-grid,
  .cost-grid {
    grid-template-columns: 1fr;
  }
}
</style>
