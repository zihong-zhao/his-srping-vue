<template>
  <div class="login-page">
    <section class="brand-pane">
      <div class="brand-top">
        <div class="logo">H</div>
        <div>
          <div class="sys-name">HIS 临床工作站</div>
          <div class="sys-sub">电子病历评级闭环 · E1–E7</div>
        </div>
      </div>

      <div class="brand-copy">
        <h1>让每一次诊疗<br />都留下完整痕迹</h1>
        <p>结构化病历、质控、CDR、闭环、CDSS、归档封存，在一个工作台内完成。</p>
      </div>

      <div class="cap-grid">
        <div class="cap">
          <div class="cap-no">E1</div>
          <div class="cap-name">结构化病历</div>
        </div>
        <div class="cap">
          <div class="cap-no">E2</div>
          <div class="cap-name">质控与整改</div>
        </div>
        <div class="cap">
          <div class="cap-no">E3</div>
          <div class="cap-name">临床数据整合</div>
        </div>
        <div class="cap">
          <div class="cap-no">E4</div>
          <div class="cap-name">临床决策支持</div>
        </div>
        <div class="cap">
          <div class="cap-no">E5</div>
          <div class="cap-name">四条业务闭环</div>
        </div>
        <div class="cap">
          <div class="cap-no">E6</div>
          <div class="cap-name">签名与审计</div>
        </div>
        <div class="cap">
          <div class="cap-no">E7</div>
          <div class="cap-name">归档与封存</div>
        </div>
      </div>

      <div class="brand-foot">本地机房部署 · JWT 鉴权 · 全程审计留痕</div>
    </section>

    <section class="form-pane">
      <div class="form-card">
        <div class="form-head">
          <div class="form-kicker">CLINICAL WORKSTATION</div>
          <h2>登录工作台</h2>
          <p>使用院内账号登录，所有操作将写入审计日志。</p>
        </div>

        <el-form :model="form" label-position="top" @submit.prevent="submit">
          <el-form-item label="账号">
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" autocomplete="username">
              <template #prefix><el-icon><User /></el-icon></template>
            </el-input>
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
              @keyup.enter="submit"
            >
              <template #prefix><el-icon><Lock /></el-icon></template>
            </el-input>
          </el-form-item>

          <el-button class="login-btn" size="large" type="primary" :loading="loading" @click="submit">
            进入工作台
          </el-button>
        </el-form>

        <div class="demo-block">
          <div class="demo-title">演示账号快速填充</div>
          <div class="demo-list">
            <button v-for="account in accounts" :key="account.username" class="demo-chip" @click="fill(account)">
              <span class="demo-role">{{ account.label }}</span>
              <span class="demo-name">{{ account.username }}</span>
            </button>
          </div>
        </div>

        <div class="form-foot">HIS EMR Evaluation Baseline · v0.1.0</div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/http'
import { auth } from '../store/auth'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const form = reactive({ username: 'doctor1', password: 'doctor123' })

const accounts = [
  { label: '管理员', username: 'admin', password: 'admin123' },
  { label: '医生', username: 'doctor1', password: 'doctor123' },
  { label: '质控', username: 'qc1', password: 'qc123' }
]

function fill(account) {
  form.username = account.username
  form.password = account.password
}

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const data = await http.post('/auth/login', form)
    auth.setSession(data)
    ElMessage.success(`欢迎，${data.displayName || data.username}`)
    router.replace(route.query.redirect || '/')
  } catch {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, 0.85fr);
  background: #f2f6f7;
}

.brand-pane {
  position: relative;
  padding: 46px 54px;
  display: flex;
  flex-direction: column;
  color: #e6fffb;
  background:
    radial-gradient(900px 480px at 12% 8%, rgba(45, 212, 191, 0.22), transparent 60%),
    radial-gradient(700px 420px at 88% 92%, rgba(14, 116, 144, 0.34), transparent 62%),
    linear-gradient(150deg, #0b3b3c 0%, #08302f 55%, #072427 100%);
  overflow: hidden;
}

.brand-pane::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.035) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(120deg, black, transparent 80%);
  pointer-events: none;
}

.brand-top {
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  z-index: 1;
}

.logo {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 22px;
  color: #083334;
  background: linear-gradient(135deg, #5eead4, #2dd4bf);
  box-shadow: 0 10px 24px rgba(45, 212, 191, 0.3);
}

.sys-name {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.sys-sub {
  font-size: 12px;
  color: #8fc2bc;
  margin-top: 4px;
}

.brand-copy {
  position: relative;
  z-index: 1;
  margin-top: auto;
  margin-bottom: auto;
  padding: 42px 0;
  max-width: 520px;
}

.brand-copy h1 {
  font-size: clamp(30px, 3.4vw, 46px);
  line-height: 1.24;
  margin: 0 0 18px;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.brand-copy p {
  margin: 0;
  color: #a9cfc9;
  line-height: 1.9;
  font-size: 14px;
  max-width: 420px;
}

.cap-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  max-width: 620px;
}

.cap {
  padding: 12px 12px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.055);
  border: 1px solid rgba(94, 234, 212, 0.16);
  backdrop-filter: blur(6px);
}

.cap-no {
  font-size: 11px;
  color: #5eead4;
  font-weight: 700;
  letter-spacing: 1px;
}

.cap-name {
  font-size: 12px;
  color: #d8f3ef;
  margin-top: 4px;
}

.brand-foot {
  position: relative;
  z-index: 1;
  margin-top: 30px;
  font-size: 11px;
  color: #6f9c97;
  letter-spacing: 1px;
}

.form-pane {
  display: grid;
  place-items: center;
  padding: 36px 30px;
}

.form-card {
  width: 100%;
  max-width: 390px;
  padding: 32px 30px 24px;
  border-radius: 22px;
  background: #ffffff;
  border: 1px solid #e3ebed;
  box-shadow: 0 26px 60px rgba(15, 46, 51, 0.12);
}

.form-kicker {
  font-size: 10px;
  letter-spacing: 2.4px;
  color: #0f766e;
  font-weight: 700;
}

.form-head h2 {
  margin: 8px 0 6px;
  font-size: 25px;
  letter-spacing: 0.3px;
}

.form-head p {
  margin: 0 0 22px;
  font-size: 12px;
  color: var(--his-muted);
  line-height: 1.7;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
  height: 44px;
  font-weight: 700;
  letter-spacing: 1px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0f766e, #0e7490);
  border: none;
}

.demo-block {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px dashed #dbe7e8;
}

.demo-title {
  font-size: 11px;
  color: var(--his-muted);
  letter-spacing: 0.8px;
  margin-bottom: 10px;
}

.demo-list {
  display: flex;
  gap: 8px;
}

.demo-chip {
  flex: 1;
  border: 1px solid #dbe7e8;
  background: #f8fbfb;
  border-radius: 10px;
  padding: 8px 6px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 3px;
  transition: all 0.18s ease;
}

.demo-chip:hover {
  border-color: #5eead4;
  background: #f0fdfa;
  transform: translateY(-1px);
}

.demo-role {
  font-size: 11px;
  color: #0f766e;
  font-weight: 700;
}

.demo-name {
  font-size: 11px;
  color: var(--his-muted);
}

.form-foot {
  margin-top: 20px;
  text-align: center;
  font-size: 10px;
  letter-spacing: 1.4px;
  color: #a8b8ba;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }
  .brand-pane {
    display: none;
  }
  .form-pane {
    padding: 24px 16px;
  }
}
</style>
