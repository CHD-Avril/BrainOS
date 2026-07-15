<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { knowledgeApi } from '@/features/knowledge/api'
import type { KnowledgeBase } from '@/features/knowledge/types'
import { requireAuth } from '@/navigation/auth'

const row = ref<KnowledgeBase | null>(null)
const loading = ref(false)
const error = ref('')
let knowledgeBaseId = 0

onLoad((options) => {
  if (!requireAuth()) return
  const parsed = Number(options?.id)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    error.value = '知识库参数无效'
    return
  }
  knowledgeBaseId = parsed
  void load()
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    row.value = await knowledgeApi.get(knowledgeBaseId)
  }
  catch (reason) {
    error.value = reason instanceof Error ? reason.message : '知识库加载失败'
  }
  finally {
    loading.value = false
  }
}

function openDocuments() {
  uni.navigateTo({ url: `/pages/documents/index?knowledgeBaseId=${knowledgeBaseId}` })
}

function edit() {
  uni.navigateTo({ url: `/pages/knowledge/editor?id=${knowledgeBaseId}` })
}
</script>

<template>
  <view class="page detail-page">
    <view v-if="loading" class="state muted">正在加载…</view>
    <view v-else-if="error" class="error-panel card">
      <text class="error-text">{{ error }}</text>
      <button class="text-button" @click="load">重试</button>
    </view>
    <template v-else-if="row">
      <view class="summary card">
        <text class="title">{{ row.name }}</text>
        <text class="description muted">{{ row.description || '暂无描述' }}</text>
        <view class="stats">
          <view class="stat">
            <text class="stat__value">{{ row.documentCount }}</text>
            <text class="muted">全部文档</text>
          </view>
          <view class="stat">
            <text class="stat__value stat__value--success">{{ row.readyDocumentCount }}</text>
            <text class="muted">已就绪</text>
          </view>
        </view>
      </view>
      <button class="primary-button" @click="openDocuments">管理文档</button>
      <button class="secondary-button" @click="edit">编辑知识库</button>
    </template>
  </view>
</template>

<style scoped lang="scss">
.detail-page { display: flex; flex-direction: column; gap: 24rpx; }
.state { padding: 96rpx 0; text-align: center; }
.error-panel { display: flex; align-items: center; justify-content: space-between; padding: 28rpx; }
.text-button { margin: 0; color: var(--color-primary); background: transparent; font-size: 26rpx; }
.summary { padding: 34rpx; }
.title { display: block; color: var(--color-heading); font-size: 40rpx; font-weight: 700; }
.description { display: block; margin-top: 14rpx; }
.stats { display: flex; gap: 20rpx; margin-top: 34rpx; padding-top: 28rpx; border-top: 1rpx solid var(--color-border); }
.stat { display: flex; flex: 1; flex-direction: column; align-items: center; }
.stat__value { color: var(--color-heading); font-size: 42rpx; font-weight: 700; }
.stat__value--success { color: var(--color-success); }
.primary-button, .secondary-button { width: 100%; }
.secondary-button { color: var(--color-primary); background: #fff; border: 1rpx solid #bfdbfe; border-radius: 14rpx; }
</style>
