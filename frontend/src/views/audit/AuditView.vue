<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">审计日志</h1>
        <p class="page-sub">记录关键业务操作的操作人、动作、资源、耗时与结果，支撑电子病历操作留痕。</p>
      </div>
      <el-tag effect="plain" type="warning">仅管理员可见</el-tag>
    </div>

    <div class="stat-grid">
      <div class="stat-card" style="--his-primary-soft: #e6fffa">
        <div class="stat-label">当前范围记录</div>
        <div class="stat-value">{{ logs.length }}</div>
        <div class="stat-note">最近 {{ filters.limit }} 条</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #f0fdf4">
        <div class="stat-label">成功</div>
        <div class="stat-value">{{ counts.success }}</div>
        <div class="stat-note">操作正常完成</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #fef2f2">
        <div class="stat-label">失败</div>
        <div class="stat-value">{{ counts.failure }}</div>
        <div class="stat-note">含异常与校验失败</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #eef6ff">
        <div class="stat-label">平均耗时</div>
        <div class="stat-value">{{ counts.avgDuration }}<span class="unit">ms</span></div>
        <div class="stat-note">当前范围平均值</div>
      </div>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar">
        <el-input v-model="filters.action" placeholder="动作编码，例如 EMR_SIGN" clearable style="width: 220px" @keyup.enter="load" />
        <el-select v-model="filters.limit" style="width: 130px" @change="load">
          <el-option label="最近 50 条" :value="50" />
          <el-option label="最近 100 条" :value="100" />
          <el-option label="最近 200 条" :value="200" />
        </el-select>
        <el-button type="primary" plain :loading="loading" @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
        <span class="spacer" />
        <span class="text-muted">按时间倒序排列</span>
      </div>

      <el-table v-loading="loading" :data="logs" stripe empty-text="暂无审计记录">
        <el-table-column label="时间" width="160">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="110">
          <template #default="{ row }">
            {{ row.operatorName || '-' }}
            <span v-if="row.operatorId" class="text-muted mono">#{{ row.operatorId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="action" label="动作" width="180">
          <template #default="{ row }"><el-tag size="small" effect="plain" class="mono">{{ row.action }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="resourceType" label="资源" width="150" />
        <el-table-column label="请求" min-width="200">
          <template #default="{ row }">
            <span class="method" :class="row.httpMethod">{{ row.httpMethod }}</span>
            <span class="mono">{{ row.requestUri }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="ip" label="IP" width="130">
          <template #default="{ row }"><span class="mono">{{ row.ip || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ row.durationMs != null ? row.durationMs + ' ms' : '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="detailVisible" title="审计详情" size="480px">
      <template v-if="detail">
        <div class="detail-grid">
          <div class="kv"><span>记录 ID</span><b class="mono">{{ detail.id }}</b></div>
          <div class="kv"><span>操作人</span><b>{{ detail.operatorName || '-' }} {{ detail.operatorId ? '#' + detail.operatorId : '' }}</b></div>
          <div class="kv"><span>动作</span><b class="mono">{{ detail.action }}</b></div>
          <div class="kv"><span>资源类型</span><b>{{ detail.resourceType || '-' }}</b></div>
          <div class="kv"><span>请求方法</span><b>{{ detail.httpMethod }}</b></div>
          <div class="kv"><span>请求路径</span><b class="mono">{{ detail.requestUri }}</b></div>
          <div class="kv"><span>来源 IP</span><b class="mono">{{ detail.ip || '-' }}</b></div>
          <div class="kv"><span>耗时</span><b>{{ detail.durationMs != null ? detail.durationMs + ' ms' : '-' }}</b></div>
          <div class="kv"><span>结果</span><b>{{ detail.success ? '成功' : '失败' }}</b></div>
          <div class="kv"><span>时间</span><b>{{ fmt(detail.createdAt) }}</b></div>
        </div>
        <div class="section-title" style="margin-top: 18px"><span>异常信息</span></div>
        <div class="note-box error">{{ detail.errorMsg || '无异常信息' }}</div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import http from '../../api/http'

const filters = reactive({ action: '', limit: 50 })
const logs = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const detail = ref(null)

const counts = computed(() => {
  const result = { success: 0, failure: 0, avgDuration: 0 }
  let totalDuration = 0
  let durationCount = 0
  logs.value.forEach((row) => {
    if (row.success) {
      result.success += 1
    } else {
      result.failure += 1
    }
    if (row.durationMs != null) {
      totalDuration += Number(row.durationMs)
      durationCount += 1
    }
  })
  result.avgDuration = durationCount ? Math.round(totalDuration / durationCount) : 0
  return result
})

function fmt(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  try {
    logs.value = await http.get('/audit/logs', {
      params: { limit: filters.limit, action: filters.action || undefined }
    })
  } catch {
    logs.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.action = ''
  filters.limit = 50
  load()
}

function openDetail(row) {
  detail.value = row
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.unit {
  font-size: 13px;
  color: var(--his-muted);
  margin-left: 3px;
}

.method {
  display: inline-block;
  min-width: 52px;
  margin-right: 6px;
  padding: 0 6px;
  border-radius: 4px;
  font-family: "JetBrains Mono", "Cascadia Code", Consolas, monospace;
  font-size: 11px;
  text-align: center;
  background: #f1f5f9;
  color: #334155;
}

.method.POST,
.method.PUT {
  background: #fef3c7;
  color: #92400e;
}

.method.DELETE {
  background: #fee2e2;
  color: #991b1b;
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
  white-space: pre-wrap;
}

.note-box.error {
  background: #fff5f5;
  border-color: #fecaca;
  color: #b91c1c;
}
</style>
