<script setup lang="ts">
import { onHide, onLoad, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { documentApi } from '@/features/document/api'
import { hasPendingDocuments, validateDocumentFile } from '@/features/document/controller'
import type { DocumentStatus, KnowledgeDocument } from '@/features/document/types'
import { documentUploader } from '@/features/document/upload'
import { knowledgeApi } from '@/features/knowledge/api'
import type { KnowledgeBase } from '@/features/knowledge/types'
import { requireAuth } from '@/navigation/auth'

const knowledgeBaseId = ref(0)
const knowledge = ref<KnowledgeBase | null>(null)
const rows = ref<KnowledgeDocument[]>([])
const loading = ref(false)
const error = ref('')
const uploading = ref(false)
const retryingId = ref<number | null>(null)
const deletingId = ref<number | null>(null)
let visible = false
let pollTimer: ReturnType<typeof setTimeout> | undefined

const statusLabels: Record<DocumentStatus, string> = {
  PARSING: '解析中',
  INDEXING: '索引中',
  READY: '可用',
  FAILED: '失败',
}

onLoad((options) => {
  if (!requireAuth()) return
  const parsed = Number(options?.knowledgeBaseId)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    error.value = '知识库参数无效'
    return
  }
  knowledgeBaseId.value = parsed
  void refresh()
})

onShow(() => {
  visible = true
  if (knowledgeBaseId.value && !loading.value && (rows.value.length > 0 || error.value)) void refresh()
})

onHide(stopPolling)
onUnload(stopPolling)

onPullDownRefresh(async () => {
  try {
    await refresh()
  }
  finally {
    uni.stopPullDownRefresh()
  }
})

function stopPolling() {
  visible = false
  clearPollTimer()
}

function clearPollTimer() {
  if (pollTimer !== undefined) {
    clearTimeout(pollTimer)
    pollTimer = undefined
  }
}

function schedulePolling() {
  clearPollTimer()
  if (!visible || !hasPendingDocuments(rows.value)) return
  pollTimer = setTimeout(() => {
    pollTimer = undefined
    void refresh()
  }, 2000)
}

async function refresh() {
  if (!knowledgeBaseId.value) return
  clearPollTimer()
  loading.value = rows.value.length === 0
  error.value = ''
  try {
    const [nextKnowledge, nextRows] = await Promise.all([
      knowledgeApi.get(knowledgeBaseId.value),
      documentApi.list(knowledgeBaseId.value),
    ])
    knowledge.value = nextKnowledge
    rows.value = nextRows
  }
  catch (reason) {
    error.value = reason instanceof Error ? reason.message : '文档加载失败'
  }
  finally {
    loading.value = false
    schedulePolling()
  }
}

async function chooseAndUpload() {
  if (uploading.value) return
  let selection: UniApp.ChooseMessageFileSuccessCallbackResult
  try {
    selection = await new Promise((resolve, reject) => {
      uni.chooseMessageFile({
        count: 1,
        type: 'file',
        extension: ['pdf', 'docx', 'txt', 'md', 'markdown'],
        success: resolve,
        fail: reject,
      })
    })
  }
  catch {
    return
  }
  const file = selection.tempFiles[0]
  if (!file) return
  const validation = validateDocumentFile({ name: file.name, size: file.size })
  if (validation) {
    uni.showToast({ title: validation, icon: 'none' })
    return
  }
  uploading.value = true
  try {
    await documentUploader.upload(knowledgeBaseId.value, file.path)
    uni.showToast({ title: '上传成功，正在处理', icon: 'success' })
    await refresh()
  }
  catch (reason) {
    uni.showToast({ title: reason instanceof Error ? reason.message : '上传失败，请重试', icon: 'none' })
  }
  finally {
    uploading.value = false
  }
}

async function retry(row: KnowledgeDocument) {
  if (retryingId.value !== null) return
  retryingId.value = row.id
  try {
    await documentApi.retry(knowledgeBaseId.value, row.id)
    uni.showToast({ title: '已重新开始处理', icon: 'success' })
    await refresh()
  }
  catch (reason) {
    uni.showToast({ title: reason instanceof Error ? reason.message : '重试失败', icon: 'none' })
  }
  finally {
    retryingId.value = null
  }
}

