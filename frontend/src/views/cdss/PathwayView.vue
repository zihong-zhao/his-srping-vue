<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">临床路径</h1>
        <p class="page-sub">路径模板维护、患者入径、节点按序执行、变异记录与退出，统计路径执行率。</p>
      </div>
      <el-button type="primary" :disabled="!templates.length" @click="openEnroll(null)">患者入径</el-button>
    </div>

    <div class="stat-grid">
      <div class="stat-card" style="--his-primary-soft: #e6fffa">
        <div class="stat-label">路径模板</div>
        <div class="stat-value">{{ templates.length }}</div>
        <div class="stat-note">可入径标准路径</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #eef6ff">
        <div class="stat-label">在径实例</div>
        <div class="stat-value">{{ instanceCounts.ACTIVE }}</div>
        <div class="stat-note">当前执行中</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #f0fdf4">
        <div class="stat-label">已完成</div>
        <div class="stat-value">{{ instanceCounts.COMPLETED }}</div>
        <div class="stat-note">按路径完成诊疗</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #fff7e6">
        <div class="stat-label">执行率</div>
        <div class="stat-value">{{ aggregateRate }}<span class="rate-unit">%</span></div>
        <div class="stat-note">{{ statsWindow }} 完成 / 入径</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="pathway-tabs">
      <el-tab-pane label="路径模板" name="templates">
        <div class="panel panel-pad">
          <el-table v-loading="loadingTemplates" :data="templates" stripe empty-text="暂无路径模板">
            <el-table-column prop="pathwayCode" label="路径编码" width="130">
              <template #default="{ row }"><span class="mono">{{ row.pathwayCode }}</span></template>
            </el-table-column>
            <el-table-column prop="name" label="路径名称" min-width="160" />
            <el-table-column prop="diagnosisCode" label="主要诊断" width="110" />
            <el-table-column prop="department" label="科室" width="110" />
            <el-table-column prop="version" label="版本" width="70">
              <template #default="{ row }">v{{ row.version }}</template>
            </el-table-column>
            <el-table-column prop="standardDays" label="标准住院日" width="110">
              <template #default="{ row }">{{ row.standardDays }} 天</template>
            </el-table-column>
            <el-table-column label="节点" width="80">
              <template #default="{ row }">{{ (row.nodes || []).length }}</template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTemplate(row)">详情</el-button>
                <el-button link type="success" @click="openEnroll(row)">入径</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="入径实例" name="instances">
        <div class="panel panel-pad">
          <div class="toolbar">
            <el-input v-model="filters.patientId" placeholder="患者 ID" clearable style="width: 130px" @keyup.enter="loadInstances" />
            <el-select v-model="filters.pathwayId" placeholder="全部路径" clearable style="width: 200px">
              <el-option v-for="item in templates" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 130px">
              <el-option label="在径" value="ACTIVE" />
              <el-option label="完成" value="COMPLETED" />
              <el-option label="退出" value="EXITED" />
            </el-select>
            <el-button type="primary" plain :loading="loadingInstances" @click="loadInstances">查询</el-button>
            <el-button @click="resetInstanceFilters">重置</el-button>
          </div>

          <el-table v-loading="loadingInstances" :data="instances" stripe empty-text="暂无入径实例">
            <el-table-column prop="patientId" label="患者" width="90" />
            <el-table-column prop="pathwayName" label="路径" min-width="160" />
            <el-table-column label="当前节点" min-width="150">
              <template #default="{ row }">
                <span v-if="row.status === 'ACTIVE'">{{ row.currentSequence }}. {{ row.currentNodeName || '已完成全部节点' }}</span>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="进度" width="150">
              <template #default="{ row }">
                <el-progress :percentage="progressOf(row)" :stroke-width="10" :show-text="false" />
                <span class="progress-text">{{ row.completedNodes }} / {{ row.totalNodes }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="STATUS[row.status]?.type">{{ STATUS[row.status]?.label || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="startedByName" label="入径人" width="100" />
            <el-table-column label="入径时间" width="160">
              <template #default="{ row }">{{ fmt(row.startedAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openInstance(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane v-if="auth.isPrivileged()" label="执行率统计" name="stats">
        <div class="panel panel-pad">
          <div class="toolbar">
            <el-date-picker v-model="statsRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px" />
            <el-button type="primary" plain :loading="loadingStats" @click="loadStats">统计</el-button>
            <span class="spacer" />
            <span class="text-muted">完成率 = 已完成实例 / 全部入径实例（近 30 天默认）</span>
          </div>
          <el-table v-loading="loadingStats" :data="stats" stripe empty-text="统计区间内暂无入径">
            <el-table-column prop="pathwayCode" label="路径编码" width="130" />
            <el-table-column prop="pathwayName" label="路径名称" min-width="160" />
            <el-table-column prop="total" label="入径" width="90" />
            <el-table-column prop="completed" label="完成" width="90" />
            <el-table-column prop="exited" label="退出" width="90" />
            <el-table-column label="完成率" width="160">
              <template #default="{ row }">
                <el-progress :percentage="Number(row.rate) || 0" :stroke-width="12" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="templateVisible" title="路径模板详情" width="720px">
      <template v-if="templateDetail">
        <el-descriptions :column="2" size="small" border style="margin-bottom: 16px">
          <el-descriptions-item label="路径编码">{{ templateDetail.pathwayCode }}</el-descriptions-item>
          <el-descriptions-item label="路径名称">{{ templateDetail.name }}</el-descriptions-item>
          <el-descriptions-item label="主要诊断">{{ templateDetail.diagnosisCode }}</el-descriptions-item>
          <el-descriptions-item label="科室">{{ templateDetail.department }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ templateDetail.version }}</el-descriptions-item>
          <el-descriptions-item label="标准住院日">{{ templateDetail.standardDays }} 天</el-descriptions-item>
        </el-descriptions>
        <div class="note-box" style="margin-bottom: 16px">{{ templateDetail.description || '暂无路径说明' }}</div>
        <el-table :data="templateDetail.nodes || []" size="small" border empty-text="暂无节点">
          <el-table-column prop="sequence" label="序号" width="60" />
          <el-table-column prop="nodeCode" label="节点编码" width="120" />
          <el-table-column prop="nodeName" label="节点名称" min-width="150" />
          <el-table-column prop="dayOffset" label="天数" width="70">
            <template #default="{ row }">D{{ row.dayOffset }}</template>
          </el-table-column>
          <el-table-column label="必填" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="row.required ? 'danger' : 'info'" effect="plain">{{ row.required ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="templateVisible = false">关闭</el-button>
        <el-button type="primary" @click="openEnroll(templateDetail)">患者入径</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="enrollVisible" title="患者入径" width="480px">
      <el-form label-width="90px">
        <el-form-item label="路径" required>
          <el-select v-model="enrollForm.pathwayId" filterable style="width: 100%">
            <el-option v-for="item in templates" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="患者 ID" required>
          <el-input v-model="enrollForm.patientId" placeholder="例如 1001" />
        </el-form-item>
        <el-form-item label="就诊 ID">
          <el-input v-model="enrollForm.visitId" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="enrollVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEnroll">确认入径</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="instanceVisible" title="入径实例详情" size="560px">
      <template v-if="instanceDetail">
        <div class="instance-head">
          <div>
            <div class="instance-name">{{ instanceDetail.pathwayName }}</div>
            <div class="text-muted mono">{{ instanceDetail.pathwayCode }} · 患者 {{ instanceDetail.patientId }}</div>
          </div>
          <el-tag :type="STATUS[instanceDetail.status]?.type">{{ STATUS[instanceDetail.status]?.label }}</el-tag>
        </div>

        <el-progress
          :percentage="progressOf(instanceDetail)"
          :stroke-width="14"
          :format="() => `${instanceDetail.completedNodes} / ${instanceDetail.totalNodes}`"
          style="margin: 8px 0 2px"
        />
        <div class="current-node" v-if="instanceDetail.status === 'ACTIVE'">
          当前节点：{{ instanceDetail.currentSequence }}. {{ instanceDetail.currentNodeName || '待更新' }}
        </div>
        <div class="current-node exit-note" v-if="instanceDetail.status === 'EXITED'">
          退出原因：{{ instanceDetail.exitReason || '-' }}
        </div>

        <div class="drawer-actions">
          <el-button type="success" :disabled="instanceDetail.status !== 'ACTIVE'" :loading="completing" @click="openComplete">完成当前节点</el-button>
          <el-button :disabled="instanceDetail.status !== 'ACTIVE'" @click="openVariance">记录变异</el-button>
          <el-button type="danger" plain :disabled="instanceDetail.status !== 'ACTIVE'" @click="openExit">退出路径</el-button>
        </div>

        <div class="section-title"><span>已执行节点</span><el-tag size="small" effect="plain">{{ (instanceDetail.nodeRecords || []).length }} 项</el-tag></div>
        <el-timeline v-if="(instanceDetail.nodeRecords || []).length">
          <el-timeline-item
            v-for="record in instanceDetail.nodeRecords"
            :key="record.sequence"
            :timestamp="fmt(record.completedAt)"
            placement="top"
          >
            <div class="tl-title">{{ record.sequence }}. {{ record.nodeName }} <span class="mono text-muted">{{ record.nodeCode }}</span></div>
            <div class="tl-sub">{{ record.completedByName }}{{ record.note ? ' · ' + record.note : '' }}</div>
          </el-timeline-item>
        </el-timeline>
        <div v-else class="empty-block">尚未执行任何节点</div>

        <div class="section-title" style="margin-top: 18px"><span>变异记录</span><el-tag size="small" effect="plain">{{ (instanceDetail.variances || []).length }} 项</el-tag></div>
        <el-table v-if="(instanceDetail.variances || []).length" :data="instanceDetail.variances" size="small" border>
          <el-table-column prop="nodeCode" label="节点" width="110" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ varianceLabel(row.varianceType) }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="150" show-overflow-tooltip />
          <el-table-column label="记录" width="150">
            <template #default="{ row }">{{ row.recordedByName }}<br /><span class="text-muted">{{ fmt(row.recordedAt) }}</span></template>
          </el-table-column>
        </el-table>
        <div v-else class="empty-block">暂无变异记录</div>
      </template>
    </el-drawer>

    <el-dialog v-model="completeVisible" title="完成当前节点" width="480px">
      <el-form label-width="90px">
        <el-form-item label="节点">
          <el-input :model-value="nextNode ? `${nextNode.sequence}. ${nextNode.nodeName}` : '已完成全部节点'" disabled />
        </el-form-item>
        <el-form-item label="执行备注">
          <el-input v-model="completeNote" type="textarea" :rows="3" placeholder="可填写节点执行说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!nextNode" :loading="submitting" @click="submitComplete">确认完成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="varianceVisible" title="记录路径变异" width="520px">
      <el-form label-width="90px">
        <el-form-item label="节点">
          <el-select v-model="varianceForm.nodeId" clearable placeholder="可选：关联具体节点" style="width: 100%">
            <el-option
              v-for="node in pathwayNodes"
              :key="node.id"
              :label="`${node.sequence}. ${node.nodeName}`"
              :value="node.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="变异类型" required>
          <el-select v-model="varianceForm.varianceType" style="width: 100%">
            <el-option v-for="type in VARIANCE_TYPES" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="变异原因" required>
          <el-input v-model="varianceForm.reason" type="textarea" :rows="3" placeholder="请描述变异原因与处理" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="varianceVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitVariance">保存变异</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="exitVisible" title="退出临床路径" width="480px">
      <el-alert title="退出后该实例将终止，不可继续执行节点。" type="warning" :closable="false" show-icon style="margin-bottom: 14px" />
      <el-form label-width="90px">
        <el-form-item label="退出原因" required>
          <el-input v-model="exitReason" type="textarea" :rows="3" placeholder="例如：患者转院、合并症需转科治疗" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exitVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitExit">确认退出</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'
import { auth } from '../../store/auth'

const STATUS = {
  ACTIVE: { label: '在径', type: 'primary' },
  COMPLETED: { label: '完成', type: 'success' },
  EXITED: { label: '退出', type: 'warning' }
}

const VARIANCE_TYPES = [
  { value: 'DELAY', label: '延迟' },
  { value: 'OMIT', label: '省略' },
  { value: 'REPLACE', label: '替代' },
  { value: 'OTHER', label: '其他' }
]

const activeTab = ref('templates')
const templates = ref([])
const loadingTemplates = ref(false)
const templateVisible = ref(false)
const templateDetail = ref(null)

const filters = reactive({ patientId: '', pathwayId: '', status: 'ACTIVE' })
const instances = ref([])
const loadingInstances = ref(false)

const stats = ref([])
const statsRange = ref([])
const loadingStats = ref(false)

const enrollVisible = ref(false)
const enrollForm = reactive({ pathwayId: null, patientId: '1001', visitId: '' })
const submitting = ref(false)

const instanceVisible = ref(false)
const instanceDetail = ref(null)
const pathwayNodes = ref([])
const completing = ref(false)

const completeVisible = ref(false)
const completeNote = ref('')
const varianceVisible = ref(false)
const varianceForm = reactive({ nodeId: null, varianceType: 'DELAY', reason: '' })
const exitVisible = ref(false)
const exitReason = ref('')

const instanceCounts = computed(() => {
  const result = { ACTIVE: 0, COMPLETED: 0, EXITED: 0 }
  instances.value.forEach((row) => {
    result[row.status] = (result[row.status] || 0) + 1
  })
  return result
})

const aggregateRate = computed(() => {
  if (!stats.value.length) return '0.00'
  const total = stats.value.reduce((sum, row) => sum + Number(row.total || 0), 0)
  const completed = stats.value.reduce((sum, row) => sum + Number(row.completed || 0), 0)
  return total ? ((completed * 100) / total).toFixed(2) : '0.00'
})

const statsWindow = computed(() => {
  if (statsRange.value?.length === 2) return `${statsRange.value[0]} ~ ${statsRange.value[1]}`
  return '近 30 天'
})

const nextNode = computed(() => {
  if (!instanceDetail.value) return null
  const done = new Set((instanceDetail.value.nodeRecords || []).map((record) => record.nodeCode))
  const nodes = [...pathwayNodes.value].sort((a, b) => a.sequence - b.sequence)
  return nodes.find((node) => !done.has(node.nodeCode)) || null
})

function fmt(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function progressOf(row) {
  if (!row || !row.totalNodes) return 0
  return Math.min(100, Math.round((row.completedNodes || 0) * 100 / row.totalNodes))
}

function varianceLabel(type) {
  return VARIANCE_TYPES.find((item) => item.value === type)?.label || type || '-'
}

async function loadTemplates() {
  loadingTemplates.value = true
  try {
    templates.value = await http.get('/e4/pathways')
  } catch {
    templates.value = []
  } finally {
    loadingTemplates.value = false
  }
}

async function loadInstances() {
  loadingInstances.value = true
  try {
    const params = {
      patientId: filters.patientId || undefined,
      pathwayId: filters.pathwayId || undefined,
      status: filters.status || undefined,
      limit: 100
    }
    if (!params.patientId && !params.pathwayId && !params.status) {
      params.status = 'ACTIVE'
    }
    instances.value = await http.get('/e4/pathways/instances', { params })
  } catch {
    instances.value = []
  } finally {
    loadingInstances.value = false
  }
}

function resetInstanceFilters() {
  filters.patientId = ''
  filters.pathwayId = ''
  filters.status = 'ACTIVE'
  loadInstances()
}

async function loadStats() {
  if (!auth.isPrivileged()) return
  loadingStats.value = true
  try {
    stats.value = await http.get('/e4/pathways/stats', {
      params: statsRange.value?.length === 2
        ? { from: statsRange.value[0], to: statsRange.value[1] }
        : {}
    })
  } catch {
    stats.value = []
  } finally {
    loadingStats.value = false
  }
}

async function openTemplate(row) {
  try {
    templateDetail.value = await http.get(`/e4/pathways/${row.id}`)
    templateVisible.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

function openEnroll(row) {
  if (!templates.value.length) {
    ElMessage.warning('暂无可入径的路径模板')
    return
  }
  enrollForm.pathwayId = row?.id || (templateDetail.value?.id ?? templates.value[0].id)
  enrollForm.patientId = filters.patientId || '1001'
  enrollForm.visitId = ''
  templateVisible.value = false
  enrollVisible.value = true
}

async function submitEnroll() {
  if (!enrollForm.pathwayId || !enrollForm.patientId) {
    ElMessage.warning('请选择路径并填写患者 ID')
    return
  }
  submitting.value = true
  try {
    await http.post(`/e4/pathways/${enrollForm.pathwayId}/enroll`, {
      patientId: Number(enrollForm.patientId),
      visitId: enrollForm.visitId ? Number(enrollForm.visitId) : null
    })
    ElMessage.success('入径成功')
    enrollVisible.value = false
    activeTab.value = 'instances'
    await loadInstances()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function openInstance(row) {
  try {
    instanceDetail.value = await http.get(`/e4/pathways/instances/${row.id}`)
    const pathway = await http.get(`/e4/pathways/${row.pathwayId}`)
    pathwayNodes.value = pathway.nodes || []
    instanceVisible.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

async function refreshInstance() {
  if (!instanceDetail.value) return
  instanceDetail.value = await http.get(`/e4/pathways/instances/${instanceDetail.value.id}`)
  await loadInstances()
}

function openComplete() {
  if (!nextNode.value) {
    ElMessage.info('当前路径已执行完所有节点')
    return
  }
  completeNote.value = ''
  completeVisible.value = true
}

async function submitComplete() {
  if (!nextNode.value) return
  submitting.value = true
  try {
    await http.post(
      `/e4/pathways/instances/${instanceDetail.value.id}/nodes/${nextNode.value.id}/complete`,
      { note: completeNote.value || null }
    )
    ElMessage.success('节点已完成')
    completeVisible.value = false
    await refreshInstance()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function openVariance() {
  varianceForm.nodeId = nextNode.value?.id || null
  varianceForm.varianceType = 'DELAY'
  varianceForm.reason = ''
  varianceVisible.value = true
}

async function submitVariance() {
  if (!varianceForm.reason.trim()) {
    ElMessage.warning('请填写变异原因')
    return
  }
  submitting.value = true
  try {
    await http.post(`/e4/pathways/instances/${instanceDetail.value.id}/variances`, {
      nodeId: varianceForm.nodeId || null,
      varianceType: varianceForm.varianceType,
      reason: varianceForm.reason
    })
    ElMessage.success('变异已记录')
    varianceVisible.value = false
    await refreshInstance()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

function openExit() {
  exitReason.value = ''
  exitVisible.value = true
}

async function submitExit() {
  if (!exitReason.value.trim()) {
    ElMessage.warning('请填写退出原因')
    return
  }
  submitting.value = true
  try {
    await http.post(`/e4/pathways/instances/${instanceDetail.value.id}/exit`, { reason: exitReason.value })
    ElMessage.success('已退出路径')
    exitVisible.value = false
    await refreshInstance()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadTemplates()
  await loadInstances()
  if (auth.isPrivileged()) {
    await loadStats()
  }
})
</script>

<style scoped>
.pathway-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}

.rate-unit {
  font-size: 14px;
  margin-left: 2px;
  color: var(--his-muted);
}

.progress-text {
  font-size: 11px;
  color: var(--his-muted);
}

.instance-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.instance-name {
  font-size: 16px;
  font-weight: 700;
}

.instance-head .text-muted {
  font-size: 12px;
  margin-top: 4px;
}

.current-node {
  margin-top: 6px;
  padding: 9px 12px;
  border-radius: 10px;
  background: #f0fdfa;
  border: 1px solid #ccfbf1;
  font-size: 12px;
  color: #0f766e;
}

.exit-note {
  background: #fff7e6;
  border-color: #fde68a;
  color: #b45309;
}

.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 14px 0 18px;
}

.tl-title {
  font-size: 13px;
  font-weight: 600;
}

.tl-sub {
  font-size: 12px;
  color: var(--his-muted);
  margin-top: 3px;
}

.note-box {
  padding: 12px 14px;
  border-radius: 10px;
  background: #fbfdfd;
  border: 1px solid var(--his-line);
  font-size: 12px;
  line-height: 1.7;
}
</style>
