<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { knowledgeApi } from '@/features/knowledge/api'
import { validateKnowledgeInput } from '@/features/knowledge/controller'
import { requireAuth } from '@/navigation/auth'

const id = ref<number | null>(null)
const loading = ref(false)
const submitting = ref(false)
const loadError = ref('')
const form = reactive({ name: '', description: '' })
const errors = reactive({ name: '', description: '' })

onLoad((options) => {
  if (!requireAuth()) return
  const parsed = Number(options?.id)
  if (Number.isInteger(parsed) && parsed > 0) {
    id.value = parsed
    void loadExisting(parsed)
  }
  else {
    uni.setNavigationBarTitle({ title: '新建知识库' })
  }
})

async function loadExisting(knowledgeBaseId: number) {
  loading.value = true
  loadError.value = ''
  try {
    const row = await knowledgeApi.get(knowledgeBaseId)
    form.name = row.name
    form.description = row.description ?? ''
  }
  catch (error) {
    loadError.value = error instanceof Error ? error.message : '知识库加载失败'
  }
  finally {
    loading.value = false
  }
}

async function submit() {
  const validation = validateKnowledgeInput(form.name, form.description)
  errors.name = validation.errors.name || ''
  errors.description = validation.errors.description || ''
  if (errors.name || errors.description || submitting.value) return

  submitting.value = true
  try {
    if (id.value) await knowledgeApi.update(id.value, validation.value)
    else await knowledgeApi.create(validation.value)
    uni.showToast({ title: id.value ? '知识库已更新' : '知识库已创建', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  }
  catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '保存失败，请重试', icon: 'none' })
  }
  finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page editor-page">
    <view v-if="loading" class="state muted">正在加载…</view>
    <view v-else-if="loadError" class="error-panel card">
      <text class="error-text">{{ loadError }}</text>
      <button v-if="id" class="text-button" @click="loadExisting(id)">重试</button>
    </view>
    <view v-else class="form-card card">
      <label class="field-group">
        <text class="label">名称</text>
        <input v-model="form.name" class="field" maxlength="60" placeholder="例如：人事制度">
        <text v-if="errors.name" class="error-text hint">{{ errors.name }}</text>
      </label>
      <label class="field-group">
        <text class="label">描述</text>
        <textarea v-model="form.description" class="textarea" maxlength="500" placeholder="简要说明知识库内容" />
        <text v-if="errors.description" class="error-text hint">{{ errors.description }}</text>
        <text v-else class="muted counter">{{ form.description.length }}/500</text>
      </label>
      <button class="primary-button" :loading="submitting" :disabled="submitting" @click="submit">保存</button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.editor-page { padding-top: 28rpx; }
.state { padding: 96rpx 0; text-align: center; }
.error-panel { display: flex; align-items: center; justify-content: space-between; padding: 28rpx; }
.text-button { margin: 0; color: var(--color-primary); background: transparent; font-size: 26rpx; }
.form-card { display: flex; flex-direction: column; gap: 34rpx; padding: 32rpx; }
.field-group { display: flex; flex-direction: column; gap: 14rpx; }
.label { color: var(--color-heading); font-weight: 650; }
.field, .textarea { box-sizing: border-box; width: 100%; padding: 0 22rpx; background: #f8fafc; border: 1rpx solid var(--color-border); border-radius: 14rpx; }
.field { height: 88rpx; }
.textarea { height: 260rpx; padding-top: 20rpx; }
.hint, .counter { font-size: 24rpx; }
.counter { align-self: flex-end; }
.primary-button { width: 100%; }
</style>
