<script setup lang="ts">
import { CircleCheck, CircleClose, Loading } from '@element-plus/icons-vue'
import { computed } from 'vue'
import type { DocumentStatus } from './api'

const props = defineProps<{ status: DocumentStatus }>()

const statusView = computed(() => ({
  PARSING: { label: '解析中', type: 'warning' as const, icon: Loading },
  INDEXING: { label: '索引中', type: 'warning' as const, icon: Loading },
  READY: { label: '可用', type: 'success' as const, icon: CircleCheck },
  FAILED: { label: '失败', type: 'danger' as const, icon: CircleClose },
}[props.status]))
</script>

<template>
  <el-tag data-test="document-status" :type="statusView.type" effect="light">
    <el-icon><component :is="statusView.icon" /></el-icon>
    {{ statusView.label }}
  </el-tag>
</template>

<style scoped>
.el-tag {
  gap: 4px;
  font-weight: 500;
}

.el-icon {
  margin-right: 4px;
}
</style>
