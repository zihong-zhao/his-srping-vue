import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '../store/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('../views/DashboardView.vue') },
      { path: 'documents', name: 'documents', component: () => import('../views/emr/DocumentListView.vue') },
      { path: 'documents/:id', name: 'document-detail', component: () => import('../views/emr/DocumentEditView.vue') },
      { path: 'qc', name: 'qc', component: () => import('../views/emr/QcView.vue') },
      { path: 'timeline', name: 'timeline', component: () => import('../views/cdr/TimelineView.vue') },
      { path: 'archive', name: 'archive', component: () => import('../views/emr/ArchiveView.vue') },
      { path: 'loops', name: 'loops', component: () => import('../views/loop/LoopView.vue') },
      { path: 'cdss', name: 'cdss', component: () => import('../views/cdss/CdssView.vue') },
      { path: 'critical-values', name: 'critical-values', component: () => import('../views/cdss/CriticalValueView.vue') },
      { path: 'exam-items', name: 'exam-items', component: () => import('../views/cdss/ExamItemView.vue') },
      { path: 'pathways', name: 'pathways', component: () => import('../views/cdss/PathwayView.vue') },
      {
        path: 'audit',
        name: 'audit',
        component: () => import('../views/audit/AuditView.vue'),
        meta: { roles: ['ADMIN'] }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!auth.state.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !auth.hasRole(...to.meta.roles)) {
    return { path: '/' }
  }
  return true
})

export default router
