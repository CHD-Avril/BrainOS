<script setup lang="ts">
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { chatApi } from '@/features/chat/api'
import { useChatStore } from '@/features/chat/store'
import type { ChatSession } from '@/features/chat/types'
import { knowledgeApi } from '@/features/knowledge/api'
import type { KnowledgeBase } from '@/features/knowledge/types'
import { requireAuth } from '@/navigation/auth'

const store = useChatStore()
const knowledgeRows = ref<KnowledgeBase[]>([])
const pageError = ref('')
const busyId = ref<number | null>(null)
const knowledgeNames = computed(() => new Map(knowledgeRows.value.map(row => [row.id, row.name])))

async function load() {
  pageError.value = ''
  try {
    const [, knowledge] = await Promise.all([store.load(), knowledgeApi.list()])
    knowledgeRows.value = knowledge
  }
  catch (error) {
    pageError.value = error instanceof Error ? error.message : '会话加载失败'
  }
}

onShow(() => {
  if (requireAuth()) void load()
})

onPullDownRefresh(async () => {
  try {
    if (requireAuth()) await load()
  }
  finally {
    uni.stopPullDownRefresh()
  }
})

function openCreate() {
  uni.navigateTo({ url: '/pages/chat/create' })
}

function openConversation(row: ChatSession) {
  uni.navigateTo({ url: `/pages/chat/conversation?sessionId=${row.id}` })
}

async function rename(row: ChatSession) {
  if (busyId.value !== null) return
  const result = await uni.showModal({
    title: '重命名会话',
    editable: true,
    placeholderText: '输入会话标题',
    content: row.title,
  })
  if (!result.confirm) return
  const title = result.content?.trim() || ''
  if (title.length < 1 || title.length > 60) {
    uni.showToast({ title: '标题请输入 1–60 个字符', icon: 'none' })
    return
  }
  busyId.value = row.id
  try {
    await chatApi.rename(row.id, title)
    await store.load()
    uni.showToast({ title: '会话已重命名', icon: 'success' })
  }
  catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '重命名失败', icon: 'none' })
  }
  finally {
    busyId.value = null
  }
}

async function remove(row: ChatSession) {
  if (busyId.value !== null) return
  const result = await uni.showModal({
    title: '删除会话',
    content: `确定删除“${row.title}”及其全部消息吗？`,
    confirmText: '删除',
    confirmColor: '#dc2626',
  })
  if (!result.confirm) return
  busyId.value = row.id
  try {
    await chatApi.remove(row.id)
    await store.load()
    uni.showToast({ title: '会话已删除', icon: 'success' })
  }
  catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '删除失败', icon: 'none' })
  }
  finally {
    busyId.value = null
  }
}
</script>

<template>
  <view class="page chat-page">
    <view class="page-header">
      <view>
        <text class="page-title">AI 问答</text>
        <text class="page-subtitle muted">基于企业知识库开始可信对话</text>
      </view>
      <button class="create-button primary-button" @click="openCreate">新对话</button>
    </view>

    <view v-if="pageError || store.error" class="error-panel card">
      <text class="error-text">{{ pageError || store.error }}</text>
      <button class="text-button" @click="load">重新加载</button>
    </view>

    <view v-if="store.loading && store.rows.length === 0" class="state muted">正在加载会话…</view>
    <view v-else-if="!pageError && !store.error && store.rows.length === 0" class="empty card">
      <text class="empty__title">还没有对话</text>
      <text class="muted">选择一个已有可用文档的知识库开始</text>
      <button class="primary-button" @click="openCreate">新建第一个对话</button>
    </view>

    <view v-else class="session-list">
      <view v-for="row in store.rows" :key="row.id" class="session-card card" @click="openConversation(row)">
        <view class="session-card__header">
          <text class="session-title">{{ row.title }}</text>
          <text class="model">{{ row.chatModel === 'QWEN' ? 'Qwen' : 'DeepSeek' }}</text>
        </view>
        <text class="muted knowledge-name">{{ knowledgeNames.get(row.knowledgeBaseId) || `知识库 #${row.knowledgeBaseId}` }}</text>
        <view class="session-card__footer">
          <button class="card-action" :disabled="busyId !== null" @click.stop="rename(row)">重命名</button>
          <button class="card-action card-action--danger" :loading="busyId === row.id" :disabled="busyId !== null" @click.stop="remove(row)">删除</button>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.chat-page { display: flex; flex-direction: column; gap: 28rpx; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 22rpx; }
.page-title { display: block; color: var(--color-heading); font-size: 40rpx; font-weight: 700; }
.page-subtitle { display: block; margin-top: 8rpx; }
.create-button { flex: none; min-width: 148rpx; margin: 0; font-size: 27rpx; }
.error-panel { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 24rpx; }
.text-button { flex: none; margin: 0; color: var(--color-primary); background: transparent; font-size: 26rpx; }
.state { padding: 96rpx 0; text-align: center; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 22rpx; padding: 80rpx 36rpx; text-align: center; }
.empty__title { color: var(--color-heading); font-size: 34rpx; font-weight: 650; }
.session-list { display: flex; flex-direction: column; gap: 20rpx; }
.session-card { padding: 28rpx; }
.session-card__header { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; }
.session-title { min-width: 0; overflow: hidden; color: var(--color-heading); font-size: 33rpx; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.model { flex: none; padding: 7rpx 14rpx; color: var(--color-primary); background: var(--color-primary-soft); border-radius: 999rpx; font-size: 23rpx; }
.knowledge-name { display: block; margin-top: 12rpx; }
.session-card__footer { display: flex; justify-content: flex-end; gap: 12rpx; margin-top: 20rpx; padding-top: 16rpx; border-top: 1rpx solid var(--color-border); }
.card-action { margin: 0; padding: 0 18rpx; color: var(--color-primary); background: transparent; font-size: 26rpx; line-height: 58rpx; }
.card-action--danger { color: var(--color-danger); }
</style>
