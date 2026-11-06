<template>
  <div class="page">
    <div class="hero">
      <div class="hero-left">
        <div class="hero-kicker">{{ todayText }}</div>
        <h1 class="hero-title">{{ greeting }}，{{ auth.state.user?.displayName || '同事' }}</h1>
        <p class="hero-sub">当前角色：{{ roleLabel }}。今天可以从病历、质控、闭环或临床决策任一入口开始。</p>
        <div class="hero-actions">
          <el-button type="primary" @click="$router.push('/documents')">打开病历中心</el-button>
          <el-button plain @click="$router.push('/timeline')">查看患者时间轴</el-button>
        </div>
      </div>
      <div class="hero-right">
        <div class="hero-stat">
          <div class="hero-num">{{ stats.pathwayTemplates }}</div>
          <div class="hero-label">临床路径模板</div>
        </div>
        <div class="hero-stat">
          <div class="hero-num">{{ stats.examItems }}</div>
          <div class="hero-label">检验检查项目</div>
        </div>
        <div class="hero-stat">
          <div class="hero-num">{{ stats.drugs }}</div>
          <div class="hero-label">药品知识库</div>
        </div>
      </div>
    </div>

    <div class="stat-grid">
      <div class="stat-card" style="--his-primary-soft: #e6fffa">
        <div class="stat-label">待确认危急值</div>
        <div class="stat-value">{{ stats.pendingCritical }}</div>
        <div class="stat-note">上报后需医生确认并记录处理措施</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #fff7e6">
        <div class="stat-label">未关闭质控缺陷</div>
        <div class="stat-value">{{ stats.openQcIssues }}</div>
        <div class="stat-note">质控缺陷闭环情况</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #eef2ff">
        <div class="stat-label">在途闭环单据</div>
        <div class="stat-value">{{ stats.activeLoops }}</div>
        <div class="stat-note">医嘱 / 处方 / 检验 / 检查</div>
      </div>
      <div class="stat-card" style="--his-primary-soft: #f0fdf4">
        <div class="stat-label">已入径患者</div>
        <div class="stat-value">{{ stats.activePathways }}</div>
        <div class="stat-note">临床路径在管实例</div>
      </div>
    </div>

    <div class="two-col">
      <div class="panel panel-pad">
        <div class="section-title">
          <span>四条闭环执行率</span>
          <el-button link type="primary" @click="$router.push('/loops')">进入闭环</el-button>
        </div>
        <div v-if="loopStats.length" class="loop-list">
          <div v-for="item in loopStats" :key="item.loopType" class="loop-row">
            <div class="loop-meta">
              <span class="loop-name">{{ loopTypeLabel[item.loopType] || item.loopType }}</span>
              <span class="loop-count">{{ item.closed }} / {{ item.total }}</span>
            </div>
            <el-progress
              :percentage="Number(item.rate || 0)"
              :stroke-width="8"
              :color="progressColor(item.rate)"
              :format="(p) => `${p}%`"
            />
          </div>
        </div>
        <div v-else class="empty-block">
          <el-empty description="暂无闭环统计数据（管理员 / 质控可见）" :image-size="72" />
        </div>
      </div>

      <div class="panel panel-pad">
        <div class="section-title">
          <span>快捷入口</span>
        </div>
        <div class="quick-grid">
          <button v-for="entry in quickEntries" :key="entry.path" class="quick-item" @click="$router.push(entry.path)">
            <el-icon class="quick-icon"><component :is="entry.icon" /></el-icon>
            <div>
              <div class="quick-name">{{ entry.name }}</div>
              <div class="quick-desc">{{ entry.desc }}</div>
            </div>
          </button>
        </div>
      </div>
    </div>

    <div class="panel panel-pad compliance">
      <div class="section-title">
        <span>评级闭环覆盖</span>
        <span class="text-muted" style="font-size: 12px; font-weight: 400">已实现的最小能力</span>
      </div>
      <div class="comp-grid">
        <div v-for="item in compliance" :key="item.code" class="comp-item">
          <div class="comp-code">{{ item.code }}</div>
          <div class="comp-name">{{ item.name }}</div>
          <div class="comp-desc">{{ item.desc }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import http from '../api/http'
import { auth, ROLE_LABEL } from '../store/auth'

const stats = reactive({
  pathwayTemplates: 0,
  examItems: 0,
  drugs: 0,
  pendingCritical: 0,
  openQcIssues: 0,
  activeLoops: 0,
  activePathways: 0
})
const loopStats = ref([])

const roleLabel = computed(() => ROLE_LABEL[auth.state.user?.role] || '访客')
const todayText = computed(() =>
  new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })
)
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const loopTypeLabel = { MED_ORDER: '医嘱闭环', PRESCRIPTION: '处方闭环', LAB: '检验闭环', EXAM: '检查闭环' }

const quickEntries = [
  { path: '/documents', name: '病历中心', desc: '创建、编辑、提交、签名与归档', icon: 'Document' },
  { path: '/qc', name: '质控管理', desc: '规则、缺陷与整改闭环', icon: 'CircleCheck' },
  { path: '/cdss', name: '合理用药', desc: '药品知识库与处方审查', icon: 'FirstAidKit' },
  { path: '/critical-values', name: '危急值', desc: '上报、确认、处理与关闭', icon: 'WarningFilled' },
  { path: '/exam-items', name: '检验检查知识库', desc: '适应症、标本与危急值阈值', icon: 'Search' },
  { path: '/pathways', name: '临床路径', desc: '入径、节点执行与变异', icon: 'Guide' },
  { path: '/archive', name: '病案管理', desc: '借阅、复印与病案首页', icon: 'FolderOpened' },
  { path: '/loops', name: '四条闭环', desc: '医嘱 / 处方 / 检验 / 检查', icon: 'Refresh' }
]

