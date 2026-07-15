<script setup lang="ts">
import { Back, Delete, DocumentAdd, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { documentApi, type KnowledgeDocument } from './api'
import DocumentStatusTag from './DocumentStatusTag.vue'
import { knowledgeApi, type KnowledgeBase } from '@/features/knowledge/api'

const route = useRoute()
const router = useRouter()
const knowledgeBaseId = computed(() => Number(route.params.id))
const knowledge = ref<KnowledgeBase | null>(null)
const rows = ref<KnowledgeDocument[]>([])
const loading = ref(true)
const uploading = ref(false)
const error = ref('')
const fileInput = ref<HTMLInputElement>()
const readyDocumentCount = computed(() => rows.value.filter(row => row.status === 'READY').length)
let pollTimer: number | undefined
let refreshRequest: Promise<void> | undefined
let disposed = false

onMounted(loadPage)
onUnmounted(() => {
  disposed = true
  stopPolling()
})

async function loadPage(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [summary] = await Promise.all([knowledgeApi.get(knowledgeBaseId.value), refresh()])
    if (!disposed) knowledge.value = summary
  }
  catch {
    error.value = '文档列表加载失败，请重试'
  }
  finally {
    loading.value = false
  }
}

async function refresh(): Promise<void> {
  if (refreshRequest) return refreshRequest
  stopPolling()
  const request = documentApi.list(knowledgeBaseId.value)
    .then((documents) => {
      if (disposed) return
      rows.value = documents
      if (documents.some(row => row.status === 'PARSING' || row.status === 'INDEXING')) {
        pollTimer = window.setTimeout(() => {
          pollTimer = undefined
          refresh().catch(() => { error.value = '状态刷新失败，请稍后重试' })
        }, 2000)
      }
    })
    .finally(() => {
      if (refreshRequest === request) refreshRequest = undefined
    })
  refreshRequest = request
  return request
}

function stopPolling(): void {
  if (pollTimer !== undefined) window.clearTimeout(pollTimer)
  pollTimer = undefined
}

function selectFile(): void {
  fileInput.value?.click()
}

async function handleFile(file: File): Promise<void> {
  if (uploading.value) return
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!extension || !['pdf', 'docx', 'txt', 'md', 'markdown'].includes(extension)) {
    ElMessage.error('仅支持 PDF、DOCX、TXT 和 Markdown 文档')
    return
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('单个文档不能超过 20MB')
    return
  }
  uploading.value = true
  try {
    await documentApi.upload(knowledgeBaseId.value, file)
    ElMessage.success('文档已上传，正在处理')
    await refresh()
  }
  catch {
    ElMessage.error('上传失败，文件可能重复或格式不正确')
  }
  finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

function onFileChange(event: Event): void {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) void handleFile(file)
}

function onDrop(event: DragEvent): void {
  const file = event.dataTransfer?.files[0]
  if (file) void handleFile(file)
}

async function retryDocument(document: KnowledgeDocument): Promise<void> {
  try {
    await documentApi.retry(knowledgeBaseId.value, document.id)
    ElMessage.success('已重新提交处理')
    await refresh()
  }
  catch {
    ElMessage.error('重试失败，请稍后再试')
  }
}

