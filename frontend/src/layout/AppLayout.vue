<template>
  <div class="shell">
    <aside class="side">
      <div class="brand">
        <div class="brand-mark">H</div>
        <div>
          <div class="brand-name">HIS 临床工作站</div>
          <div class="brand-sub">电子病历评级闭环</div>
        </div>
      </div>

      <el-menu
        class="side-menu"
        :default-active="activePath"
        router
        background-color="transparent"
        text-color="#b8d4d1"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <span>工作台</span>
        </el-menu-item>

        <el-menu-item-group title="临床业务">
          <el-menu-item index="/documents">
            <el-icon><Document /></el-icon>
            <span>病历中心</span>
          </el-menu-item>
          <el-menu-item index="/qc">
            <el-icon><CircleCheck /></el-icon>
            <span>质控管理</span>
          </el-menu-item>
          <el-menu-item index="/timeline">
            <el-icon><Clock /></el-icon>
            <span>患者时间轴</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="病案与闭环">
          <el-menu-item index="/archive">
            <el-icon><FolderOpened /></el-icon>
            <span>病案管理</span>
          </el-menu-item>
          <el-menu-item index="/loops">
            <el-icon><Refresh /></el-icon>
            <span>四条闭环</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="临床决策 E4">
          <el-menu-item index="/cdss">
            <el-icon><FirstAidKit /></el-icon>
            <span>合理用药</span>
          </el-menu-item>
          <el-menu-item index="/critical-values">
            <el-icon><WarningFilled /></el-icon>
            <span>危急值</span>
          </el-menu-item>
          <el-menu-item index="/exam-items">
            <el-icon><Search /></el-icon>
            <span>检验检查知识库</span>
          </el-menu-item>
          <el-menu-item index="/pathways">
            <el-icon><Guide /></el-icon>
            <span>临床路径</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group v-if="auth.hasRole('ADMIN')" title="系统">
          <el-menu-item index="/audit">
            <el-icon><Tickets /></el-icon>
            <span>审计日志</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>

      <div class="side-foot">
        <span class="dot" />
        <span>评级闭环 E1–E7 · 最小实现</span>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <div class="top-left">
          <span class="top-title">{{ currentTitle }}</span>
          <span class="top-sep">/</span>
          <span class="top-hint">数据来源于院内 HIS，操作全程留痕</span>
        </div>
        <div class="top-right">
          <el-tag effect="plain" round class="role-tag">{{ roleLabel }}</el-tag>
          <el-dropdown trigger="click" @command="onCommand">
            <div class="user-chip">
              <div class="avatar">{{ initial }}</div>
              <div class="user-meta">
                <div class="user-name">{{ auth.state.user?.displayName || '未登录' }}</div>
                <div class="user-sub">{{ auth.state.user?.username }}</div>
              </div>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <div class="content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auth, ROLE_LABEL } from '../store/auth'

const route = useRoute()
const router = useRouter()

const activePath = computed(() => {
  if (route.path.startsWith('/documents')) return '/documents'
  return route.path
})

const TITLES = {
  '/': '工作台',
  '/documents': '病历中心',
  '/qc': '质控管理',
  '/timeline': '患者时间轴',
  '/archive': '病案管理',
  '/loops': '四条闭环',
  '/cdss': '合理用药',
  '/critical-values': '危急值',
  '/exam-items': '检验检查知识库',
  '/pathways': '临床路径',
  '/audit': '审计日志'
}

const currentTitle = computed(() => {
  if (route.path.startsWith('/documents/')) return '病历详情'
  return TITLES[route.path] || '工作台'
})

const roleLabel = computed(() => ROLE_LABEL[auth.state.user?.role] || '访客')
const initial = computed(() => (auth.state.user?.displayName || 'H').slice(0, 1))

async function onCommand(command) {
  if (command !== 'logout') return
  try {
    await ElMessageBox.confirm('确认退出当前账号？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    auth.clear()
    ElMessage.success('已退出登录')
    router.replace('/login')
  } catch {
    /* 用户取消 */
  }
}
</script>

<style scoped>
.shell {
  display: flex;
  height: 100%;
  min-height: 100vh;
}

.side {
  width: 236px;
  flex: 0 0 236px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0b3b3c 0%, #0a2e31 100%);
  color: #d7ede9;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 20px;
  font-weight: 800;
  color: #083334;
  background: linear-gradient(135deg, #5eead4, #2dd4bf);
  box-shadow: 0 6px 18px rgba(45, 212, 191, 0.28);
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.3px;
}

.brand-sub {
  font-size: 11px;
  color: #8fb5b0;
  margin-top: 3px;
}

.side-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
  padding: 8px 10px 12px;
}

.side-menu :deep(.el-menu-item-group__title) {
  padding: 14px 10px 6px;
  font-size: 11px;
  letter-spacing: 1.5px;
  color: #6f9691;
}

.side-menu :deep(.el-menu-item) {
  height: 42px;
  line-height: 42px;
  border-radius: 10px;
  margin: 2px 0;
  font-size: 13px;
}

.side-menu :deep(.el-menu-item:hover) {
  background: rgba(94, 234, 212, 0.1) !important;
  color: #ffffff !important;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(45, 212, 191, 0.24), rgba(45, 212, 191, 0.06));
  color: #ffffff !important;
  box-shadow: inset 3px 0 0 #2dd4bf;
}

.side-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  font-size: 11px;
  color: #7fa7a2;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 0 4px rgba(52, 211, 153, 0.16);
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 62px;
  flex: 0 0 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 26px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--his-line);
  position: sticky;
  top: 0;
  z-index: 10;
}

.top-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.top-title {
  font-size: 16px;
  font-weight: 700;
}

.top-sep {
  color: #b7c8c8;
}

.top-hint {
  font-size: 12px;
  color: var(--his-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.top-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.role-tag {
  border-color: #bfe8e2;
  color: #0f766e;
  background: #f0fdfa;
  font-weight: 600;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 8px;
  border-radius: 10px;
}

.user-chip:hover {
  background: #f1f7f6;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, #0f766e, #0e7490);
}

.user-meta {
  line-height: 1.2;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
}

.user-sub {
  font-size: 11px;
  color: var(--his-muted);
  margin-top: 3px;
}

.content {
  flex: 1;
  min-height: 0;
}

@media (max-width: 900px) {
  .side {
    width: 190px;
    flex-basis: 190px;
  }
  .top-hint {
    display: none;
  }
  .topbar {
    padding: 0 14px;
  }
}
</style>
