<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">四条闭环</h1>
        <p class="page-sub">医嘱、处方、检验、检查从开立到完成的全链路留痕。</p>
      </div>
      <el-button type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        开立闭环单据
      </el-button>
    </div>

    <div v-if="auth.isPrivileged()" class="stat-grid">
      <div v-for="item in stats" :key="item.loopType" class="stat-card">
        <div class="stat-label">{{ LOOP_TYPE[item.loopType] || item.loopType }}</div>
        <div class="stat-value">{{ item.rate }}<span style="font-size: 14px; margin-left: 4px">%</span></div>
        <div class="stat-note">已闭环 {{ item.closed }} / 总数 {{ item.total }}</div>
      </div>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar">
        <el-input v-model="filters.patientId" placeholder="患者 ID" clearable style="width: 130px" @keyup.enter="load" />
        <el-input v-model="filters.visitId" placeholder="就诊 ID" clearable style="width: 130px" @keyup.enter="load" />
        <el-select v-model="filters.loopType" placeholder="全部类型" clearable style="width: 150px">
          <el-option v-for="(item, key) in LOOP_TYPE" :key="key" :label="item" :value="key" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option label="进行中" value="ACTIVE" />
          <el-option label="已闭环" value="CLOSED" />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="load">查询</el-button>
        <span class="spacer" />
        <el-button @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="loops" stripe empty-text="暂无闭环单据">
        <el-table-column prop="bizNo" label="业务号" width="180">
          <template #default="{ row }"><span class="mono">{{ row.bizNo }}</span></template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ LOOP_TYPE[row.loopType] || row.loopType }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="patientId" label="患者" width="90" />
        <el-table-column label="当前节点" width="120">
          <template #default="{ row }">{{ nodeName(row.loopType, row.currentNode) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'CLOSED' ? 'success' : 'warning'">
              {{ row.status === 'CLOSED' ? '已闭环' : '进行中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">{{ fmt(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="success" @click="openAdvance(row)">推进</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="createVisible" title="开立闭环单据" width="560px">
      <el-form label-width="90px">
        <el-form-item label="闭环类型" required>
          <el-select v-model="createForm.loopType" style="width: 100%">
            <el-option v-for="(item, key) in LOOP_TYPE" :key="key" :label="item" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="患者 ID" required>
          <el-input v-model="createForm.patientId" placeholder="例如 1001" />
        </el-form-item>
        <el-form-item label="就诊 ID">
          <el-input v-model="createForm.visitId" />
        </el-form-item>
        <el-form-item label="单据标题" required>
          <el-input v-model="createForm.title" placeholder="例如：头孢曲松 2g 静滴" />
        </el-form-item>
        <el-form-item label="业务明细">
          <el-input v-model="createForm.detail" type="textarea" :rows="4" placeholder="可选，JSON 对象，例如 {&quot;drugCode&quot;:&quot;CEFTRIAXONE&quot;}" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">开立</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="advanceVisible" title="推进闭环节点" width="520px">
      <div v-if="advanceTarget" class="advance-head">
        <div class="advance-title">{{ advanceTarget.title }}</div>
        <div class="advance-sub">{{ LOOP_TYPE[advanceTarget.loopType] }} · 当前 {{ nodeName(advanceTarget.loopType, advanceTarget.currentNode) }}</div>
      </div>
      <el-form label-width="90px">
        <el-form-item label="下一节点" required>
          <el-select v-model="advanceForm.nodeCode" style="width: 100%">
            <el-option
              v-for="code in nextOptions"
              :key="code"
              :label="`${code} · ${nodeName(advanceTarget?.loopType, code)}`"
              :value="code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="节点数据">
          <el-input v-model="advanceForm.payload" type="textarea" :rows="4" placeholder="可选，JSON 对象" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="advanceVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAdvance">推进</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="闭环单据详情" size="620px">
      <template v-if="detail">
        <div class="detail-head">
          <div>
            <div class="detail-title">{{ detail.title }}</div>
            <div class="detail-sub mono">{{ detail.bizNo }} · 患者 {{ detail.patientId }}</div>
          </div>
          <el-tag :type="detail.status === 'CLOSED' ? 'success' : 'warning'">
            {{ detail.status === 'CLOSED' ? '已闭环' : '进行中' }}
          </el-tag>
        </div>

        <el-steps :active="stepActive" finish-status="success" align-center style="margin: 18px 0 22px">
          <el-step v-for="code in detail.expectedNodes" :key="code" :title="nodeName(detail.loopType, code)" />
        </el-steps>

        <div class="section-title"><span>节点记录</span></div>
        <el-timeline v-if="detail.nodes?.length">
          <el-timeline-item
            v-for="(node, index) in detail.nodes"
            :key="index"
            :timestamp="`${fmt(node.occurredAt)} · ${node.operatorName || '-'}`"
            :type="index === detail.nodes.length - 1 ? 'success' : 'primary'"
            placement="top"
          >
            <div class="node-card">
              <div class="node-name">{{ node.sequence }}. {{ node.nodeName }}</div>
              <div v-if="node.payload" class="mono node-payload">{{ pretty(node.payload) }}</div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无节点记录" :image-size="70" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'
import { auth } from '../../store/auth'

const LOOP_TYPE = { MED_ORDER: '医嘱闭环', PRESCRIPTION: '处方闭环', LAB: '检验闭环', EXAM: '检查闭环' }

const CHAINS = {
  MED_ORDER: [
    ['ORDERED', '开立'],
    ['REVIEWED', '审核'],
    ['EXECUTED', '执行'],
    ['CHARGED', '计费']
  ],
  PRESCRIPTION: [
    ['ORDERED', '开立'],
    ['REVIEWED', '审方'],
    ['CHARGED', '收费'],
    ['DISPENSED', '调配'],
    ['DISPENSED_OUT', '发药']
  ],
  LAB: [
    ['ORDERED', '申请'],
    ['CHARGED', '收费'],
    ['SAMPLED', '采样'],
    ['RECEIVED', '接收'],
    ['TESTED', '上机'],
    ['VERIFIED', '审核'],
    ['REPORTED', '报告'],
    ['VIEWED', '医生调阅']
  ],
  EXAM: [
    ['ORDERED', '申请'],
    ['CHARGED', '收费'],
    ['SCHEDULED', '预约'],
    ['PERFORMED', '检查'],
    ['REPORTED', '报告'],
    ['VIEWED', '医生调阅']
  ]
}

const filters = reactive({ patientId: '', visitId: '', loopType: '', status: '' })
const loops = ref([])
const loading = ref(false)
const stats = ref([])
const submitting = ref(false)

const createVisible = ref(false)
const createForm = reactive({ loopType: 'MED_ORDER', patientId: '1001', visitId: '', title: '', detail: '' })

const advanceVisible = ref(false)
const advanceTarget = ref(null)
const advanceForm = reactive({ nodeCode: '', payload: '' })

const detailVisible = ref(false)
const detail = ref(null)

const nextOptions = computed(() => {
  if (!advanceTarget.value) return []
  const chain = CHAINS[advanceTarget.value.loopType] || []
  const index = chain.findIndex(([code]) => code === advanceTarget.value.currentNode)
  return chain.slice(index + 1).map(([code]) => code)
})

const stepActive = computed(() => {
  if (!detail.value) return 0
  if (detail.value.status === 'CLOSED') return detail.value.expectedNodes.length
  const index = detail.value.expectedNodes.indexOf(detail.value.currentNode)
  return Math.max(1, index + 1)
})

function nodeName(loopType, code) {
  const chain = CHAINS[loopType] || []
  return chain.find(([nodeCode]) => nodeCode === code)?.[1] || code
}
function fmt(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}
function pretty(value) {
  return JSON.stringify(value, null, 2)
}

async function load() {
  loading.value = true
  try {
    loops.value = await http.get('/e5/loops', {
      params: {
        patientId: filters.patientId || undefined,
        visitId: filters.visitId || undefined,
        loopType: filters.loopType || undefined,
        status: filters.status || undefined,
        limit: 100
      }
    })
  } catch {
    loops.value = []
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  if (!auth.isPrivileged()) return
  try {
    stats.value = await http.get('/e5/loops/stats')
  } catch {
    stats.value = []
  }
}

function openCreate() {
  createForm.loopType = 'MED_ORDER'
  createForm.patientId = filters.patientId || '1001'
  createForm.visitId = filters.visitId || ''
  createForm.title = ''
  createForm.detail = ''
  createVisible.value = true
}

function parseJson(text, fallback) {
  if (!text || !text.trim()) return null
  try {
    return JSON.parse(text)
  } catch {
    ElMessage.warning('业务明细不是合法 JSON，已按空值提交')
    return fallback
  }
}

async function submitCreate() {
  if (!createForm.patientId || !createForm.title) {
    ElMessage.warning('请填写患者 ID 与单据标题')
    return
  }
  submitting.value = true
  try {
    await http.post('/e5/loops', {
      loopType: createForm.loopType,
      patientId: Number(createForm.patientId),
      visitId: createForm.visitId ? Number(createForm.visitId) : null,
      title: createForm.title,
      detail: parseJson(createForm.detail)
    })
    ElMessage.success('闭环单据已开立')
    createVisible.value = false
    await load()
    await loadStats()
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

async function openDetail(row) {
  try {
    detail.value = await http.get(`/e5/loops/${row.id}`)
    detailVisible.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

function openAdvance(row) {
  advanceTarget.value = row
  advanceForm.nodeCode = nextOptions.value[0] || ''
  advanceForm.payload = ''
  advanceVisible.value = true
}

async function submitAdvance() {
  if (!advanceForm.nodeCode) {
    ElMessage.warning('请选择下一节点')
    return
  }
  submitting.value = true
  try {
    await http.post(`/e5/loops/${advanceTarget.value.id}/advance`, {
      nodeCode: advanceForm.nodeCode,
      payload: parseJson(advanceForm.payload)
    })
    ElMessage.success('节点已推进')
    advanceVisible.value = false
    await load()
    await loadStats()
    if (detailVisible.value) await openDetail(advanceTarget.value)
  } catch {
    /* 拦截器已提示 */
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  load()
  loadStats()
})
</script>

<style scoped>
.advance-head {
  padding: 12px 14px;
  border-radius: 10px;
  background: #f4fafa;
  border: 1px solid var(--his-line);
  margin-bottom: 14px;
}

.advance-title {
  font-size: 14px;
  font-weight: 700;
}

.advance-sub {
  font-size: 12px;
  color: var(--his-muted);
  margin-top: 4px;
}

.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--his-line);
}

.detail-title {
  font-size: 16px;
  font-weight: 700;
}

.detail-sub {
  font-size: 11px;
  color: var(--his-muted);
  margin-top: 5px;
}

.node-card {
  padding: 10px 12px;
  border-radius: 10px;
  background: #fbfdfd;
  border: 1px solid var(--his-line);
}

.node-name {
  font-size: 13px;
  font-weight: 600;
}

.node-payload {
  margin: 6px 0 0;
  font-size: 11px;
  color: var(--his-muted);
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