async function removeDocument(document: KnowledgeDocument): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除“${document.originalName}”吗？`,
      '确认删除文档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await documentApi.remove(knowledgeBaseId.value, document.id)
    ElMessage.success('文档已删除')
    await refresh()
  }
  catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error('删除失败，请重试')
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <section class="document-page" aria-labelledby="document-heading">
    <button class="back-link" type="button" @click="router.push('/knowledge-bases')">
      <el-icon><Back /></el-icon>
      返回知识库
    </button>

    <header class="document-header">
      <div>
        <h2 id="document-heading">{{ knowledge?.name || '文档管理' }}</h2>
        <p>{{ knowledge?.description || '上传企业资料，系统将自动解析并建立索引。' }}</p>
      </div>
      <div v-if="knowledge" class="document-summary">
        <strong>{{ readyDocumentCount }}</strong>
        <span>/ {{ rows.length }} 个文档可用</span>
      </div>
    </header>

    <div
      class="upload-zone"
      :class="{ 'is-uploading': uploading }"
      @dragover.prevent
      @drop.prevent="onDrop"
    >
      <div class="upload-zone__icon"><el-icon><DocumentAdd /></el-icon></div>
      <div>
        <h3>{{ uploading ? '正在上传…' : '上传知识文档' }}</h3>
        <p>支持 PDF、DOCX、TXT、Markdown，单个文件不超过 20MB</p>
      </div>
      <el-button type="primary" :loading="uploading" :disabled="uploading" @click="selectFile">
        选择文档
      </el-button>
      <input
        ref="fileInput"
        class="visually-hidden"
        aria-label="上传文档"
        type="file"
        accept=".pdf,.docx,.txt,.md,.markdown"
        @change="onFileChange"
      >
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <template #default><el-button text type="primary" @click="loadPage">重新加载</el-button></template>
    </el-alert>

    <div class="document-table">
      <div class="document-table__heading">
        <h3>文档列表</h3>
        <el-button data-test="refresh-documents" :icon="RefreshRight" text @click="loadPage">刷新</el-button>
      </div>
      <el-table v-loading="loading" :data="rows" empty-text="暂无文档">
        <el-table-column label="文档名称" min-width="240">
          <template #default="{ row }">
            <div class="file-cell">
              <strong>{{ row.originalName }}</strong>
              <span>{{ formatSize(row.sizeBytes) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="230">
          <template #default="{ row }">
            <DocumentStatusTag :status="row.status" />
            <p v-if="row.status === 'FAILED'" data-test="failure-reason" class="failure-reason">
              {{ row.failureReason || '处理失败，请重试' }}
            </p>
          </template>
        </el-table-column>
        <el-table-column label="切片数" width="100">
          <template #default="{ row }">{{ row.status === 'READY' ? row.chunkCount : '—' }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'FAILED'"
              data-test="retry"
              type="primary"
              text
              @click="retryDocument(row)"
            >
              重试
            </el-button>
            <el-button type="danger" text :icon="Delete" @click="removeDocument(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<style scoped>
.document-page {
  min-width: 0;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 16px;
  padding: 0;
  color: var(--color-muted);
  background: transparent;
  border: 0;
}

.back-link:hover {
  color: var(--color-primary);
}

.document-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.document-header h2 {
  margin: 0;
  color: var(--color-heading);
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
}

.document-header p {
  margin: 6px 0 0;
  color: var(--color-muted);
}

.document-summary {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--color-muted);
}

.document-summary strong {
  color: var(--color-primary);
  font-size: 28px;
  line-height: 36px;
  font-variant-numeric: tabular-nums;
}

.upload-zone {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 112px;
  margin-bottom: 20px;
  padding: 20px 24px;
  background: var(--color-surface);
  border: 1px dashed #93c5fd;
  border-radius: var(--radius-md);
  transition: border-color var(--motion-fast), box-shadow var(--motion-fast);
}

.upload-zone:hover {
  border-color: var(--color-primary);
  box-shadow: 0 6px 18px rgb(37 99 235 / 8%);
}

.upload-zone.is-uploading {
  opacity: 0.75;
}

.upload-zone__icon {
  flex: 0 0 auto;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
  background: var(--color-primary-subtle);
  border-radius: var(--radius-md);
  font-size: 22px;
}

.upload-zone > div:nth-child(2) {
  min-width: 0;
  flex: 1;
}

.upload-zone h3,
.document-table__heading h3 {
  margin: 0;
  color: var(--color-heading);
  font-size: 18px;
  line-height: 28px;
  font-weight: 600;
}

.upload-zone p {
  margin: 2px 0 0;
  color: var(--color-muted);
  font-size: 12px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

.document-table {
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.document-table__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.file-cell {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.file-cell strong {
  overflow: hidden;
  color: var(--color-heading);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-cell span,
.failure-reason {
  color: var(--color-muted);
  font-size: 12px;
  line-height: 18px;
}

.failure-reason {
  max-width: 210px;
  margin: 5px 0 0;
  color: var(--color-danger);
}
</style>
