<script setup lang="ts">
import { ChatDotRound, Collection, Document, Files } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import TrendChart from './TrendChart.vue'
import {
  dashboardApi,
  type DailyCount,
  type DashboardSummary,
  type RecentDocument,
} from './api'
import DocumentStatusTag from '@/features/document/DocumentStatusTag.vue'

const router = useRouter()
const summary = ref<DashboardSummary>({
  knowledgeBaseCount: 0,
  documentCount: 0,
  chunkCount: 0,
  questionCount: 0,
})
const trend = ref<DailyCount[]>([])
const recent = ref<RecentDocument[]>([])
const loading = ref(true)
const error = ref('')

const metrics = computed(() => [
  { label: '知识库', value: summary.value.knowledgeBaseCount, icon: Collection, tone: 'blue' },
  { label: '文档总数', value: summary.value.documentCount, icon: Document, tone: 'cyan' },
  { label: '可检索切片', value: summary.value.chunkCount, icon: Files, tone: 'violet' },
  { label: 'AI 提问', value: summary.value.questionCount, icon: ChatDotRound, tone: 'green' },
])

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [summaryData, trendData, recentData] = await Promise.all([
      dashboardApi.summary(),
      dashboardApi.trend(),
      dashboardApi.recentDocuments(),
    ])
    summary.value = summaryData
    trend.value = trendData
    recent.value = recentData
  }
  catch {
    error.value = '工作台数据加载失败，请重试'
  }
  finally {
    loading.value = false
  }
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
  <section v-loading="loading" class="dashboard-page" aria-labelledby="dashboard-heading">
    <header class="page-heading">
      <div>
        <h2 id="dashboard-heading">工作台</h2>
        <p>一眼了解知识库资产与 AI 使用情况。</p>
      </div>
      <el-button type="primary" @click="router.push('/chat')">开始提问</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon>
      <template #default><el-button text type="primary" @click="load">重新加载</el-button></template>
    </el-alert>

    <div class="metric-grid" aria-label="工作台统计">
      <article v-for="metric in metrics" :key="metric.label" class="metric-card">
        <div class="metric-card__icon" :class="`is-${metric.tone}`">
          <el-icon><component :is="metric.icon" /></el-icon>
        </div>
        <div>
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value.toLocaleString('zh-CN') }}</strong>
        </div>
      </article>
    </div>

    <div class="dashboard-grid">
      <article class="panel trend-panel">
        <header class="panel__heading">
          <div>
            <h3>最近 7 天提问趋势</h3>
            <p>统计用户向企业知识助手发起的问题数。</p>
          </div>
        </header>
        <TrendChart :data="trend" />
      </article>

      <article class="panel recent-panel">
        <header class="panel__heading">
          <div>
            <h3>最近文档</h3>
            <p>最新更新的 5 份知识文档。</p>
          </div>
          <el-button text type="primary" @click="router.push('/knowledge-bases')">查看全部</el-button>
        </header>
        <el-empty v-if="!loading && recent.length === 0" description="暂无文档" :image-size="72" />
        <div v-else class="recent-list">
          <button
            v-for="document in recent"
            :key="document.id"
            class="recent-row"
            type="button"
            @click="router.push(`/knowledge-bases/${document.knowledgeBaseId}/documents`)"
          >
            <div class="recent-row__name">
              <strong>{{ document.originalName }}</strong>
              <span>{{ document.knowledgeBaseName }}</span>
            </div>
            <DocumentStatusTag :status="document.status" />
            <time>{{ formatTime(document.updatedAt) }}</time>
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.dashboard-page {
  min-width: 0;
  min-height: 600px;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.page-heading h2,
.panel__heading h3 {
  margin: 0;
  color: var(--color-heading);
  font-weight: 600;
}

.page-heading h2 {
  font-size: 24px;
  line-height: 32px;
}

.page-heading p,
.panel__heading p {
  margin: 6px 0 0;
  color: var(--color-muted);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin: 20px 0 16px;
}

.metric-card,
.panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 116px;
  padding: 20px;
}

.metric-card__icon {
  width: 44px;
  height: 44px;
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 10px;
  font-size: 21px;
}

.metric-card__icon.is-blue { color: #2563eb; background: #eff6ff; }
.metric-card__icon.is-cyan { color: #0891b2; background: #ecfeff; }
.metric-card__icon.is-violet { color: #7c3aed; background: #f5f3ff; }
.metric-card__icon.is-green { color: #16a34a; background: #f0fdf4; }

.metric-card span {
  display: block;
  color: var(--color-muted);
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin-top: 4px;
  color: var(--color-heading);
  font-size: 26px;
  line-height: 34px;
  font-weight: 650;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(360px, 0.9fr);
  gap: 16px;
}

.panel {
  min-width: 0;
  padding: 20px;
}

.panel__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.panel__heading h3 {
  font-size: 16px;
  line-height: 24px;
}

.panel__heading p {
  font-size: 13px;
  line-height: 20px;
}

.recent-list {
  margin-top: 16px;
}

.recent-row {
  width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 84px;
  align-items: center;
  gap: 12px;
  padding: 13px 0;
  color: inherit;
  background: transparent;
  border: 0;
  border-top: 1px solid #eef2f7;
  text-align: left;
}

.recent-row:hover strong { color: var(--color-primary); }

.recent-row__name {
  min-width: 0;
}

.recent-row__name strong,
.recent-row__name span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-row__name strong {
  color: var(--color-heading);
  font-weight: 550;
}

.recent-row__name span,
.recent-row time {
  color: var(--color-muted);
  font-size: 12px;
}

.recent-row time {
  text-align: right;
}

@media (max-width: 1180px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dashboard-grid { grid-template-columns: 1fr; }
}
</style>
