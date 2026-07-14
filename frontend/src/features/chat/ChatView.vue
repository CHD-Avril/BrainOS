<script setup lang="ts">
import {
  ChatLineRound,
  Delete,
  EditPen,
  Plus,
  Promotion,
  RefreshRight,
  VideoPause,
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { chatApi, type ChatMessage, type ChatModel, type ChatSession } from './api'
import MarkdownContent from './MarkdownContent.vue'
import { knowledgeApi, type KnowledgeBase } from '@/features/knowledge/api'

interface DisplayMessage extends Omit<ChatMessage, 'id' | 'createdAt'> {
  id: number | string
  createdAt?: string
  streaming?: boolean
  error?: boolean
  stopped?: boolean
  retryQuestion?: string
}

const knowledgeBases = ref<KnowledgeBase[]>([])
const sessions = ref<ChatSession[]>([])
const activeSessionId = ref<number | null>(null)
const messages = ref<DisplayMessage[]>([])
const selectedKnowledgeBaseId = ref<number | null>(null)
const selectedModel = ref<ChatModel>('QWEN')
const question = ref('')
const loading = ref(true)
const loadingHistory = ref(false)
const creating = ref(false)
const streaming = ref(false)
const error = ref('')
const messageViewport = ref<HTMLElement>()
let streamController: AbortController | undefined
let activeAssistant: DisplayMessage | undefined
let historyRequestId = 0

const activeSession = computed(() => sessions.value.find(item => item.id === activeSessionId.value))
const readyKnowledgeBases = computed(() => knowledgeBases.value.filter(item => item.readyDocumentCount > 0))
const canSend = computed(() => (
  question.value.trim().length > 0
  && !streaming.value
  && !creating.value
  && !loadingHistory.value
))

onMounted(loadWorkspace)
onUnmounted(() => streamController?.abort())

async function loadWorkspace(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [bases, items] = await Promise.all([knowledgeApi.list(), chatApi.list()])
    knowledgeBases.value = bases
    sessions.value = items
    selectedKnowledgeBaseId.value = readyKnowledgeBases.value[0]?.id ?? bases[0]?.id ?? null
    if (items[0]) await openSession(items[0])
  }
  catch {
    error.value = '聊天工作区加载失败，请重试'
  }
  finally {
    loading.value = false
  }
}

async function refreshSessions(preferredId?: number): Promise<void> {
  sessions.value = await chatApi.list()
  if (preferredId) activeSessionId.value = preferredId
}

async function openSession(session: ChatSession): Promise<void> {
  if (streaming.value) return
  const requestId = ++historyRequestId
  activeSessionId.value = session.id
  messages.value = []
  selectedKnowledgeBaseId.value = session.knowledgeBaseId
  selectedModel.value = session.chatModel
  loadingHistory.value = true
  try {
    const detail = await chatApi.get(session.id)
    if (requestId !== historyRequestId) return
    messages.value = detail.messages
    await scrollToBottom()
  }
  catch {
    if (requestId === historyRequestId) ElMessage.error('会话记录加载失败')
  }
  finally {
    if (requestId === historyRequestId) loadingHistory.value = false
  }
}

async function createSession(): Promise<ChatSession | null> {
  if (streaming.value || creating.value) return null
  if (!selectedKnowledgeBaseId.value) {
    ElMessage.warning('请先选择知识库')
    return null
  }
  creating.value = true
  try {
    const created = await chatApi.create(selectedKnowledgeBaseId.value, selectedModel.value)
    await refreshSessions(created.id)
    messages.value = []
    return created
  }
  catch {
    ElMessage.error('新建会话失败')
    return null
  }
  finally {
    creating.value = false
  }
}

async function renameSession(session: ChatSession): Promise<void> {
  if (streaming.value) return
  try {
    const result = await ElMessageBox.prompt('输入新的会话名称', '重命名会话', {
      inputValue: session.title,
      inputPattern: /^.{1,60}$/u,
      inputErrorMessage: '名称长度需为 1–60 个字符',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
    })
    await chatApi.rename(session.id, result.value.trim())
    await refreshSessions(session.id)
  }
  catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error('重命名失败')
  }
}

