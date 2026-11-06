<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">合理用药</h1>
        <p class="page-sub">药品知识库、开立前处方审查与提醒留痕。</p>
      </div>
    </div>

    <el-tabs v-model="tab" class="panel panel-pad">
      <el-tab-pane label="药品知识库" name="drugs">
        <div class="toolbar">
          <el-input v-model="drugKeyword" placeholder="药品名称或编码" clearable style="width: 240px" @keyup.enter="searchDrugs" />
          <el-button type="primary" plain :loading="drugLoading" @click="searchDrugs">检索</el-button>
          <span class="spacer" />
          <span class="text-muted" style="font-size: 12px">共 {{ drugs.length }} 条</span>
        </div>
        <el-table v-loading="drugLoading" :data="drugs" stripe empty-text="暂无药品">
          <el-table-column prop="drugCode" label="编码" width="140">
            <template #default="{ row }"><span class="mono">{{ row.drugCode }}</span></template>
          </el-table-column>
          <el-table-column prop="drugName" label="药品名称" min-width="160" />
          <el-table-column prop="category" label="类别" width="110" />
          <el-table-column label="适应症" min-width="180">
            <template #default="{ row }">
              <el-tag v-for="item in row.indications" :key="item" size="small" type="success" effect="plain" style="margin: 2px 4px 2px 0">
                {{ item }}
              </el-tag>
              <span v-if="!row.indications?.length" class="text-muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="adultDose" label="成人剂量" min-width="130" show-overflow-tooltip />
          <el-table-column label="日最大量" width="110">
            <template #default="{ row }">{{ row.maxDailyDoseMg ? `${row.maxDailyDoseMg} mg` : '-' }}</template>
          </el-table-column>
          <el-table-column prop="pregnancyNote" label="妊娠提示" min-width="150" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="处方审查" name="review">
        <div class="review-grid">
          <div>
            <div class="section-title"><span>患者信息</span></div>
            <el-form label-width="82px" size="default">
              <div class="form-grid">
                <el-form-item label="患者 ID" required>
                  <el-input v-model="reviewForm.patientId" />
                </el-form-item>
                <el-form-item label="就诊 ID">
                  <el-input v-model="reviewForm.visitId" />
                </el-form-item>
                <el-form-item label="年龄">
                  <el-input-number v-model="reviewForm.ageYears" :min="0" :max="150" :controls="false" style="width: 100%" />
                </el-form-item>
                <el-form-item label="体重 kg">
                  <el-input-number v-model="reviewForm.weightKg" :min="0" :precision="1" :controls="false" style="width: 100%" />
                </el-form-item>
                <el-form-item label="eGFR">
                  <el-input-number v-model="reviewForm.egfr" :min="0" :precision="1" :controls="false" style="width: 100%" />
                </el-form-item>
                <el-form-item label="妊娠">
                  <el-switch v-model="reviewForm.pregnant" />
                </el-form-item>
              </div>
              <el-form-item label="诊断编码">
                <el-input v-model="reviewForm.diagnosisCodes" placeholder="多个 ICD 编码用英文逗号分隔，例如 I10,R50.9" />
              </el-form-item>
              <el-form-item label="业务号">
                <el-input v-model="reviewForm.bizNo" placeholder="可选，例如 RX20261107001" />
              </el-form-item>
              <el-form-item label="覆盖理由">
                <el-input v-model="reviewForm.overrideReason" type="textarea" :rows="2" placeholder="如需覆盖提醒，请填写临床理由" />
              </el-form-item>
            </el-form>

            <div class="section-title" style="margin-top: 10px">
              <span>处方药品</span>
              <el-button link type="primary" @click="addDrug">添加药品</el-button>
            </div>
            <div class="drug-rows">
              <div v-for="(row, index) in reviewForm.drugs" :key="index" class="drug-row">
                <el-select
                  v-model="row.drugCode"
                  filterable
                  placeholder="选择药品"
                  style="flex: 1; min-width: 180px"
                >
                  <el-option
                    v-for="drug in drugs"
                    :key="drug.drugCode"
                    :label="`${drug.drugName} · ${drug.drugCode}`"
                    :value="drug.drugCode"
                  />
                </el-select>
                <el-input-number v-model="row.dose" :min="0" :precision="2" :controls="false" placeholder="剂量" style="width: 100px" />
                <el-input v-model="row.unit" placeholder="单位" style="width: 82px" />
                <el-input v-model="row.frequency" placeholder="频次" style="width: 92px" />
                <el-button link type="danger" @click="reviewForm.drugs.splice(index, 1)">删除</el-button>
              </div>
            </div>
            <el-button type="primary" style="margin-top: 14px" :loading="reviewing" @click="submitReview">
              开始审查
            </el-button>
          </div>

          <div class="result-col">
            <div v-if="!reviewResult" class="empty-block panel" style="padding: 40px 18px">
              <el-empty description="填写处方后开始审查" :image-size="80" />
            </div>
            <template v-else>
              <div class="decision-card" :class="decisionClass(reviewResult.decision)">
                <div class="decision-top">
                  <div>
                    <div class="decision-label">审查结论</div>
                    <div class="decision-value">{{ DECISION[reviewResult.decision]?.label || reviewResult.decision }}</div>
                  </div>
                  <el-tag :type="DECISION[reviewResult.decision]?.type || 'info'" effect="dark">
                    {{ reviewResult.blocked ? '已阻断' : reviewResult.requiresOverride ? '需理由' : '可执行' }}
                  </el-tag>
                </div>
                <div class="decision-biz mono">{{ reviewResult.bizNo }}</div>
              </div>

              <div class="panel panel-pad" style="margin-top: 12px">
                <div class="section-title"><span>提醒明细</span></div>
                <div v-if="reviewResult.alerts?.length" class="alert-list">
                  <div v-for="alert in reviewResult.alerts" :key="alert.id" class="alert-item">
                    <div class="alert-head">
                      <el-tag size="small" :type="severityType(alert.severity)">{{ alert.severity }}</el-tag>
                      <span class="alert-rule mono">{{ alert.ruleCode }}</span>
                      <span class="spacer" />
                      <el-button
                        v-if="alert.decision !== 'IGNORED' && alert.decision !== 'OVERRIDDEN'"
                        link
                        type="warning"
                        @click="openIgnore(alert)"
                      >
                        忽略并留痕
                      </el-button>
                    </div>
                    <div class="alert-msg">{{ alert.message }}</div>
                    <div class="alert-meta">处置：{{ DECISION[alert.decision]?.label || alert.decision }}</div>
                  </div>
                </div>
                <el-empty v-else description="未触发用药提醒" :image-size="70" />
              </div>
            </template>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="提醒记录" name="alerts">
        <div class="toolbar">
          <el-input v-model="alertFilters.patientId" placeholder="患者 ID" clearable style="width: 140px" @keyup.enter="loadAlerts" />
          <el-input v-model="alertFilters.bizNo" placeholder="业务号" clearable style="width: 180px" @keyup.enter="loadAlerts" />
          <el-button type="primary" plain :loading="alertLoading" @click="loadAlerts">查询</el-button>
        </div>
        <el-table v-loading="alertLoading" :data="alerts" stripe empty-text="暂无提醒记录">
          <el-table-column prop="ruleCode" label="规则" width="190">
            <template #default="{ row }"><span class="mono">{{ row.ruleCode }}</span></template>
          </el-table-column>
          <el-table-column label="级别" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="severityType(row.severity)">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="message" label="提醒内容" min-width="260" show-overflow-tooltip />
          <el-table-column label="处置" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="DECISION[row.decision]?.type || 'info'">{{ DECISION[row.decision]?.label || row.decision }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="overrideReason" label="理由" min-width="160" show-overflow-tooltip />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.decision !== 'IGNORED'" link type="warning" @click="openIgnore(row)">忽略</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="ignoreVisible" title="忽略提醒" width="480px">
      <el-alert :title="ignoreTarget?.message" type="warning" :closable="false" show-icon style="margin-bottom: 12px" />
      <el-input v-model="ignoreReason" type="textarea" :rows="3" placeholder="请填写忽略理由，将写入审计日志" />
      <template #footer>
        <el-button @click="ignoreVisible = false">取消</el-button>
        <el-button type="warning" :loading="ignoring" @click="submitIgnore">确认忽略</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'

const DECISION = {
  PASSED: { label: '审查通过', type: 'success' },
  BLOCKED: { label: '禁止执行', type: 'danger' },
  PENDING: { label: '待覆盖理由', type: 'warning' },
  OVERRIDDEN: { label: '已覆盖', type: 'warning' },
  IGNORED: { label: '已忽略', type: 'info' }
}

const tab = ref('drugs')
const drugs = ref([])
const drugLoading = ref(false)
const drugKeyword = ref('')

const reviewForm = reactive({
  patientId: '1001',
  visitId: '',
  ageYears: 56,
  weightKg: 65,
  egfr: 90,
  pregnant: false,
  diagnosisCodes: 'I10',
  bizNo: '',
  overrideReason: '',
  drugs: [{ drugCode: '', dose: null, unit: 'mg', frequency: 'tid' }]
})
const reviewing = ref(false)
const reviewResult = ref(null)

const alerts = ref([])
const alertLoading = ref(false)
const alertFilters = reactive({ patientId: '', bizNo: '' })

const ignoreVisible = ref(false)
const ignoreTarget = ref(null)
const ignoreReason = ref('')
const ignoring = ref(false)

function severityType(severity) {
  if (severity === 'BLOCK') return 'danger'
  if (severity === 'WARN') return 'warning'
  return 'info'
}
function decisionClass(decision) {
  if (decision === 'BLOCKED') return 'danger'
  if (decision === 'PASSED') return 'success'
  if (decision === 'PENDING' || decision === 'OVERRIDDEN') return 'warning'
  return 'info'
}
function fmt(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function searchDrugs() {
  drugLoading.value = true
  try {
    drugs.value = await http.get('/e4/drugs', { params: { keyword: drugKeyword.value || undefined, limit: 200 } })
  } catch {
    drugs.value = []
  } finally {
    drugLoading.value = false
  }
}

function addDrug() {
  reviewForm.drugs.push({ drugCode: '', dose: null, unit: 'mg', frequency: 'tid' })
}

async function submitReview() {
  if (!reviewForm.patientId || !reviewForm.drugs.length || !reviewForm.drugs[0].drugCode) {
    ElMessage.warning('请填写患者 ID 并至少选择一味药品')
    return
  }
  if (reviewForm.drugs.some((item) => !item.drugCode)) {
    ElMessage.warning('存在未选择药品的处方行')
    return
  }
  reviewing.value = true
  try {
    reviewResult.value = await http.post('/e4/prescriptions/review', {
      patientId: Number(reviewForm.patientId),
      visitId: reviewForm.visitId ? Number(reviewForm.visitId) : null,
      ageYears: reviewForm.ageYears ?? null,
      weightKg: reviewForm.weightKg ?? null,
      egfr: reviewForm.egfr ?? null,
      pregnant: reviewForm.pregnant,
      diagnosisCodes: reviewForm.diagnosisCodes
        ? reviewForm.diagnosisCodes.split(',').map((item) => item.trim()).filter(Boolean)
        : [],
      drugs: reviewForm.drugs.map((item) => ({
        drugCode: item.drugCode,
        dose: item.dose ?? null,
        unit: item.unit || null,
        frequency: item.frequency || null
      })),
      bizNo: reviewForm.bizNo || null,
      overrideReason: reviewForm.overrideReason || null
    })
    ElMessage.success(`审查完成：${DECISION[reviewResult.value.decision]?.label || reviewResult.value.decision}`)
  } catch {
    /* 拦截器已提示 */
  } finally {
    reviewing.value = false
  }
}

async function loadAlerts() {
  alertLoading.value = true
  try {
    alerts.value = await http.get('/e4/alerts', {
      params: {
        patientId: alertFilters.patientId || undefined,
        bizNo: alertFilters.bizNo || undefined,
        limit: 100
      }
    })
  } catch {
    alerts.value = []
  } finally {
    alertLoading.value = false
  }
}

function openIgnore(alert) {
  ignoreTarget.value = alert
  ignoreReason.value = ''
  ignoreVisible.value = true
}

async function submitIgnore() {
  if (!ignoreReason.value.trim()) {
    ElMessage.warning('请填写忽略理由')
    return
  }
  ignoring.value = true
  try {
    await http.post(`/e4/alerts/${ignoreTarget.value.id}/ignore`, { reason: ignoreReason.value })
    ElMessage.success('已忽略并留痕')
    ignoreVisible.value = false
    await loadAlerts()
    if (reviewResult.value?.alerts?.length) {
      const target = reviewResult.value.alerts.find((item) => item.id === ignoreTarget.value.id)
      if (target) target.decision = 'IGNORED'
    }
  } catch {
    /* 拦截器已提示 */
  } finally {
    ignoring.value = false
  }
}

onMounted(() => {
  searchDrugs()
  loadAlerts()
})
</script>

<style scoped>
.review-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: 20px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.drug-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px dashed #dbe7e8;
  background: #fafdfd;
}

.drug-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.result-col {
  border-left: 1px dashed var(--his-line);
  padding-left: 20px;
}

.decision-card {
  border-radius: 14px;
  padding: 18px 20px;
  color: #ffffff;
}

.decision-card.success {
  background: linear-gradient(135deg, #0f766e, #15803d);
}
.decision-card.danger {
  background: linear-gradient(135deg, #b91c1c, #991b1b);
}
.decision-card.warning {
  background: linear-gradient(135deg, #b45309, #d97706);
}
.decision-card.info {
  background: linear-gradient(135deg, #0e7490, #0369a1);
}

.decision-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.decision-label {
  font-size: 11px;
  opacity: 0.8;
  letter-spacing: 1px;
}

.decision-value {
  font-size: 22px;
  font-weight: 700;
  margin-top: 6px;
}

.decision-biz {
  margin-top: 12px;
  font-size: 11px;
  opacity: 0.85;
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.alert-item {
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--his-line);
  background: #fbfdfd;
}

.alert-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.alert-rule {
  color: var(--his-muted);
  font-size: 11px;
}

.alert-msg {
  font-size: 13px;
  margin-top: 8px;
  line-height: 1.6;
}

.alert-meta {
  font-size: 11px;
  color: var(--his-muted);
  margin-top: 6px;
}

@media (max-width: 1100px) {
  .review-grid {
    grid-template-columns: 1fr;
  }
  .result-col {
    border-left: none;
    padding-left: 0;
  }
}
</style>