const compliance = [
  { code: 'E1', name: '结构化病历', desc: '模板、章节、版本与签名' },
  { code: 'E2', name: '质控', desc: '规则引擎、缺陷整改' },
  { code: 'E3', name: 'CDR', desc: '患者全流程时间轴' },
  { code: 'E4', name: 'CDSS', desc: '用药、危急值、知识库、路径' },
  { code: 'E5', name: '闭环', desc: '四条业务链路留痕' },
  { code: 'E6', name: '签名审计', desc: '签名验真与操作审计' },
  { code: 'E7', name: '归档封存', desc: '归档、封存、借阅复印' }
]

function progressColor(rate) {
  const value = Number(rate || 0)
  if (value >= 90) return '#0f766e'
  if (value >= 60) return '#0ea5e9'
  return '#f59e0b'
}

onMounted(async () => {
  const results = await Promise.allSettled([
    http.get('/e4/pathways'),
    http.get('/e4/exam-items', { params: { limit: 200 } }),
    http.get('/e4/drugs', { params: { limit: 200 } }),
    http.get('/e4/critical-values', { params: { status: 'PENDING', limit: 100 } }),
    http.get('/emr/qc/issues', { params: { status: 'OPEN', limit: 100 } }),
    http.get('/e5/loops/stats'),
    http.get('/e4/pathways/instances', { params: { status: 'ACTIVE', limit: 100 } })
  ])

  const [pathways, examItems, drugs, critical, qcIssues, loops, instances] = results
  if (pathways.status === 'fulfilled') stats.pathwayTemplates = pathways.value.length
  if (examItems.status === 'fulfilled') stats.examItems = examItems.value.length
  if (drugs.status === 'fulfilled') stats.drugs = drugs.value.length
  if (critical.status === 'fulfilled') stats.pendingCritical = critical.value.length
  if (qcIssues.status === 'fulfilled') stats.openQcIssues = qcIssues.value.length
  if (loops.status === 'fulfilled') {
    loopStats.value = loops.value
    stats.activeLoops = loops.value.reduce((sum, item) => sum + Math.max(0, item.total - item.closed), 0)
  }
  if (instances.status === 'fulfilled') stats.activePathways = instances.value.length
})
</script>

<style scoped>
.hero {
  display: flex;
  justify-content: space-between;
  gap: 22px;
  padding: 28px 32px;
  border-radius: 18px;
  color: #eafffb;
  background:
    radial-gradient(600px 260px at 8% 10%, rgba(45, 212, 191, 0.25), transparent 65%),
    linear-gradient(120deg, #0b3b3c, #0a4a49 55%, #0e5b57);
  box-shadow: 0 18px 40px rgba(11, 59, 60, 0.2);
  margin-bottom: 20px;
}

.hero-kicker {
  font-size: 12px;
  color: #8fc2bc;
  letter-spacing: 0.6px;
}

.hero-title {
  margin: 10px 0 8px;
  font-size: 28px;
  letter-spacing: 0.4px;
}

.hero-sub {
  margin: 0;
  color: #a9cfc9;
  font-size: 13px;
  line-height: 1.8;
  max-width: 560px;
}

.hero-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}

.hero-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.hero-stat {
  min-width: 100px;
  padding: 16px 18px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(94, 234, 212, 0.2);
  backdrop-filter: blur(6px);
}

.hero-num {
  font-size: 26px;
  font-weight: 700;
}

.hero-label {
  font-size: 11px;
  color: #9cc7c1;
  margin-top: 4px;
}

.two-col {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.loop-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0 2px;
}

.loop-meta {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 6px;
}

.loop-name {
  font-weight: 600;
}

.loop-count {
  color: var(--his-muted);
  font-size: 12px;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.quick-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  text-align: left;
  padding: 13px 14px;
  border-radius: 12px;
  border: 1px solid var(--his-line);
  background: #fbfdfd;
  cursor: pointer;
  transition: all 0.18s ease;
}

.quick-item:hover {
  border-color: #5eead4;
  background: #f0fdfa;
  transform: translateY(-1px);
}

.quick-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  color: #0f766e;
  background: #e6fffa;
  flex: 0 0 32px;
}

.quick-name {
  font-size: 13px;
  font-weight: 600;
}

.quick-desc {
  font-size: 11px;
  color: var(--his-muted);
  margin-top: 3px;
  line-height: 1.5;
}

.compliance {
  margin-bottom: 6px;
}

.comp-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
}

.comp-item {
  padding: 14px;
  border-radius: 12px;
  background: #f8fbfb;
  border: 1px solid var(--his-line);
}

.comp-code {
  font-size: 11px;
  font-weight: 800;
  color: #0f766e;
  letter-spacing: 1px;
}

.comp-name {
  font-size: 13px;
  font-weight: 600;
  margin-top: 6px;
}

.comp-desc {
  font-size: 11px;
  color: var(--his-muted);
  margin-top: 4px;
}

@media (max-width: 1100px) {
  .two-col {
    grid-template-columns: 1fr;
  }
  .hero {
    flex-direction: column;
  }
  .hero-right {
    justify-content: flex-start;
  }
}
</style>