async function remove(row: KnowledgeDocument) {
  if (deletingId.value !== null) return
  const result = await uni.showModal({
    title: '删除文档',
    content: `确定删除“${row.originalName}”及其索引数据吗？`,
    confirmText: '删除',
    confirmColor: '#dc2626',
  })
  if (!result.confirm) return
  deletingId.value = row.id
  try {
    await documentApi.remove(knowledgeBaseId.value, row.id)
    uni.showToast({ title: '文档已删除', icon: 'success' })
    await refresh()
  }
  catch (reason) {
    uni.showToast({ title: reason instanceof Error ? reason.message : '删除失败', icon: 'none' })
  }
  finally {
    deletingId.value = null
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <view class="page documents-page">
    <view class="page-header">
      <view>
        <text class="page-title">{{ knowledge?.name || '文档管理' }}</text>
        <text class="page-subtitle muted">支持 PDF、DOCX、TXT、MD，单个文件不超过 20MB</text>
      </view>
      <button class="upload-button primary-button" :loading="uploading" :disabled="uploading" @click="chooseAndUpload">
        上传
      </button>
    </view>

    <view v-if="error" class="error-panel card">
      <text class="error-text">{{ error }}</text>
      <button class="text-button" @click="refresh">重新加载</button>
    </view>

    <view v-if="loading && rows.length === 0" class="state muted">正在加载文档…</view>
    <view v-else-if="!error && rows.length === 0" class="empty card">
      <text class="empty__title">还没有文档</text>
      <text class="muted">从微信会话中选择文件上传</text>
      <button class="primary-button" :loading="uploading" @click="chooseAndUpload">上传第一个文档</button>
    </view>

    <view v-else class="document-list">
      <view v-for="row in rows" :key="row.id" class="document-card card">
        <view class="document-card__header">
          <view class="document-card__identity">
            <text class="document-name">{{ row.originalName }}</text>
            <text class="muted metadata">{{ formatSize(row.sizeBytes) }} · {{ row.chunkCount }} 个片段</text>
          </view>
          <text class="status" :class="`status--${row.status.toLowerCase()}`">{{ statusLabels[row.status] }}</text>
        </view>
        <text v-if="row.failureReason" class="failure error-text">{{ row.failureReason }}</text>
        <view class="document-card__footer">
          <button
            v-if="row.status === 'FAILED'"
            class="card-action"
            :loading="retryingId === row.id"
            :disabled="retryingId !== null"
            @click="retry(row)"
          >重试</button>
          <button
            class="card-action card-action--danger"
            :loading="deletingId === row.id"
            :disabled="deletingId !== null"
            @click="remove(row)"
          >删除</button>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.documents-page { display: flex; flex-direction: column; gap: 26rpx; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 22rpx; }
.page-title { display: block; color: var(--color-heading); font-size: 38rpx; font-weight: 700; }
.page-subtitle { display: block; margin-top: 8rpx; font-size: 24rpx; }
.upload-button { flex: none; min-width: 128rpx; margin: 0; font-size: 27rpx; }
.error-panel { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; padding: 24rpx; }
.text-button { flex: none; margin: 0; color: var(--color-primary); background: transparent; font-size: 26rpx; }
.state { padding: 96rpx 0; text-align: center; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 22rpx; padding: 80rpx 36rpx; text-align: center; }
.empty__title { color: var(--color-heading); font-size: 34rpx; font-weight: 650; }
.document-list { display: flex; flex-direction: column; gap: 20rpx; }
.document-card { padding: 26rpx; }
.document-card__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20rpx; }
.document-card__identity { min-width: 0; flex: 1; }
.document-name { display: block; overflow: hidden; color: var(--color-heading); font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.metadata { display: block; margin-top: 8rpx; font-size: 24rpx; }
.status { flex: none; padding: 7rpx 14rpx; border-radius: 999rpx; font-size: 23rpx; }
.status--parsing, .status--indexing { color: var(--color-warning); background: #fffbeb; }
.status--ready { color: var(--color-success); background: #f0fdf4; }
.status--failed { color: var(--color-danger); background: #fef2f2; }
.failure { display: block; margin-top: 18rpx; padding: 16rpx; background: #fef2f2; border-radius: 10rpx; font-size: 24rpx; }
.document-card__footer { display: flex; justify-content: flex-end; gap: 12rpx; margin-top: 20rpx; }
.card-action { margin: 0; padding: 0 18rpx; color: var(--color-primary); background: transparent; font-size: 26rpx; line-height: 58rpx; }
.card-action--danger { color: var(--color-danger); }
</style>
