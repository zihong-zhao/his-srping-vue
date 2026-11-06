<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">检验检查知识库</h1>
        <p class="page-sub">项目字典、检查适应症校验与危急值阈值判定，支撑开单前的决策提醒。</p>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card" style="--his-primary-soft: #e6fffa">
        <div class="stat-label">知识库项目</div>
        <div class="stat-value">{{ items.length }}</div>
        <div class="stat-note">当前筛选范围</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #eef6ff">
        <div class="stat-label">检验项目</div>
        <div class="stat-value">{{ counts.LAB }}</div>
        <div class="stat-note">含危急值阈值</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #f5f0ff">
        <div class="stat-label">检查项目</div>
        <div class="stat-value">{{ counts.EXAM }}</div>
        <div class="stat-note">含适应症与标本要求</div>
      </div>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar">
        <el-select v-model="filters.itemType" placeholder="全部类型" clearable style="width: 150px">
          <el-option label="检验 LAB" value="LAB" />
          <el-option label="检查 EXAM" value="EXAM" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="项目名称 / 编码 / 类别"
          clearable
          style="width: 240px"
          @keyup.enter="load"
        />
        <el-button type="primary" plain :loading="loading" @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <span class="spacer" />
        <el-button type="primary" :disabled="!selectedCode" @click="openIndication">适应症校验</el-button>
        <el-button :disabled="!selectedCode" @click="openCritical">危急值判定</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="items"
        stripe
        highlight-current-row
        empty-text="暂无匹配项目"
        @current-change="onCurrentChange"
      >
        <el-table-column prop="itemCode" label="项目编码" width="120">
          <template #default="{ row }"><span class="mono">{{ row.itemCode }}</span></template>
        </el-table-column>
        <el-table-column prop="itemName" label="项目名称" min-width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.itemType === 'LAB' ? 'success' : 'primary'" effect="plain">
              {{ row.itemType === 'LAB' ? '检验' : '检查' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="类别" width="110" />
        <el-table-column prop="specimen" label="标本" width="110" />
        <el-table-column prop="referenceRange" label="参考范围" width="130" />
        <el-table-column label="危急值阈值" width="150">
          <template #default="{ row }">
            <span v-if="row.criticalLow !== null || row.criticalHigh !== null" class="crit-range">
              {{ row.criticalLow ?? '-' }} ~ {{ row.criticalHigh ?? '-' }}
            </span>
            <span v-else class="text-muted">未配置</span>
          </template>
        </el-table-column>
        <el-table-column prop="turnaroundMinutes" label="周转" width="80">
          <template #default="{ row }">{{ row.turnaroundMinutes ? row.turnaroundMinutes + ' 分' : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="detailVisible" title="项目详情" size="500px">
      <template v-if="detail">
        <div class="detail-grid">
          <div class="kv"><span>项目编码</span><b class="mono">{{ detail.itemCode }}</b></div>
          <div class="kv"><span>项目名称</span><b>{{ detail.itemName }}</b></div>
          <div class="kv"><span>类型</span><b>{{ detail.itemType === 'LAB' ? '检验' : '检查' }}</b></div>
          <div class="kv"><span>类别</span><b>{{ detail.category || '-' }}</b></div>
          <div class="kv"><span>标本</span><b>{{ detail.specimen || '-' }}</b></div>
          <div class="kv"><span>参考范围</span><b>{{ detail.referenceRange || '-' }}</b></div>
          <div class="kv"><span>单位</span><b>{{ detail.unit || '-' }}</b></div>
          <div class="kv">
            <span>危急值阈值</span>
            <b>{{ detail.criticalLow ?? '-' }} ~ {{ detail.criticalHigh ?? '-' }}</b>
          </div>
          <div class="kv"><span>周转时间</span><b>{{ detail.turnaroundMinutes ? detail.turnaroundMinutes + ' 分钟' : '-' }}</b></div>
        </div>
        <div class="section-title" style="margin-top: 18px"><span>检查注意事项</span></div>
        <div class="note-box">{{ detail.precaution || '暂无注意事项' }}</div>
        <div class="section-title" style="margin-top: 18px">
          <span>推荐诊断</span>
          <el-tag size="small" effect="plain">{{ (detail.indications || []).length }} 项</el-tag>
        </div>
        <div class="tag-row">
          <el-tag v-for="code in detail.indications || []" :key="code" size="small" class="mono">{{ code }}</el-tag>
          <span v-if="!(detail.indications || []).length" class="text-muted">未配置适应症</span>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="indicationVisible" title="按诊断校验适应症" width="540px">
      <el-form label-width="96px">
        <el-form-item label="项目">
          <el-input :model-value="selectedItem ? `${selectedItem.itemName} · ${selectedItem.itemCode}` : ''" disabled />
        </el-form-item>
        <el-form-item label="诊断编码">
          <el-select
            v-model="diagnosisCodes"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入 ICD-10 编码后回车，例如 I10、J18.9"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="indicationResult"
        :title="indicationResult.message"
        :type="indicationResult.restricted ? 'warning' : indicationResult.indicated ? 'success' : 'info'"
        :closable="false"
        show-icon
        style="margin-top: 4px"
      >
        <template #default>
          <div class="result-line">匹配诊断：{{ (indicationResult.matchedDiagnoses || []).join('、') || '无' }}</div>
          <div class="result-line" v-if="indicationResult.specimen">标本要求：{{ indicationResult.specimen }}</div>
          <div class="result-line" v-if="indicationResult.precaution">注意事项：{{ indicationResult.precaution }}</div>
        </template>
      </el-alert>
      <template #footer>
        <el-button @click="indicationVisible = false">关闭</el-button>
        <el-button type="primary" :loading="checking" @click="runIndication">校验</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="criticalVisible" title="按阈值判定危急值" width="540px">
      <el-form label-width="96px">
        <el-form-item label="项目">
          <el-input :model-value="selectedItem ? `${selectedItem.itemName} · ${selectedItem.itemCode}` : ''" disabled />
        </el-form-item>
        <el-form-item label="结果值">
          <el-input v-model="criticalValue" placeholder="例如 6.8">
            <template #append>{{ selectedItem?.unit || '单位' }}</template>
          </el-input>
        </el-form-item>
        <el-form-item label="参考范围">
          <el-input :model-value="selectedItem?.referenceRange || '-'" disabled />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="criticalResult"
        :title="criticalResult.message"
        :type="criticalAlertType"
        :closable="false"
        show-icon
        style="margin-top: 4px"
      >
        <template #default>
          <div class="result-line">
            阈值区间：{{ criticalResult.criticalLow ?? '-' }} ~ {{ criticalResult.criticalHigh ?? '-' }}
            {{ criticalResult.unit || '' }}
          </div>
        </template>
      </el-alert>
      <template #footer>
        <el-button @click="criticalVisible = false">关闭</el-button>
        <el-button type="primary" :loading="checking" @click="runCritical">判定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import http from '../../api/http'

const filters = reactive({ itemType: '', keyword: '' })
const items = ref([])
const loading = ref(false)

const selectedItem = ref(null)
const selectedCode = computed(() => selectedItem.value?.itemCode || '')

const detailVisible = ref(false)
const detail = ref(null)

const indicationVisible = ref(false)
const diagnosisCodes = ref([])
const indicationResult = ref(null)
const criticalVisible = ref(false)
const criticalValue = ref('')
const criticalResult = ref(null)
const checking = ref(false)

const counts = computed(() => {
  const result = { LAB: 0, EXAM: 0 }
  items.value.forEach((item) => {
    result[item.itemType] = (result[item.itemType] || 0) + 1
  })
  return result
})

const criticalAlertType = computed(() => {
  if (!criticalResult.value) return 'info'
  if (criticalResult.value.severity === 'HIGH') return 'error'
  if (criticalResult.value.severity === 'LOW') return 'warning'
  return 'success'
})

async function load() {
  loading.value = true
  try {
    items.value = await http.get('/e4/exam-items', {
      params: {
        itemType: filters.itemType || undefined,
        keyword: filters.keyword || undefined,
        limit: 200
      }
    })
    if (!items.value.some((row) => row.itemCode === selectedCode.value)) {
      selectedItem.value = null
    }
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.itemType = ''
  filters.keyword = ''
  load()
}

function onCurrentChange(row) {
  selectedItem.value = row || null
}

async function openDetail(row) {
  try {
    detail.value = await http.get(`/e4/exam-items/${row.itemCode}`)
    selectedItem.value = row
    detailVisible.value = true
  } catch {
    /* 拦截器已提示 */
  }
}

function openIndication() {
  if (!selectedItem.value) return
  diagnosisCodes.value = [...(selectedItem.value.indications || []).slice(0, 1)]
  indicationResult.value = null
  indicationVisible.value = true
}

function openCritical() {
  if (!selectedItem.value) return
  criticalValue.value = ''
  criticalResult.value = null
  criticalVisible.value = true
}

async function runIndication() {
  if (!selectedCode.value) return
  checking.value = true
  try {
    indicationResult.value = await http.post('/e4/exam-items/indication-check', {
      itemCode: selectedCode.value,
      diagnosisCodes: diagnosisCodes.value
    })
  } catch {
    /* 拦截器已提示 */
  } finally {
    checking.value = false
  }
}

async function runCritical() {
  if (!selectedCode.value) return
  const value = Number(criticalValue.value)
  if (!criticalValue.value || Number.isNaN(value)) {
    ElMessage.warning('请输入数字类型的结果值')
    return
  }
  checking.value = true
  try {
    criticalResult.value = await http.post('/e4/exam-items/critical-check', {
      itemCode: selectedCode.value,
      value
    })
  } catch {
    /* 拦截器已提示 */
  } finally {
    checking.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.crit-range {
  font-weight: 600;
  color: #b45309;
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
  min-height: 54px;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.result-line {
  font-size: 12px;
  line-height: 1.7;
}
</style>
