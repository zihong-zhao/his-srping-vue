import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth } from '../store/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

http.interceptors.request.use((config) => {
  if (auth.state.token) {
    config.headers.Authorization = `Bearer ${auth.state.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.success === 'boolean') {
      if (!body.success) {
        ElMessage.error(body.message || '请求失败')
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body.data
    }
    return body
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络异常'
    if (status === 401) {
      auth.clear()
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default http
