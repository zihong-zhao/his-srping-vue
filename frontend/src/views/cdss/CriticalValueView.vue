<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">危急值</h1>
        <p class="page-sub">上报、医生确认、处理措施记录与关闭，形成危急值闭环。</p>
      </div>
      <el-button type="primary" @click="openReport">上报危急值</el-button>
    </div>

    <div class="stat-grid">
      <div class="stat-card" style="--his-primary-soft: #fff7e6">
        <div class="stat-label">待确认</div>
        <div class="stat-value">{{ counts.PENDING }}</div>
        <div class="stat-note">等待医生确认收到</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #eef2ff">
        <div class="stat-label">已确认</div>
        <div class="stat-value">{{ counts.CONFIRMED }}</div>
        <div class="stat-note">等待记录处理措施</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #f0fdf4">
        <div class="stat-label">已关闭</div>
        <div class="stat-value">{{ counts.CLOSED }}</div>
        <div class="stat-note">当前筛选范围内</div>
      </div>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar">
        <el-input v-model="filters.patientId" placeholder="患者 ID" clearable style="width: 140px" @keyup.enter="load" />
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option label="待确认" value="PENDING" />
          <el-option label="已确认" value="CONFIRMED" />
          <el-option label="已关闭" value="CLOSED" />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="load">查询</el-button>
        <span class="spacer" />
        <el-button @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="items" stripe empty-text="暂无危急值">
        <el-table-column prop="patientId" label="患者" width="90" />
        <el-table-column prop="visitId" label="就诊" width="90">
          <template #default="{ row }">{{ row.visitId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="itemName" label="项目" width="130" />
        <el-table-column label="结果" width="150">
          <template #default="{ row }">
            <span class="value-strong">{{ row.resultValue }}</span>
            <span class="text-muted" style="margin-left: 4px">{{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="referenceRange" label="参考范围" width="130" />
        <el-table-column label="级别" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.severity === 'HIGH' ? 'danger' : 'warning'">
              {{ row.severity === 'HIGH' ? '偏高' : '偏低' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS[row.status]?.type">{{ STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reportedByName" label="上报人" width="100" />
        <el-table-column label="上报时间" width="160">
          <template #default="{ row }">{{ fmt(row.reportedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PENDING'" link type="success" @click="confirm(row)">确认</el-button>
            <el-button v-if="row.status === 'CONFIRMED'" link type="primary" @click="openClose(row)">处理并关闭</el-button>
            <el-button link type="info" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="reportVisible" title="上报危急值" width="640px">
      <el-form label-width="92px">
        <div class="form-grid">
          <el-form-item label="患者 ID" required>
            <el-input v-model="reportForm.patientId" />
          </el-form-item>
          <el-form-item label="就诊 ID">
            <el-input v-model="reportForm.visitId" />
          </el-form-item>
        </div>
        <el-form-item label="检验项目">
          <el-select v-model="reportForm.itemCode" filterable clearable placeholder="选择知识库项目可自动带出单位与参考范围" style="width: 100%" @change="applyExamItem">
            <el-option
              v-for="item in labItems"
              :key="item.itemCode"
              :label="`${item.itemName} · ${item.itemCode}`"
              :value="item.itemCode"
            />
          </el-select>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="项目名称" required>
            <el-input v-model="reportForm.itemName" />
          </el-form-item>
          <el-form-item label="结果值" required>
            <el-input v-model="reportForm.resultValue" placeholder="例如 6.8">
              <template #append>
                <el-button :loading="checking" @click="judgeSeverity">按阈值判定</el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="单位">
            <el-input v-model="reportForm.unit" />
          </el-form-item>
          <el-form-item label="参考范围">
            <el-input v-model="reportForm.referenceRange" />
          </el-form-item>
          <el-form-item label="级别" required>
            <el-select v-model="reportForm.severity" style="width: 100%">
              <el-option label="偏高（HIGH）" value="HIGH" />
              <el-option label="偏低（LOW）" value="LOW" />
            </el-select>
          </el-form-item>
        </div>
        <el-alert
          v-if="judgeMessage"
          :title="judgeMessage"
          :type="judgeSeverityType"
          :closable="false"
          show-icon
          style="margin-top: 4px"
        />
      </el-form>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReport">上报</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="closeVisible" title="记录处理措施并关闭" width="520px">
      <el-descriptions :column="1" size="small" border style="margin-bottom: 14px">
        <el-descriptions-item label="项目">{{ closeTarget?.itemName }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ closeTarget?.resultValue }} {{ closeTarget?.unit }}</el-descriptions-item>
        <el-descriptions-item label="参考范围">{{ closeTarget?.referenceRange }}</el-descriptions-item>
      </el-descriptions>
      <el-input v-model="handlingNote" type="textarea" :rows="4" placeholder="请填写处理措施与结果，例如：立即复查血钾，予降钾处理" />
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitClose">关闭危急值</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="危急值详情" size="520px">
      <template v-if="detail">
        <div class="detail-grid">
          <div class="kv"><span>患者</span><b>{{ detail.patientId }}</b></div>
          <div class="kv"><span>就诊</span><b>{{ detail.visitId || '-' }}</b></div>
          <div class="kv"><span>项目</span><b>{{ detail.itemName }}（{{ detail.itemCode }}）</b></div>
          <div class="kv"><span>结果</span><b>{{ detail.resultValue }} {{ detail.unit }}</b></div>
          <div class="kv"><span>参考范围</span><b>{{ detail.referenceRange || '-' }}</b></div>
          <div class="kv"><span>级别</span><b>{{ detail.severity === 'HIGH' ? '偏高' : '偏低' }}</b></div>
          <div class="kv"><span>状态</span><b>{{ STATUS[detail.status]?.label }}</b></div>
          <div class="kv"><span>上报</span><b>{{ detail.reportedByName }} · {{ fmt(detail.reportedAt) }}</b></div>
          <div class="kv"><span>确认</span><b>{{ detail.confirmedByName || '-' }} · {{ fmt(detail.confirmedAt) }}</b></div>
          <div class="kv"><span>关闭</span><b>{{ fmt(detail.closedAt) }}</b></div>
        </div>
        <div class="section-title" style="margin-top: 18px"><span>处理措施</span></div>
        <div class="note-box">{{ detail.handlingNote || '尚未记录处理措施' }}</div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../../api/http'

const STATUS = {
  PENDING: { label: '待确认', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'primary' },
  CLOSED: { label: '已关闭', type: 'success' }
}

const filters = reactive({ patientId: '', status: '' })
const items = ref([])
const loading = ref(false)
const labItems = ref([])

const reportVisible = ref(false)
const reportForm = reactive({
  patientId: '1001',
  visitId: '',
  itemCode: '',
  itemName: '',
  resultValue: '',
  unit: '',
  referenceRange: '',
  severity: 'HIGH'
})
const judgeMessage = ref('')
const judgeSeverityType = ref('info')
const checking = ref(false)
const submitting = ref(false)

const closeVisible = ref(false)
const closeTarget = ref(null)
const handlingNote = ref('')

const detailVisible = ref(false)
const detail = ref(null)

const counts = computed(() => {
  const result = { PENDING: 0, CONFIRMED: 0, CLOSED: 0 }
  items.value.forEach((item) => {
    result[item.status] = (result[item.status] || 0) + 1
  })
  return result
})

function fmt(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function load() {
  loading.value = true
  try {
    items.value = await http.get('/e4/critical-values', {
      params: { patientId: filters.patientId || undefined, status: filters.status || undefined, limit: 100 }
    })
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function loadLabItems() {
  try {
    labItems.value = await http.get('/e4/exam-items', { params: { itemType: 'LAB', limit: 200 } })
  } catch {
    labItems.value = []
  }
}

function openReport() {
  Object.assign(reportForm, {
    patientId: filters.patientId || '1001',
    visitId: '',
    itemCode: '',
    itemName: '',
    resultValue: '',
    unit: '',
    referenceRange: '',
    severity: 'HIGH'
  })
  judgeMessage.value = ''
  reportVisible.value = true
}

function applyExamItem(code) {
  const item = labItems.value.find((entry) => entry.itemCode === code)
  if (!item) return
  reportForm.itemName = item.itemName
  reportForm.unit = item.unit || ''
  reportForm.referenceRange = item.referenceRange || ''
  judgeMessage.value = ''
}

async function judgeSeverity() {
  if (!reportForm.itemCode || !reportForm.resultValue) {
    ElMessage.warning('请先选择项目并填写结果值')
    return
  }
  const value = Number(reportForm.resultValue)
  if (Number.isNaN(value)) {
    ElMessage.warning('结果值必须为数字')
    return
  }
  checking.value = true
  try {
    const result = await http.post('/e4/exam-items/critical-check', {
      itemCode: reportForm.itemCode,
      value
    })
    judgeMessage.value = result.message
    if (result.severity === 'HIGH') {
      reportForm.severity = 'HIGH'
      judgeSeverityType.value = 'error'
    } else if (result.severity === 'LOW') {
      reportForm.severity = 'LOW'
      judgeSeverityType.value = 'warning'
    } else {
      judgeSeverityType.value = 'success'
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    checking.value = false
  }
}

async function submitReport() {
  if (!reportForm.patientId || !reportForm.itemCode || !reportForm.itemName || !reportForm.resultValue) {
    ElMessage.warning('请填写患者、项目与结果值')
    return
  }
  submitting.value = true
  try {
    await http.post('/e4/critical-values', {
      patientId: Number(reportForm.patientId),
      visitId: reportForm.visitId ? Number(reportForm.visitId) : null,
      itemCode: reportForm.itemCode,
      itemName: reportForm.itemName,
      resultValue: reportForm.resultValue,
      unit: reportForm.unit || null,
      referenceRange: reportForm.referenceRange || null,
      severity: reportForm.severity
    })
    ElMessage.success('危急值已上报')
    reportVisible.value = false
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function confirm(row) {
  try {
    await ElMessageBox.confirm('确认已收到该危急值？', '确认危急值', { type: 'warning' })
    await http.post(`/e4/critical-values/${row.id}/confirm`)
    ElMessage.success('已确认')
    await load()
  } catch {
    /* 取消或失败 */
  }
}

function openClose(row) {
  closeTarget.value = row
  handlingNote.value = ''
  closeVisible.value = true
}

async function submitClose() {
  if (!handlingNote.value.trim()) {
    ElMessage.warning('请填写处理措施')
    return
  }
  submitting.value = true
  try {
    await http.post(`/e4/critical-values/${closeTarget.value.id}/close`, { handlingNote: handlingNote.value })
    ElMessage.success('危急值已关闭')
    closeVisible.value = false
    await load()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function openDetail(row) {
  try {
    detail.value = await http.get(`/e4/critical-values/${row.id}`)
    detailVisible.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

onMounted(() => {
  load()
  loadLabItems()
})
</script>

<style scoped>
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.value-strong {
  font-weight: 700;
  color: #b91c1c;
  font-size: 14px;
}

.detail-grid {
  display: flex;
  flex-direction: column;
}

.kv {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 0;
  font-size: 12px;
  border-bottom: 1px dashed #edf3f3;
}

.kv span {
  color: var(--his-muted);
}

.note-box {
  padding: 12px 14px;
  border-radius: 10px;
  background: #fbfdfd;
  border: 1px solid var(--his-line);
  font-size: 12px;
  line-height: 1.7;
  min-height: 60px;
}
</style>
