<script setup lang="ts">
import {
  ChatDotRound,
  Collection,
  Monitor,
  SwitchButton,
  Tickets,
  User,
} from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/features/auth/store'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const mainNavigation = [
  { label: '工作台', path: '/dashboard', icon: Monitor },
  { label: '知识库', path: '/knowledge-bases', icon: Collection },
  { label: 'AI 问答', path: '/chat', icon: ChatDotRound },
]

const adminNavigation = [
  { label: '用户管理', path: '/admin/users', icon: User },
  { label: '操作日志', path: '/admin/audit-logs', icon: Tickets },
]

const navigation = computed(() => (
  auth.isAdmin ? [...mainNavigation, ...adminNavigation] : mainNavigation
))
const pageTitle = computed(() => String(route.meta.title ?? 'BrainOS'))
const activeNavigation = computed(() => (
  route.path.startsWith('/knowledge-bases') ? '/knowledge-bases' : route.path
))

async function logout(): Promise<void> {
  await auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <aside class="app-shell__sidebar">
      <div class="app-shell__brand">BrainOS</div>
      <nav aria-label="主导航">
        <el-menu router :default-active="activeNavigation">
          <el-menu-item v-for="item in navigation" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </nav>
    </aside>

    <header class="app-shell__topbar">
      <h1>{{ pageTitle }}</h1>
      <div class="app-shell__user">
        <span class="app-shell__username">{{ auth.user?.username }}</span>
        <el-button aria-label="退出登录" text @click="logout">
          <el-icon><SwitchButton /></el-icon>
          <span>退出</span>
        </el-button>
      </div>
    </header>

    <main class="app-shell__main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--color-background);
}

.app-shell__sidebar {
  position: fixed;
  z-index: 20;
  inset: 0 auto 0 0;
  width: var(--sidebar-width);
  background: var(--color-surface);
  border-right: 1px solid var(--color-border);
}

.app-shell__brand {
  height: var(--topbar-height);
  display: flex;
  align-items: center;
  padding: 0 24px;
  color: var(--color-primary);
  border-bottom: 1px solid var(--color-border);
  font-size: 18px;
  line-height: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.app-shell__sidebar :deep(.el-menu) {
  padding: 12px;
  border-right: 0;
}

.app-shell__sidebar :deep(.el-menu-item) {
  height: 44px;
  margin-bottom: 4px;
  padding: 0 12px !important;
  border-radius: var(--radius-sm);
  color: var(--color-text);
  font-size: 14px;
  line-height: 20px;
  font-weight: 500;
}

.app-shell__sidebar :deep(.el-menu-item .el-icon) {
  width: 18px;
  margin-right: 12px;
  color: var(--color-muted);
  font-size: 18px;
}

.app-shell__sidebar :deep(.el-menu-item:hover) {
  color: var(--color-heading);
  background: var(--color-background);
}

.app-shell__sidebar :deep(.el-menu-item.is-active) {
  color: var(--color-primary);
  background: var(--color-primary-subtle);
  font-weight: 600;
}

.app-shell__sidebar :deep(.el-menu-item.is-active .el-icon) {
  color: var(--color-primary);
}

.app-shell__topbar {
  position: fixed;
  z-index: 10;
  top: 0;
  right: 0;
  left: var(--sidebar-width);
  height: var(--topbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}

.app-shell__topbar h1 {
  margin: 0;
  color: var(--color-heading);
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
}

.app-shell__user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-shell__username {
  max-width: 180px;
  overflow: hidden;
  color: var(--color-text);
  font-size: 14px;
  line-height: 22px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-shell__user :deep(.el-button) {
  font-size: 14px;
  line-height: 20px;
  font-weight: 500;
}

.app-shell__main {
  min-width: 0;
  min-height: 100vh;
  margin-left: var(--sidebar-width);
  padding: calc(var(--topbar-height) + 24px) 24px 24px;
}

@media (max-width: 1100px) {
  .app-shell__topbar {
    padding-inline: 20px;
  }

  .app-shell__main {
    padding: calc(var(--topbar-height) + 20px) 20px 20px;
  }
}
</style>