async function removeSession(session: ChatSession): Promise<void> {
  if (streaming.value) return
  try {
    await ElMessageBox.confirm(`确定删除“${session.title}”及其聊天记录吗？`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await chatApi.remove(session.id)
    const wasActive = activeSessionId.value === session.id
    await refreshSessions()
    if (wasActive) {
      activeSessionId.value = null
      messages.value = []
      if (sessions.value[0]) await openSession(sessions.value[0])
    }
  }
  catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error('删除失败')
  }
}

async function sendQuestion(): Promise<void> {
  const content = question.value.trim()
  if (!content || content.length > 1000 || streaming.value || creating.value || loadingHistory.value) return
  let session = activeSession.value
  if (!session) session = await createSession() ?? undefined
  if (!session) return

  historyRequestId++
  loadingHistory.value = false
  question.value = ''
  messages.value.push({
    id: `user-${Date.now()}`,
    sessionId: session.id,
    role: 'USER',
    content,
    citations: [],
  })
  const assistant: DisplayMessage = {
    id: `assistant-${Date.now()}`,
    sessionId: session.id,
    role: 'ASSISTANT',
    content: '',
    citations: [],
    streaming: true,
    retryQuestion: content,
  }
  messages.value.push(assistant)
  streaming.value = true
  activeAssistant = assistant
  streamController = new AbortController()
  await scrollToBottom()
  try {
    await chatApi.stream(session.id, content, (event) => {
      if (event.type === 'delta' && event.content) assistant.content += event.content
      if (event.type === 'citations') {
        assistant.citations = event.citations
      }
      if (event.type === 'error') {
        assistant.error = true
        assistant.content = event.message || '回答生成失败，请稍后重试'
      }
      if (event.type === 'done' || event.type === 'error') assistant.streaming = false
      void scrollToBottom()
    }, streamController.signal)
    await refreshSessions(session.id)
  }
  catch (reason) {
    if ((reason as Error).name === 'AbortError') {
      assistant.stopped = true
      assistant.streaming = false
      if (!assistant.content) assistant.content = '已停止生成'
    }
    else {
      assistant.error = true
      assistant.streaming = false
      assistant.content = '连接中断，请重试本次提问'
    }
  }
  finally {
    streaming.value = false
    streamController = undefined
    if (activeAssistant === assistant) activeAssistant = undefined
    await scrollToBottom()
  }
}

function stopStreaming(): void {
  if (!streaming.value) return
  streamController?.abort()
}

async function retryAnswer(message: DisplayMessage): Promise<void> {
  if (!message.retryQuestion || streaming.value || creating.value || loadingHistory.value) return
  question.value = message.retryQuestion
  await sendQuestion()
}

function handleComposerKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void sendQuestion()
  }
}

function resetDraft(): void {
  if (streaming.value || creating.value) return
  historyRequestId++
  loadingHistory.value = false
  activeSessionId.value = null
  messages.value = []
}

async function scrollToBottom(): Promise<void> {
  await nextTick()
  if (messageViewport.value) messageViewport.value.scrollTop = messageViewport.value.scrollHeight
}

function modelLabel(model: ChatModel): string {
  return model === 'QWEN' ? '通义千问' : 'DeepSeek'
}

function knowledgeName(id: number): string {
  return knowledgeBases.value.find(item => item.id === id)?.name ?? '知识库'
}
</script>

