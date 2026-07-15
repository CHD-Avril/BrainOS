<script setup lang="ts">
import { reactive } from 'vue'
import { useAuthStore } from '@/features/auth/store'

const auth = useAuthStore()
const form = reactive({ username: '', password: '' })

async function submit() {
  const username = form.username.trim()
  if (!username || !form.password) {
    auth.error = '请输入用户名和密码'
    return
  }
  try {
    await auth.login(username, form.password)
    uni.switchTab({ url: '/pages/knowledge/index' })
  }
  catch {
    // The store exposes the safe error message.
  }
}
</script>

<template>
  <view class="login-page">
    <view class="brand">
      <text class="brand__mark">B</text>
      <text class="brand__name">BrainOS</text>
    </view>
    <view class="login-card card">
      <text class="title">登录企业知识库</text>
      <text class="subtitle">使用现有 BrainOS 账号继续</text>
      <input v-model="form.username" class="field" maxlength="64" placeholder="用户名" autocomplete="username">
      <input v-model="form.password" class="field" password maxlength="128" placeholder="密码" autocomplete="current-password" @confirm="submit">
      <text v-if="auth.error" class="error-text">{{ auth.error }}</text>
      <button class="primary-button" :loading="auth.submitting" :disabled="auth.submitting" @click="submit">
        登录
      </button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.login-page { min-height: 100vh; padding: 120rpx 48rpx 48rpx; background: linear-gradient(160deg, #eff6ff, #f8fafc 48%); }
.brand { display: flex; align-items: center; justify-content: center; gap: 16rpx; margin-bottom: 48rpx; }
.brand__mark { display: grid; place-items: center; width: 72rpx; height: 72rpx; color: #fff; background: var(--color-primary); border-radius: 18rpx; font-size: 40rpx; font-weight: 700; }
.brand__name { color: var(--color-heading); font-size: 44rpx; font-weight: 700; }
.login-card { display: flex; flex-direction: column; gap: 24rpx; padding: 40rpx; }
.title { color: var(--color-heading); font-size: 36rpx; font-weight: 650; }
.subtitle { margin-top: -16rpx; color: var(--color-muted); }
.field { height: 88rpx; padding: 0 24rpx; background: #f8fafc; border: 1rpx solid var(--color-border); border-radius: 14rpx; }
.primary-button { width: 100%; margin-top: 8rpx; }
</style>
