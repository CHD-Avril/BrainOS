<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { chatApi } from '@/features/chat/api'
import type { ChatModel } from '@/features/chat/types'
import { knowledgeApi } from '@/features/knowledge/api'
import type { KnowledgeBase } from '@/features/knowledge/types'
import { requireAuth } from '@/navigation/auth'

const knowledgeRows = ref<KnowledgeBase[]>([])
const knowledgeIndex = ref(0)
const modelIndex = ref(0)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const modelOptions: Array<{ label: string; value: ChatModel }> = [
  { label: 'Qwen', value: 'QWEN' },
  { label: 'DeepSeek', value: 'DEEPSEEK' },
]
const selectedKnowledge = computed(() => knowledgeRows.value[knowledgeIndex.value])

onLoad(() => {
  if (requireAuth()) void loadKnowledge()
})

async function loadKnowledge() {
  loading.value = true
  error.value = ''
  try {
    knowledgeRows.value = (await knowledgeApi.list()).filter(row => row.readyDocumentCount > 0)
  }
  catch (reason) {
    error.value = reason instanceof Error ? reason.message : '知识库加载失败'
  }
  finally {
    loading.value = false
  }
}

function selectKnowledge(event: { detail: { value: string } }) {
  knowledgeIndex.value = Number(event.detail.value)
}

function selectModel(event: { detail: { value: string } }) {
  modelIndex.value = Number(event.detail.value)
}

function goToKnowledge() {
  uni.switchTab({ url: '/pages/knowledge/index' })
}

async function submit() {
  const knowledge = selectedKnowledge.value
  const model = modelOptions[modelIndex.value]
  if (!knowledge || !model || submitting.value) return
  submitting.value = true
  try {
    const session = await chatApi.create(knowledge.id, model.value)
    uni.redirectTo({ url: `/pages/chat/conversation?sessionId=${session.id}` })
  }
  catch (reason) {
    uni.showToast({ title: reason instanceof Error ? reason.message : '创建会话失败', icon: 'none' })
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page create-page">
    <view v-if="loading" class="state muted">正在加载知识库…</view>
    <view v-else-if="error" class="error-panel card">
      <text class="error-text">{{ error }}</text>
      <button class="text-button" @click="loadKnowledge">重试</button>
    </view>
    <view v-else-if="knowledgeRows.length === 0" class="empty card">
      <text class="empty__title">暂无可用于问答的知识库</text>
      <text class="muted">请先上传文档并等待处理完成</text>
      <button class="primary-button" @click="goToKnowledge">前往知识库</button>
    </view>
    <view v-else class="form-card card">
      <view class="field-group">
        <text class="label">知识库</text>
        <picker :range="knowledgeRows" range-key="name" :value="knowledgeIndex" @change="selectKnowledge">
          <view class="picker-field">{{ selectedKnowledge?.name }}</view>
        </picker>
        <text class="muted hint">仅显示至少有一份可用文档的知识库</text>
      </view>
      <view class="field-group">
        <text class="label">AI 模型</text>
        <picker :range="modelOptions" range-key="label" :value="modelIndex" @change="selectModel">
          <view class="picker-field">{{ modelOptions[modelIndex]?.label }}</view>
        </picker>
      </view>
      <button class="primary-button" :loading="submitting" :disabled="submitting" @click="submit">开始对话</button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.create-page { padding-top: 28rpx; }
.state { padding: 96rpx 0; text-align: center; }
.error-panel { display: flex; align-items: center; justify-content: space-between; padding: 28rpx; }
.text-button { margin: 0; color: var(--color-primary); background: transparent; font-size: 26rpx; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 22rpx; padding: 80rpx 36rpx; text-align: center; }
.empty__title { color: var(--color-heading); font-size: 32rpx; font-weight: 650; }
.form-card { display: flex; flex-direction: column; gap: 34rpx; padding: 32rpx; }
.field-group { display: flex; flex-direction: column; gap: 14rpx; }
.label { color: var(--color-heading); font-weight: 650; }
.picker-field { display: flex; align-items: center; box-sizing: border-box; height: 88rpx; padding: 0 22rpx; background: #f8fafc; border: 1rpx solid var(--color-border); border-radius: 14rpx; }
.hint { font-size: 24rpx; }
.primary-button { width: 100%; }
</style>