<template>
  <section v-loading="loading" class="chat-page" aria-label="AI 企业知识问答">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <template #default><el-button text type="primary" @click="loadWorkspace">重新加载</el-button></template>
    </el-alert>

    <div class="chat-toolbar">
      <div>
        <h2>AI 企业知识助手</h2>
        <p>仅依据已入库文档回答，并附上可追溯的引用来源。</p>
      </div>
      <div class="chat-toolbar__controls">
        <el-select
          v-model="selectedKnowledgeBaseId"
          :disabled="streaming || creating || loadingHistory"
          placeholder="选择知识库"
          aria-label="选择知识库"
          @change="resetDraft"
        >
          <el-option
            v-for="base in knowledgeBases"
            :key="base.id"
            :label="`${base.name} · ${base.readyDocumentCount} 文档可用`"
            :value="base.id"
            :disabled="base.readyDocumentCount === 0"
          />
        </el-select>
        <el-select
          v-model="selectedModel"
          :disabled="streaming || creating || loadingHistory"
          aria-label="选择大模型"
          @change="resetDraft"
        >
          <el-option label="通义千问" value="QWEN" />
          <el-option label="DeepSeek" value="DEEPSEEK" />
        </el-select>
        <el-button
          type="primary"
          :icon="Plus"
          :loading="creating"
          :disabled="streaming || loadingHistory"
          @click="createSession"
        >
          新建对话
        </el-button>
      </div>
    </div>

    <div class="chat-workspace">
      <aside class="session-panel" aria-label="会话列表">
        <div class="panel-heading">
          <strong>历史会话</strong>
          <span>{{ sessions.length }}</span>
        </div>
        <div v-if="sessions.length" class="session-list">
          <div
            v-for="session in sessions"
            :key="session.id"
            class="session-item"
            :class="{ 'is-active': session.id === activeSessionId }"
            role="button"
            :tabindex="streaming ? -1 : 0"
            :aria-disabled="streaming"
            @click="openSession(session)"
            @keydown.enter="openSession(session)"
            @keydown.space.prevent="openSession(session)"
          >
            <span class="session-item__icon"><el-icon><ChatLineRound /></el-icon></span>
            <span class="session-item__content">
              <strong>{{ session.title }}</strong>
              <small>{{ knowledgeName(session.knowledgeBaseId) }} · {{ modelLabel(session.chatModel) }}</small>
            </span>
            <span class="session-item__actions">
              <el-button aria-label="重命名会话" text :disabled="streaming" :icon="EditPen" @click.stop="renameSession(session)" />
              <el-button aria-label="删除会话" text type="danger" :disabled="streaming" :icon="Delete" @click.stop="removeSession(session)" />
            </span>
          </div>
        </div>
        <div v-else class="session-empty">
          <el-icon><ChatLineRound /></el-icon>
          <p>暂无会话</p>
          <span>提出第一个问题即可开始</span>
        </div>
      </aside>

      <main class="conversation-panel">
        <div ref="messageViewport" v-loading="loadingHistory" class="message-viewport" aria-live="polite">
          <div v-if="!messages.length" class="conversation-empty">
            <div class="conversation-empty__icon"><el-icon><ChatLineRound /></el-icon></div>
            <h3>从企业资料中快速找到答案</h3>
            <p v-if="readyKnowledgeBases.length">可询问制度、产品、项目或流程，答案会标注文档依据。</p>
            <p v-else>请先在知识库中上传文档，并等待索引完成。</p>
          </div>
          <article
            v-for="message in messages"
            :key="message.id"
            class="message-row"
            :class="[`is-${message.role.toLowerCase()}`, { 'is-error': message.error, 'is-stopped': message.stopped }]"
          >
            <div class="message-avatar">{{ message.role === 'USER' ? '我' : 'AI' }}</div>
            <div class="message-body">
              <div v-if="message.role === 'USER'" class="user-message">{{ message.content }}</div>
              <div v-else class="assistant-message">
                <MarkdownContent v-if="message.content" :content="message.content" />
                <span v-if="message.streaming && !message.content" class="typing-indicator">
                  <i /><i /><i />
                </span>
                <div v-if="message.citations.length" class="citation-disclosures">
                  <details v-for="(citation, index) in message.citations" :key="citation.chunkId">
                    <summary>
                      <span class="source-number">来源 {{ index + 1 }}</span>
                      <strong>{{ citation.fileName }}</strong>
                      <small>
                        {{ citation.pageNumber ? `第 ${citation.pageNumber} 页` : `切片 ${citation.chunkIndex + 1}` }}
                        · {{ Math.round(citation.score * 100) }}%
                      </small>
                    </summary>
                    <p>{{ citation.snippet }}</p>
                  </details>
                </div>
                <div v-if="message.error || message.stopped" class="message-recovery">
                  <span>{{ message.stopped ? '生成已停止，未完整回答不会保存。' : '本次回答未完成，可重试原问题。' }}</span>
                  <el-button
                    data-test="retry-answer"
                    text
                    type="primary"
                    :icon="RefreshRight"
                    :disabled="streaming || loadingHistory"
                    @click="retryAnswer(message)"
                  >
                    重试
                  </el-button>
                </div>
              </div>
            </div>
          </article>
        </div>

        <div class="composer">
          <el-input
            v-model="question"
            data-test="chat-input"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 5 }"
            maxlength="1000"
            show-word-limit
            :disabled="streaming || creating || loadingHistory || !readyKnowledgeBases.length"
            placeholder="输入你想从知识库中查询的问题…"
            @keydown="handleComposerKeydown"
          />
          <div class="composer__footer">
            <span>Enter 发送 · Shift + Enter 换行</span>
            <el-button
              v-if="streaming"
              data-test="stop-generation"
              type="danger"
              plain
              :icon="VideoPause"
              @click="stopStreaming"
            >
              停止生成
            </el-button>
            <el-button
              v-else
              data-test="send-question"
              type="primary"
              :icon="Promotion"
              :disabled="!canSend"
              @click="sendQuestion"
            >
              发送
            </el-button>
          </div>
        </div>
      </main>

    </div>
  </section>
