<script setup lang="ts">
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import { computed } from 'vue'

const props = defineProps<{ content: string }>()
const html = computed(() => DOMPurify.sanitize(marked.parse(props.content) as string))
</script>

<template>
  <div class="markdown-content" v-html="html" />
</template>

<style scoped>
.markdown-content {
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.75;
  overflow-wrap: anywhere;
}

.markdown-content :deep(p) {
  margin: 0 0 10px;
}

.markdown-content :deep(p:last-child),
.markdown-content :deep(ul:last-child),
.markdown-content :deep(ol:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 8px 0 12px;
  padding-left: 22px;
}

.markdown-content :deep(code) {
  padding: 2px 5px;
  color: #1d4ed8;
  background: #eff6ff;
  border-radius: 4px;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 13px;
}

.markdown-content :deep(pre) {
  overflow-x: auto;
  padding: 12px;
  background: #f1f5f9;
  border-radius: var(--radius-sm);
}

.markdown-content :deep(pre code) {
  padding: 0;
  color: var(--color-heading);
  background: transparent;
}
</style>
