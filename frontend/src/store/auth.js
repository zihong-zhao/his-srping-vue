import { reactive } from 'vue'

const TOKEN_KEY = 'his_token'
const USER_KEY = 'his_user'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

const state = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: readUser()
})

function setSession(payload) {
  state.token = payload.token || ''
  state.user = {
    userId: payload.userId,
    username: payload.username,
    displayName: payload.displayName,
    role: payload.role,
    expiresAt: payload.expiresAt
  }
  localStorage.setItem(TOKEN_KEY, state.token)
  localStorage.setItem(USER_KEY, JSON.stringify(state.user))
}

function clear() {
  state.token = ''
  state.user = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

function hasRole(...roles) {
  if (!roles || roles.length === 0) return true
  return roles.includes(state.user?.role)
}

function isPrivileged() {
  return hasRole('ADMIN', 'QC')
}

export const auth = { state, setSession, clear, hasRole, isPrivileged }

export const ROLE_LABEL = {
  ADMIN: '系统管理员',
  DOCTOR: '临床医生',
  QC: '质控人员'
}
