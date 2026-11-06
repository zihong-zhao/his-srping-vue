<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">患者时间轴</h1>
        <p class="page-sub">按患者聚合门诊、住院、医嘱、检验检查与签名事件，形成纵向临床数据视图。</p>
      </div>
    </div>

    <div class="panel panel-pad">
      <div class="toolbar" style="margin-bottom: 0">
        <el-input v-model="patientId" placeholder="患者 ID" style="width: 180px" @keyup.enter="load" />
        <el-button type="primary" :loading="loading" @click="load">加载时间轴</el-button>
        <span class="text-muted" style="font-size: 12px">默认演示患者 1001</span>
      </div>
    </div>

    <template v-if="timeline">
      <div class="stat-grid" style="margin-top: 16px">
        <div class="stat-card">
          <div class="stat-label">文书总数</div>
          <div class="stat-value">{{ timeline.totalDocuments }}</div>
          <div class="stat-note">患者 {{ timeline.patientId }}</div>
        </div>
        <div class="stat-card" style="--his-primary-soft: #f0fdf4">
          <div class="stat-label">已签名文书</div>
          <div class="stat-value">{{ timeline.signedDocuments }}</div>
          <div class="stat-note">完成医师签名</div>
        </div>
        <div class="stat-card" style="--his-primary-soft: #fff7e6">
          <div class="stat-label">最近活动</div>
          <div class="stat-value" style="font-size: 20px">{{ fmt(timeline.latestActivityAt) }}</div>
          <div class="stat-note">最后一次临床事件</div>
        </div>
        <div class="stat-card" style="--his-primary-soft: #eef2ff">
          <div class="stat-label">文书类型</div>
          <div class="stat-value">{{ docTypeCount }}</div>
          <div class="stat-note">覆盖不同类型记录</div>
        </div>
      </div>

      <div class="timeline-grid">
        <div class="panel panel-pad">
          <div class="section-title"><span>临床事件</span></div>
          <el-timeline v-if="timeline.events.length">
            <el-timeline-item
              v-for="(event, index) in timeline.events"
              :key="index"
              :timestamp="fmt(event.occurredAt)"
              :type="event.type === 'DOC_SIGNED' ? 'success' : 'primary'"
              placement="top"
            >
              <div class="event-card">
                <div class="event-head">
                  <span class="event-title">{{ event.title || event.docType || event.type }}</span>
                  <el-tag size="small" :type="event.status === 'SIGNED' ? 'success' : 'info'">
                    {{ DOC_STATUS[event.status]?.label || event.status || event.type }}
                  </el-tag>
                </div>
                <div class="event-meta">
                  <span>{{ DOC_TYPE[event.docType] || event.docType || '-' }}</span>
                  <span v-if="event.actor">操作人：{{ event.actor }}</span>
                  <span v-if="event.docId" class="mono">doc {{ event.docId }}</span>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="该患者暂无临床事件" :image-size="80" />
        </div>

        <div class="side-col">
          <div class="panel panel-pad">
            <div class="section-title"><span>文书构成</span></div>
            <div v-for="(count, type) in timeline.byDocType" :key="type" class="type-row">
              <span>{{ DOC_TYPE[type] || type }}</span>
              <el-progress
                :percentage="percent(count)"
                :stroke-width="6"
                color="#0f766e"
                :format="() => String(count)"
                style="flex: 1; margin-left: 12px"
              />
            </div>
            <el-empty v-if="!Object.keys(timeline.byDocType || {}).length" description="暂无文书" :image-size="60" />
          </div>

          <div class="panel panel-pad tip">
            <div class="section-title"><span>说明</span></div>
            <p>时间轴数据来自 EMR 文档、签名与业务闭环事件。E3 最小实现按患者维度聚合，不跨患者检索。</p>
          </div>
        </div>
      </div>
    </template>

    <div v-else class="panel panel-pad" style="margin-top: 16px">
      <el-empty description="输入患者 ID 加载时间轴" :image-size="90" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import http from '../../api/http'

const DOC_TYPE = {
  OUTPATIENT_NOTE: '门诊病历',
  ADMISSION_RECORD: '入院记录',
  FIRST_COURSE: '首次病程',
  DISCHARGE_RECORD: '出院记录'
}
const DOC_STATUS = {
  DRAFT: { label: '草稿' },
  SUBMITTED: { label: '待签名' },
  SIGNED: { label: '已签名' },
  ARCHIVED: { label: '已归档' }
}

const patientId = ref('1001')
const timeline = ref(null)
const loading = ref(false)

const docTypeCount = computed(() => Object.keys(timeline.value?.byDocType || {}).length)

function fmt(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function percent(count) {
  const total = timeline.value?.totalDocuments || 0
  if (!total) return 0
  return Math.round((count / total) * 100)
}

async function load() {
  if (!patientId.value) return
  loading.value = true
  try {
    timeline.value = await http.get(`/cdr/patients/${patientId.value}/timeline`)
  } catch {
    timeline.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.timeline-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  margin-top: 16px;
}

.event-card {
  padding: 12px 14px;
  border-radius: 12px;
  background: #fbfdfd;
  border: 1px solid var(--his-line);
}

.event-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.event-title {
  font-size: 13px;
  font-weight: 700;
}

.event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 7px;
  font-size: 11px;
  color: var(--his-muted);
}

.side-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.type-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  padding: 6px 0;
}

.tip p {
  margin: 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--his-muted);
}

@media (max-width: 1100px) {
  .timeline-grid {
    grid-template-columns: 1fr;
  }
}
</style>
