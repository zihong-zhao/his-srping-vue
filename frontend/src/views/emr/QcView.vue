<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">质控管理</h1>
        <p class="page-sub">运行质控规则、查看缺陷并完成整改闭环。</p>
      </div>
      <el-button type="primary" plain @click="runVisible = true">运行质控</el-button>
    </div>

    <div class="qc-top">
      <div class="panel panel-pad run-panel">
        <div class="section-title"><span>按病历运行质控</span></div>
        <div class="toolbar" style="margin-bottom: 0">
          <el-input v-model="runDocId" placeholder="病历 ID" style="width: 200px" @keyup.enter="runQc" />
          <el-button type="primary" :loading="running" @click="runQc">运行</el-button>
          <span class="text-muted" style="font-size: 12px">阻断级缺陷会阻止提交</span>
        </div>
      </div>
      <div class="panel panel-pad mini-stats">
        <div class="mini">
          <div class="mini-label">未关闭缺陷</div>
          <div class="mini-value">{{ openCount }}</div>
        </div>
        <div class="mini">
          <div class="mini-label">已关闭缺陷</div>
          <div class="mini-value">{{ closedCount }}</div>
        </div>
        <div class="mini">
          <div class="mini-label">规则总数</div>
          <div class="mini-value">{{ rules.length }}</div>
        </div>
      </div>
    </div>

    <el-tabs v-model="tab" class="panel panel-pad" style="margin-top: 16px">
      <el-tab-pane label="缺陷清单" name="issues">
        <div class="toolbar">
          <el-input v-model="issueFilters.docId" placeholder="病历 ID" clearable style="width: 150px" @keyup.enter="loadIssues" />
          <el-select v-model="issueFilters.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="未关闭" value="OPEN" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
          <el-button type="primary" plain :loading="loadingIssues" @click="loadIssues">查询</el-button>
          <span class="spacer" />
          <el-button @click="loadIssues">刷新</el-button>
        </div>
        <el-table v-loading="loadingIssues" :data="issues" stripe empty-text="暂无质控缺陷">
          <el-table-column prop="docId" label="病历 ID" width="150">
            <template #default="{ row }"><span class="mono">{{ row.docId }}</span></template>
          </el-table-column>
          <el-table-column prop="ruleName" label="规则" width="170" />
          <el-table-column prop="category" label="分类" width="110" />
          <el-table-column label="级别" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="severityType(row.severity)">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="defectDesc" label="缺陷描述" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'OPEN' ? 'warning' : 'success'">
                {{ row.status === 'OPEN' ? '未关闭' : '已关闭' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="assigneeName" label="责任人" width="100" />
          <el-table-column label="创建时间" width="160">
            <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'OPEN'" link type="primary" @click="openClose(row)">整改</el-button>
              <span v-else class="text-muted" style="font-size: 12px">{{ fmt(row.closedAt) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="auth.isPrivileged()" label="质控规则" name="rules">
        <el-table :data="rules" stripe empty-text="暂无规则">
          <el-table-column prop="ruleCode" label="规则编码" width="160" />
          <el-table-column prop="name" label="规则名称" min-width="180" />
          <el-table-column prop="category" label="分类" width="130" />
          <el-table-column label="级别" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="severityType(row.severity)">{{ row.severity }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="docType" label="适用范围" width="140">
            <template #default="{ row }">{{ row.docType || '全部' }}</template>
          </el-table-column>
          <el-table-column label="启用" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="runVisible" title="运行质控" width="480px">
      <el-form label-width="90px">
        <el-form-item label="病历 ID" required>
          <el-input v-model="runDocId" placeholder="请输入要检查的病历 ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runVisible = false">取消</el-button>
        <el-button type="primary" :loading="running" @click="runQc">开始检查</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="closeVisible" title="整改并关闭缺陷" width="520px">
      <el-descriptions :column="1" size="small" border style="margin-bottom: 14px">
        <el-descriptions-item label="规则">{{ closeTarget?.ruleName }}</el-descriptions-item>
        <el-descriptions-item label="缺陷">{{ closeTarget?.defectDesc }}</el-descriptions-item>
      </el-descriptions>
      <el-input v-model="closeNote" type="textarea" :rows="4" placeholder="请填写整改措施与结果" />
      <template #footer>
        <el-button @click="closeVisible = false">取消</el-button>
        <el-button type="primary" :loading="closing" @click="submitClose">关闭缺陷</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'
import { auth } from '../../store/auth'

const tab = ref('issues')
const issues = ref([])
const rules = ref([])
const loadingIssues = ref(false)
const issueFilters = reactive({ docId: '', status: 'OPEN' })

const runVisible = ref(false)
const runDocId = ref('')
const running = ref(false)

const closeVisible = ref(false)
const closeTarget = ref(null)
const closeNote = ref('')
const closing = ref(false)

const openCount = computed(() => issues.value.filter((item) => item.status === 'OPEN').length)
const closedCount = computed(() => issues.value.filter((item) => item.status === 'CLOSED').length)

function severityType(severity) {
  if (severity === 'BLOCK') return 'danger'
  if (severity === 'WARN') return 'warning'
  return 'info'
}
function fmt(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

async function loadIssues() {
  loadingIssues.value = true
  try {
    issues.value = await http.get('/emr/qc/issues', { params: { ...issueFilters, limit: 100 } })
  } catch {
    issues.value = []
  } finally {
    loadingIssues.value = false
  }
}

async function loadRules() {
  if (!auth.isPrivileged()) return
  try {
    rules.value = await http.get('/emr/qc/rules')
  } catch {
    rules.value = []
  }
}

async function runQc() {
  if (!runDocId.value) {
    ElMessage.warning('请输入病历 ID')
    return
  }
  running.value = true
  try {
    const result = await http.post(`/emr/documents/${runDocId.value}/qc/run`)
    if (result.issues?.length) {
      issues.value = result.issues
      tab.value = 'issues'
      issueFilters.docId = String(runDocId.value)
      issueFilters.status = 'OPEN'
      ElMessage.warning(result.blocked ? '存在阻断级缺陷，无法提交' : `发现 ${result.issues.length} 条质控缺陷`)
    } else {
      ElMessage.success('质控通过，未发现缺陷')
    }
    runVisible.value = false
    await loadIssues()
  } catch {
    /* 拦截器已提示 */
  } finally {
    running.value = false
  }
}

function openClose(row) {
  closeTarget.value = row
  closeNote.value = ''
  closeVisible.value = true
}

async function submitClose() {
  if (!closeNote.value.trim()) {
    ElMessage.warning('请填写整改说明')
    return
  }
  closing.value = true
  try {
    await http.post(`/emr/qc/issues/${closeTarget.value.id}/close`, { note: closeNote.value })
    ElMessage.success('缺陷已关闭')
    closeVisible.value = false
    await loadIssues()
  } catch {
    /* 拦截器已提示 */
  } finally {
    closing.value = false
  }
}

onMounted(() => {
  loadIssues()
  loadRules()
})
</script>

<style scoped>
.qc-top {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
}

.mini-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.mini {
  text-align: center;
  padding: 6px 0;
  border-right: 1px dashed var(--his-line);
}

.mini:last-child {
  border-right: none;
}

.mini-label {
  font-size: 11px;
  color: var(--his-muted);
}

.mini-value {
  font-size: 24px;
  font-weight: 700;
  margin-top: 6px;
}

@media (max-width: 1100px) {
  .qc-top {
    grid-template-columns: 1fr;
  }
}
</style>
