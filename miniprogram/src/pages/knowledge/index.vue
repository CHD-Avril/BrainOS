<script setup lang="ts">
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { knowledgeApi } from '@/features/knowledge/api'
import { useKnowledgeStore } from '@/features/knowledge/store'
import type { KnowledgeBase } from '@/features/knowledge/types'
import { requireAuth } from '@/navigation/auth'

const store = useKnowledgeStore()

async function load() {
  try {
    await store.load()
  }
  catch {
    // The store keeps existing rows and exposes a safe message.
  }
}

onShow(() => {
  if (requireAuth()) void load()
})

onPullDownRefresh(async () => {
  try {
    if (requireAuth()) await store.load()
  }
  catch {
    // The error state is rendered below.
  }
  finally {
    uni.stopPullDownRefresh()
  }
})

function openCreate() {
  uni.navigateTo({ url: '/pages/knowledge/editor' })
}

function openDetail(row: KnowledgeBase) {
  uni.navigateTo({ url: `/pages/knowledge/detail?id=${row.id}` })
}

function openEdit(row: KnowledgeBase) {
  uni.navigateTo({ url: `/pages/knowledge/editor?id=${row.id}` })
}

async function remove(row: KnowledgeBase) {
  const result = await uni.showModal({
    title: '确认删除知识库',
    content: `删除“${row.name}”后，其文档和向量数据也会清理。`,
    confirmText: '删除',
    confirmColor: '#dc2626',
  })
  if (!result.confirm) return
  try {
    await knowledgeApi.remove(row.id)
    await store.load()
    uni.showToast({ title: '知识库已删除', icon: 'success' })
  }
  catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '删除失败，请重试', icon: 'none' })
  }
}
</script>

<template>
  <view class="page knowledge-page">
    <view class="page-header">
      <view>
        <text class="page-title">企业知识库</text>
        <text class="page-subtitle">集中管理文档，为 AI 问答提供可信资料</text>
      </view>
      <button class="create-button primary-button" @click="openCreate">新建</button>
    </view>

    <view v-if="store.error" class="error-panel card">
      <text class="error-text">{{ store.error }}</text>
      <button class="text-button" @click="load">重新加载</button>
    </view>

    <view v-if="store.loading && store.rows.length === 0" class="state muted">正在加载知识库…</view>
    <view v-else-if="!store.error && store.rows.length === 0" class="empty card">
      <text class="empty__title">还没有知识库</text>
      <text class="muted">新建知识库后即可上传企业文档</text>
      <button class="primary-button" @click="openCreate">新建第一个知识库</button>
    </view>

    <view v-else class="knowledge-list">
      <view v-for="row in store.rows" :key="row.id" class="knowledge-card card" @click="openDetail(row)">
        <view class="knowledge-card__header">
          <text class="knowledge-card__title">{{ row.name }}</text>
          <text class="readiness">{{ row.readyDocumentCount }}/{{ row.documentCount }} 就绪</text>
        </view>
        <text class="description muted">{{ row.description || '暂无描述' }}</text>
        <view class="knowledge-card__footer">
          <button class="card-action" @click.stop="openDetail(row)">详情</button>
          <button class="card-action" @click.stop="openEdit(row)">编辑</button>
          <button class="card-action card-action--danger" @click.stop="remove(row)">删除</button>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.knowledge-page { display: flex; flex-direction: column; gap: 28rpx; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24rpx; }
.page-title { display: block; color: var(--color-heading); font-size: 40rpx; font-weight: 700; }
.page-subtitle { display: block; margin-top: 8rpx; color: var(--color-muted); font-size: 26rpx; }
.create-button { flex: none; min-width: 128rpx; margin: 0; font-size: 27rpx; }
.error-panel { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 24rpx; }
.text-button { flex: none; margin: 0; padding: 0 16rpx; color: var(--color-primary); background: transparent; font-size: 26rpx; }
.state { padding: 96rpx 0; text-align: center; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 24rpx; padding: 80rpx 40rpx; text-align: center; }
.empty__title { color: var(--color-heading); font-size: 34rpx; font-weight: 650; }
.knowledge-list { display: flex; flex-direction: column; gap: 22rpx; }
.knowledge-card { padding: 28rpx; }
.knowledge-card__header { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; }
.knowledge-card__title { min-width: 0; color: var(--color-heading); font-size: 34rpx; font-weight: 650; }
.readiness { flex: none; padding: 7rpx 14rpx; color: var(--color-success); background: #f0fdf4; border-radius: 999rpx; font-size: 23rpx; }
.description { display: block; min-height: 42rpx; margin-top: 14rpx; }
.knowledge-card__footer { display: flex; justify-content: flex-end; gap: 12rpx; margin-top: 24rpx; padding-top: 18rpx; border-top: 1rpx solid var(--color-border); }
.card-action { margin: 0; padding: 0 18rpx; color: var(--color-primary); background: transparent; font-size: 26rpx; line-height: 58rpx; }
.card-action--danger { color: var(--color-danger); }
</style>
