<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './store'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const form = reactive({ username: '', password: '' })
const errors = reactive({ username: '', password: '' })
const submitting = ref(false)
const authenticationError = ref('')

async function submit(): Promise<void> {
  if (submitting.value) return

  const username = form.username.trim()
  errors.username = username ? '' : '请输入用户名'
  errors.password = form.password ? '' : '请输入密码'
  authenticationError.value = ''
  if (errors.username || errors.password) return

  submitting.value = true
  try {
    await auth.login(username, form.password)
    const redirect = route.query.redirect
    await router.replace(
      typeof redirect === 'string' && redirect.startsWith('/') && !redirect.startsWith('//')
        ? redirect
        : { name: 'dashboard' },
    )
  } catch {
    authenticationError.value = '用户名或密码错误，请重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel" aria-labelledby="login-title">
      <div class="login-brand">BrainOS</div>
      <h1 id="login-title">登录 BrainOS</h1>

      <el-alert
        v-if="authenticationError"
        class="login-error"
        :title="authenticationError"
        type="error"
        :closable="false"
        show-icon
      />

      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" :error="errors.username">
          <el-input
            v-model="form.username"
            aria-label="用户名"
            autocomplete="username"
            :disabled="submitting"
          />
        </el-form-item>
        <el-form-item label="密码" :error="errors.password">
          <el-input
            v-model="form.password"
            aria-label="密码"
            type="password"
            autocomplete="current-password"
            show-password
            :disabled="submitting"
          />
        </el-form-item>
        <el-button
          class="login-submit"
          native-type="submit"
          type="primary"
          :loading="submitting"
          :disabled="submitting"
        >
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px 24px;
  background: var(--color-background);
}

.login-panel {
  width: min(400px, calc(100vw - 48px));
  padding: 32px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.login-brand {
  margin-bottom: 24px;
  color: var(--color-primary);
  font-size: 18px;
  line-height: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

h1 {
  margin: 0;
  color: var(--color-heading);
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
}

.login-error {
  margin-bottom: 20px;
}

.login-submit {
  width: 100%;
  min-height: 40px;
  margin-top: 4px;
  font-size: 14px;
  line-height: 20px;
  font-weight: 500;
}
</style>
