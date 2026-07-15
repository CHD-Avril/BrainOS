<script setup lang="ts">
import { Refresh, Search } from '@element-plus/icons-vue'
import { onMounted, reactive, ref } from 'vue'
import { auditApi, type AuditLog } from './api'

const actionOptions = [
  ['AUTH_LOGIN', '用户登录'],
  ['USER_CREATE', '创建用户'],
  ['USER_UPDATE', '更新用户'],
  ['USER_STATUS_CHANGE', '用户状态'],
  ['KNOWLEDGE_CREATE', '创建知识库'],
  ['KNOWLEDGE_UPDATE', '更新知识库'],
  ['KNOWLEDGE_DELETE', '删除知识库'],
  ['DOCUMENT_UPLOAD', '上传文档'],
  ['DOCUMENT_RETRY', '重试文档'],
  ['DOCUMENT_DELETE', '删除文档'],
] as const

const rows = ref<AuditLog[]>([])
const loading = ref(true)
const error = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({ userId: '', action: '', range: [] as Date[] })

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const result = await auditApi.list({
      ...(filters.userId ? { userId: Number(filters.userId) } : {}),
      ...(filters.action ? { action: filters.action } : {}),
      ...(filters.range[0] ? { from: filters.range[0].toISOString() } : {}),
      ...(filters.range[1] ? { to: filters.range[1].toISOString() } : {}),
      page: page.value,
      size: size.value,
    })
    rows.value = result.items
    total.value = result.total
  }
  catch {
    error.value = '操作日志加载失败，请重试'
  }
  finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function resetFilters(): void {
  filters.userId = ''
  filters.action = ''
  filters.range = []
  page.value = 1
  void load()
}

function changePage(value: number): void {
  page.value = value
  void load()
}

function actionLabel(action: string): string {
  return actionOptions.find(([value]) => value === action)?.[1] ?? action
}

function targetLabel(log: AuditLog): string {
  const type = {
    USER: '用户',
    KNOWLEDGE_BASE: '知识库',
    DOCUMENT: '文档',
  }[log.targetType] ?? log.targetType
  return log.targetId ? `${type} #${log.targetId}` : type
}

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <section class="audit-page" aria-labelledby="audit-heading">
    <header class="page-heading">
      <div>
        <h2 id="audit-heading">操作日志</h2>
        <p>查看关键操作记录，辅助安全追溯与系统管理。</p>
      </div>
    </header>

    <div class="filter-panel">
      <el-input v-model="filters.userId" aria-label="操作人 ID" placeholder="操作人 ID" clearable />
      <el-select v-model="filters.action" aria-label="操作类型" placeholder="操作类型" clearable>
        <el-option v-for="option in actionOptions" :key="option[0]" :label="option[1]" :value="option[0]" />
      </el-select>
      <el-date-picker
        v-model="filters.range"
        type="datetimerange"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        range-separator="至"
        aria-label="日志时间范围"
      />
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <template #default><el-button text type="primary" @click="load">重新加载</el-button></template>
    </el-alert>

    <div class="table-panel">
      <el-table v-loading="loading" :data="rows" empty-text="暂无操作日志">
        <el-table-column label="时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作人" min-width="115">
          <template #default="{ row }">
            <strong class="operator">{{ row.username || '系统 / 未知' }}</strong>
            <span v-if="row.userId" class="muted">ID {{ row.userId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="105">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column label="对象" min-width="100">
          <template #default="{ row }">{{ targetLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" effect="light">
              {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.summary || '—' }}</template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <span>共 {{ total }} 条记录</span>
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="changePage"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.audit-page { min-width: 0; }

.page-heading { margin-bottom: 24px; }
.page-heading h2 {
  margin: 0;
  color: var(--color-heading);
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
}
.page-heading p { margin: 6px 0 0; color: var(--color-muted); }

.filter-panel {
  display: grid;
  grid-template-columns: 150px 180px minmax(320px, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 16px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.filter-panel :deep(.el-date-editor) { width: 100%; }

.table-panel {
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.table-panel :deep(.el-table__header th) {
  height: 48px;
  color: var(--color-muted);
  background: #f8fafc;
  font-weight: 550;
}

.table-panel :deep(.el-table__cell) { padding-block: 12px; }

.operator,
.muted { display: block; }
.operator {
  overflow: hidden;
  color: var(--color-heading);
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.muted { color: var(--color-muted); font-size: 12px; }

.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-top: 1px solid var(--color-border);
}
.pagination-row > span { color: var(--color-muted); font-size: 13px; }

@media (max-width: 1180px) {
  .filter-panel { grid-template-columns: 140px 170px minmax(260px, 1fr) auto; }
  .filter-panel > :last-child { display: none; }
}
</style>
