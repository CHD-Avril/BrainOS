import type { Pinia } from 'pinia'
import {
  createRouter,
  createWebHistory,
  type Router,
  type RouterHistory,
} from 'vue-router'
import LoginView from '@/features/auth/LoginView.vue'
import ChatView from '@/features/chat/ChatView.vue'
import { useAuthStore } from '@/features/auth/store'
import DocumentListView from '@/features/document/DocumentListView.vue'
import KnowledgeListView from '@/features/knowledge/KnowledgeListView.vue'
import AppShell from '@/layouts/AppShell.vue'
import PlaceholderView from '@/layouts/PlaceholderView.vue'
import { pinia } from '@/pinia'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    description?: string
    requiresAuth?: boolean
    adminOnly?: boolean
  }
}

export function createBrainOsRouter(history: RouterHistory, store: Pinia): Router {
  const router = createRouter({
    history,
    routes: [
      { path: '/login', name: 'login', component: LoginView, meta: { title: '登录' } },
      {
        path: '/',
        component: AppShell,
        meta: { requiresAuth: true },
        children: [
          { path: '', redirect: { name: 'dashboard' } },
          {
            path: 'dashboard', name: 'dashboard', component: PlaceholderView,
            meta: { title: '工作台', description: '工作台功能将在后续阶段接入。' },
          },
          {
            path: 'knowledge-bases', name: 'knowledge-bases', component: KnowledgeListView,
            meta: { title: '知识库' },
          },
          {
            path: 'knowledge-bases/:id/documents', name: 'knowledge-documents', component: DocumentListView,
            meta: { title: '文档管理' },
          },
          {
            path: 'chat', name: 'chat', component: ChatView,
            meta: { title: 'AI 问答' },
          },
          {
            path: 'admin/users', name: 'admin-users', component: PlaceholderView,
            meta: { title: '用户管理', description: '用户管理功能将在后续阶段接入。', adminOnly: true },
          },
          {
            path: 'admin/audit-logs', name: 'audit-logs', component: PlaceholderView,
            meta: { title: '操作日志', description: '操作日志功能将在后续阶段接入。', adminOnly: true },
          },
        ],
      },
      { path: '/:pathMatch(.*)*', redirect: { name: 'dashboard' } },
    ],
  })

  router.beforeEach((to) => {
    const auth = useAuthStore(store)
    if (to.name === 'login' && auth.isAuthenticated) return { name: 'dashboard' }
    if (to.meta.requiresAuth && !auth.isAuthenticated) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    if (to.meta.adminOnly && !auth.isAdmin) return { name: 'dashboard' }
    return true
  })

  return router
}

export const router = createBrainOsRouter(createWebHistory(), pinia)
