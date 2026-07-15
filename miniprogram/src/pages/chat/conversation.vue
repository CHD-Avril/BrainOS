<script setup lang="ts">
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { nextTick, reactive, ref } from 'vue'
import { chatStreamClient } from '@/api/chat-stream'
import { chatApi } from '@/features/chat/api'
import { createConversationController, type ConversationMessage } from '@/features/chat/conversation'
import { requireAuth } from '@/navigation/auth'

const sessionId = ref(0)
const question = ref('')
const scrollIntoView = ref('')
const expanded = reactive<Record<string, boolean>>({})
const controller = createConversationController({
  get: chatApi.get,
  stream: (id, value, onEvent) => chatStreamClient.stream(id, value, (event) => {
    onEvent(event)
    if (event.type === 'delta') void scrollToBottom()
  }),
})

onLoad((options) => {
  if (!requireAuth()) return
  const parsed = Number(options?.sessionId)
  if (!Number.isInteger(parsed) || parsed <= 0) {
    controller.error = '会话参数无效'
    return
  }
  sessionId.value = parsed
  void load()
})

onUnload(() => controller.stop())

async function load() {
  try {
    await controller.load(sessionId.value)
    if (controller.session) uni.setNavigationBarTitle({ title: controller.session.title })
    await scrollToBottom()
  }
  catch {
    // The controller exposes the safe message.
  }
}

async function send() {
  const value = question.value.trim()
  if (!value || value.length > 1000 || controller.streaming) {
    await controller.send(sessionId.value, value)
    return
  }
  question.value = ''
  const sending = controller.send(sessionId.value, value)
  await scrollToBottom()
  await sending
  await scrollToBottom()
}

async function retry(message: ConversationMessage) {
  await controller.retry(message)
  await scrollToBottom()
}

function citationKey(message: ConversationMessage, index: number): string {
  return `${message.key}-citation-${index}`
}

function toggleCitation(message: ConversationMessage, index: number) {
  const key = citationKey(message, index)
  expanded[key] = !expanded[key]
}

async function scrollToBottom() {
  await nextTick()
  scrollIntoView.value = ''
  await nextTick()
  scrollIntoView.value = 'conversation-bottom'
}
</script>

<template>
  <view class="conversation-page">
    <view v-if="controller.error" class="error-banner">
      <text class="error-text">{{ controller.error }}</text>
      <button v-if="controller.messages.length === 0" class="text-button" @click="load">重试</button>
    </view>

    <scroll-view class="message-scroll" scroll-y :scroll-into-view="scrollIntoView" :enhanced="true">
      <view v-if="controller.loading" class="state muted">正在加载消息…</view>
      <view v-else-if="controller.messages.length === 0" class="welcome">
        <text class="welcome__title">向知识库提问</text>
        <text class="muted">回答会引用已处理完成的企业文档</text>
      </view>
      <view v-for="message in controller.messages" :key="message.key" class="message-row" :class="`message-row--${message.role.toLowerCase()}`">
        <view class="bubble" :class="[`bubble--${message.role.toLowerCase()}`, { 'bubble--error': message.error }]">
          <text class="message-content">{{ message.content || (message.streaming ? '正在思考…' : '') }}</text>
          <text v-if="message.stopped" class="message-state muted">已停止生成</text>
          <text v-else-if="message.error" class="message-state error-text">回答未完成</text>

          <view v-if="message.citations.length" class="citations">
            <text class="citations__title">参考资料</text>
            <view v-for="(citation, index) in message.citations" :key="citation.chunkId" class="citation">
              <view class="citation__header" @click="toggleCitation(message, index)">
                <text>{{ citation.fileName }}{{ citation.pageNumber ? ` · 第 ${citation.pageNumber} 页` : '' }}</text>
                <text class="muted">{{ expanded[citationKey(message, index)] ? '收起' : '展开' }}</text>
              </view>
              <text v-if="expanded[citationKey(message, index)]" class="citation__snippet">{{ citation.snippet }}</text>
            </view>
          </view>

          <button v-if="(message.error || message.stopped) && message.retryQuestion" class="retry-button" :disabled="controller.streaming" @click="retry(message)">重试本次提问</button>
        </view>
      </view>
      <view id="conversation-bottom" class="bottom-anchor" />
    </scroll-view>

    <view class="composer">
      <textarea v-model="question" class="question-input" maxlength="1000" auto-height placeholder="输入问题，最多 1000 字" :disabled="controller.streaming" />
      <button v-if="controller.streaming" class="stop-button" @click="controller.stop">停止</button>
      <button v-else class="send-button primary-button" @click="send">发送</button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.conversation-page { display: flex; flex-direction: column; height: 100vh; background: var(--color-bg); }
.error-banner { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; padding: 16rpx 28rpx; background: #fef2f2; border-bottom: 1rpx solid #fecaca; }
.text-button { flex: none; margin: 0; color: var(--color-primary); background: transparent; font-size: 25rpx; }
.message-scroll { flex: 1; min-height: 0; box-sizing: border-box; padding: 24rpx 28rpx; }
.state, .welcome { padding: 120rpx 24rpx; text-align: center; }
.welcome { display: flex; flex-direction: column; gap: 12rpx; }
.welcome__title { color: var(--color-heading); font-size: 36rpx; font-weight: 700; }
.message-row { display: flex; margin-bottom: 24rpx; }
.message-row--user { justify-content: flex-end; }
.message-row--assistant { justify-content: flex-start; }
.bubble { box-sizing: border-box; max-width: 88%; padding: 22rpx 24rpx; border-radius: 20rpx; }
.bubble--user { color: #fff; background: var(--color-primary); border-bottom-right-radius: 6rpx; }
.bubble--assistant { background: #fff; border: 1rpx solid var(--color-border); border-bottom-left-radius: 6rpx; }
.bubble--error { border-color: #fecaca; }
.message-content { white-space: pre-wrap; word-break: break-word; }
.message-state { display: block; margin-top: 12rpx; font-size: 23rpx; }
.citations { margin-top: 20rpx; padding-top: 16rpx; border-top: 1rpx solid var(--color-border); }
.citations__title { display: block; margin-bottom: 10rpx; color: var(--color-heading); font-size: 24rpx; font-weight: 650; }
.citation { margin-top: 10rpx; overflow: hidden; background: #f8fafc; border-radius: 10rpx; }
.citation__header { display: flex; justify-content: space-between; gap: 16rpx; padding: 14rpx; font-size: 23rpx; }
.citation__snippet { display: block; padding: 0 14rpx 14rpx; color: var(--color-muted); font-size: 23rpx; white-space: pre-wrap; }
.retry-button { margin: 16rpx 0 0; color: var(--color-primary); background: var(--color-primary-soft); font-size: 24rpx; line-height: 60rpx; }
.bottom-anchor { height: 2rpx; }
.composer { display: flex; align-items: flex-end; gap: 16rpx; padding: 18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom)); background: #fff; border-top: 1rpx solid var(--color-border); }
.question-input { flex: 1; min-height: 72rpx; max-height: 220rpx; box-sizing: border-box; padding: 16rpx 20rpx; background: #f8fafc; border: 1rpx solid var(--color-border); border-radius: 16rpx; }
.send-button, .stop-button { flex: none; width: 132rpx; margin: 0; font-size: 27rpx; }
.stop-button { color: var(--color-danger); background: #fff; border: 1rpx solid #fecaca; border-radius: 14rpx; }
</style>
