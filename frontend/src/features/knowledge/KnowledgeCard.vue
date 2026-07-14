<script setup lang="ts">
import { Calendar, Document } from '@element-plus/icons-vue'
import type { KnowledgeBase } from './api'

defineProps<{ knowledge: KnowledgeBase }>()
defineEmits<{
  enter: []
  edit: []
  remove: []
}>()

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <article class="knowledge-card">
    <div class="knowledge-card__heading">
      <h3>{{ knowledge.name }}</h3>
      <el-dropdown trigger="click">
        <el-button aria-label="更多操作" text>管理</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="$emit('edit')">编辑</el-dropdown-item>
            <el-dropdown-item divided @click="$emit('remove')">删除</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <p class="knowledge-card__description">
      {{ knowledge.description || '暂无描述' }}
    </p>
    <div class="knowledge-card__meta">
      <span><el-icon><Document /></el-icon>{{ knowledge.readyDocumentCount }} / {{ knowledge.documentCount }} 可用</span>
      <span><el-icon><Calendar /></el-icon>{{ formatDate(knowledge.updatedAt) }}</span>
    </div>
    <el-button data-test="enter-knowledge" type="primary" plain @click="$emit('enter')">
      进入知识库
    </el-button>
  </article>
</template>

<style scoped>
.knowledge-card {
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 220px;
  padding: 20px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition: border-color var(--motion-fast), box-shadow var(--motion-fast);
}

.knowledge-card:hover {
  border-color: #bfdbfe;
  box-shadow: 0 8px 22px rgb(15 23 42 / 6%);
}

.knowledge-card__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.knowledge-card h3 {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: var(--color-heading);
  font-size: 18px;
  line-height: 28px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-card__description {
  min-height: 44px;
  margin: 12px 0 20px;
  display: -webkit-box;
  overflow: hidden;
  color: var(--color-muted);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.knowledge-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  margin-bottom: 18px;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 18px;
}

.knowledge-card__meta span {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.knowledge-card :deep(.el-button--primary) {
  width: 100%;
}
</style>