</template>

<style scoped>
.chat-page {
  min-width: 0;
}

.chat-page > :deep(.el-alert) {
  margin-bottom: 16px;
}

.chat-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}

.chat-toolbar h2 {
  margin: 0 0 4px;
  color: var(--color-heading);
  font-size: 22px;
  line-height: 30px;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.chat-toolbar p {
  margin: 0;
  color: var(--color-muted);
}

.chat-toolbar__controls {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-toolbar__controls :deep(.el-select:first-child) {
  width: 230px;
}

.chat-toolbar__controls :deep(.el-select:nth-child(2)) {
  width: 132px;
}

.chat-workspace {
  height: calc(100vh - var(--topbar-height) - 128px);
  min-height: 580px;
  display: grid;
  grid-template-columns: 260px minmax(420px, 1fr);
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.session-panel {
  min-width: 0;
  background: #fbfdff;
  border-right: 1px solid var(--color-border);
}

.panel-heading {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid var(--color-border);
}

.panel-heading strong {
  color: var(--color-heading);
  font-size: 14px;
  font-weight: 650;
}

.panel-heading span {
  min-width: 24px;
  padding: 1px 7px;
  color: var(--color-muted);
  background: #eef2f7;
  border-radius: 999px;
  text-align: center;
  font-size: 12px;
}

.session-list {
  height: calc(100% - 56px);
  overflow-y: auto;
  padding: 10px;
}

.session-item {
  width: 100%;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 8px;
  margin-bottom: 4px;
  padding: 10px;
  color: var(--color-text);
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  text-align: left;
}

.session-item:hover,
.session-item.is-active {
  background: var(--color-primary-subtle);
}

.session-item.is-active {
  border-color: #bfdbfe;
}

.session-item__icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
  background: #dbeafe;
  border-radius: var(--radius-sm);
}

.session-item__content {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.session-item__content strong,
.session-item__content small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__content strong {
  color: var(--color-heading);
  font-size: 13px;
  font-weight: 600;
}

.session-item__content small {
  margin-top: 2px;
  color: var(--color-muted);
  font-size: 11px;
}

.session-item__actions {
  grid-column: 2;
  display: none;
  justify-content: flex-end;
  margin-top: 4px;
}

.session-item:hover .session-item__actions,
.session-item:focus-within .session-item__actions {
  display: flex;
}

.session-item__actions :deep(.el-button) {
  width: 26px;
  height: 26px;
  margin-left: 2px;
  padding: 0;
}

.session-empty {
  height: calc(100% - 56px);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 24px;
  color: var(--color-muted);
  text-align: center;
}

.session-empty .el-icon {
  color: #94a3b8;
  font-size: 26px;
}

.session-empty p {
  margin: 10px 0 2px;
  color: var(--color-text);
  font-weight: 600;
}

.session-empty span {
  font-size: 12px;
  line-height: 18px;
}

.conversation-panel {
  min-width: 0;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  background: var(--color-surface);
}

.message-viewport {
  overflow-y: auto;
  padding: 28px 32px;
}

.conversation-empty {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: var(--color-muted);
  text-align: center;
}

.conversation-empty__icon {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
  background: var(--color-primary-subtle);
  border: 1px solid #bfdbfe;
  border-radius: 14px;
  font-size: 24px;
}

.conversation-empty h3 {
  margin: 16px 0 6px;
  color: var(--color-heading);
  font-size: 17px;
}

.conversation-empty p {
  max-width: 430px;
  margin: 0;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 24px;
}

.message-row.is-user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  color: var(--color-primary);
  background: var(--color-primary-subtle);
  border: 1px solid #bfdbfe;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 700;
}

.is-user .message-avatar {
  color: #fff;
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.message-body {
  max-width: min(720px, 82%);
}

.user-message,
.assistant-message {
  padding: 11px 14px;
  border-radius: var(--radius-md);
}

.user-message {
  color: #fff;
  background: var(--color-primary);
  white-space: pre-wrap;
}

.assistant-message {
  background: #f8fafc;
  border: 1px solid var(--color-border);
}

.is-error .assistant-message {
  background: #fef2f2;
  border-color: #fecaca;
}

.is-stopped .assistant-message {
  background: #fffbeb;
  border-color: #fde68a;
}

.citation-disclosures {
  display: grid;
  gap: 7px;
  margin-top: 11px;
}

.citation-disclosures details {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
}

.citation-disclosures summary {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  cursor: pointer;
  list-style: none;
}

.citation-disclosures summary::-webkit-details-marker {
  display: none;
}

.source-number {
  color: var(--color-primary);
  font-size: 11px;
  font-weight: 700;
}

.citation-disclosures summary strong {
  overflow: hidden;
  color: var(--color-heading);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.citation-disclosures summary small {
  color: var(--color-muted);
  font-size: 10px;
}

.citation-disclosures details > p {
  margin: 0;
  padding: 0 10px 10px;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 19px;
}

.message-recovery {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
}

.message-recovery > span {
  color: var(--color-muted);
  font-size: 11px;
  line-height: 17px;
}

.typing-indicator {
  height: 20px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.typing-indicator i {
  width: 5px;
  height: 5px;
  background: #94a3b8;
  border-radius: 50%;
  animation: pulse 1.1s infinite ease-in-out;
}

.typing-indicator i:nth-child(2) { animation-delay: 0.16s; }
.typing-indicator i:nth-child(3) { animation-delay: 0.32s; }

@keyframes pulse {
  0%, 70%, 100% { opacity: 0.35; transform: translateY(0); }
  35% { opacity: 1; transform: translateY(-3px); }
}

.composer {
  padding: 14px 18px 16px;
  border-top: 1px solid var(--color-border);
}

.composer :deep(.el-textarea__inner) {
  min-height: 58px !important;
  padding: 10px 12px;
  box-shadow: 0 0 0 1px var(--color-border) inset;
  resize: none;
}

.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 9px;
}

.composer__footer > span {
  color: var(--color-muted);
  font-size: 11px;
}

@media (max-width: 1250px) {
  .chat-toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }

  .chat-toolbar__controls {
    width: 100%;
  }

  .chat-toolbar__controls :deep(.el-select:first-child) {
    flex: 1;
    width: auto;
  }

  .chat-workspace {
    height: calc(100vh - var(--topbar-height) - 190px);
    min-height: 520px;
    grid-template-columns: 260px minmax(420px, 1fr);
  }
}
</style>
