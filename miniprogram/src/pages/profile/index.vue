<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed } from 'vue'
import { APP_ENV } from '@/config/env'
import { useAuthStore } from '@/features/auth/store'
import { requireAuth } from '@/navigation/auth'

const auth = useAuthStore()
const user = computed(() => auth.session?.user)

onShow(() => requireAuth())

async function logout() {
  const result = await uni.showModal({
    title: '退出登录',
    content: '确定要退出当前 BrainOS 账号吗？',
    confirmText: '退出',
    confirmColor: '#dc2626',
  })
  if (!result.confirm) return
  await auth.logout()
  uni.reLaunch({ url: '/pages/login/index' })
}
</script>

<template>
  <view class="page profile-page">
    <view v-if="user" class="identity card">
      <view class="avatar">{{ user.displayName.slice(0, 1) }}</view>
      <view class="identity__content">
        <text class="identity__name">{{ user.displayName }}</text>
        <text class="muted">@{{ user.username }}</text>
      </view>
      <text class="role">{{ user.role === 'ADMIN' ? '管理员' : '用户' }}</text>
    </view>

    <view class="details card">
      <view class="details__row">
        <text>运行环境</text>
        <text class="muted">{{ APP_ENV }}</text>
      </view>
      <view class="details__row">
        <text>客户端</text>
        <text class="muted">微信小程序</text>
      </view>
    </view>

    <button class="logout-button" @click="logout">退出登录</button>
  </view>
</template>

<style scoped lang="scss">
.profile-page { display: flex; flex-direction: column; gap: 28rpx; }
.identity { display: flex; align-items: center; gap: 24rpx; padding: 32rpx; }
.avatar { display: flex; align-items: center; justify-content: center; width: 88rpx; height: 88rpx; color: #fff; background: var(--color-primary); border-radius: 24rpx; font-size: 38rpx; font-weight: 700; }
.identity__content { display: flex; flex: 1; flex-direction: column; }
.identity__name { color: var(--color-heading); font-size: 34rpx; font-weight: 650; }
.role { padding: 8rpx 14rpx; color: var(--color-primary); background: var(--color-primary-soft); border-radius: 999rpx; font-size: 24rpx; }
.details { padding: 8rpx 28rpx; }
.details__row { display: flex; justify-content: space-between; padding: 28rpx 0; border-bottom: 1rpx solid var(--color-border); }
.details__row:last-child { border-bottom: 0; }
.logout-button { width: 100%; margin-top: 16rpx; color: var(--color-danger); background: #fff; border: 1rpx solid #fecaca; border-radius: 14rpx; }
</style>
