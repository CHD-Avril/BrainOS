<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import KnowledgeCard from './KnowledgeCard.vue'
import { knowledgeApi, type KnowledgeBase } from './api'
import { useKnowledgeStore } from './store'

const router = useRouter()
const store = useKnowledgeStore()
const dialogOpen = ref(false)
const submitting = ref(false)
const editing = ref<KnowledgeBase | null>(null)
const form = reactive({ name: '', description: '' })
const errors = reactive({ name: '', description: '' })

onMounted(store.load)

function openCreate(): void {
  editing.value = null
  form.name = ''
  form.description = ''
  errors.name = ''
  errors.description = ''
  dialogOpen.value = true
}

function openEdit(knowledge: KnowledgeBase): void {
  editing.value = knowledge
  form.name = knowledge.name
  form.description = knowledge.description ?? ''
  errors.name = ''
  errors.description = ''
  dialogOpen.value = true
}

async function submit(): Promise<void> {
  const name = form.name.trim()
  const description = form.description.trim()
  errors.name = name.length < 2 || name.length > 60 ? '名称请输入 2–60 个字符' : ''
  errors.description = description.length > 500 ? '描述不能超过 500 个字符' : ''
  if (errors.name || errors.description || submitting.value) return

  submitting.value = true
  try {
    const input = { name, description: description || null }
    if (editing.value) await knowledgeApi.update(editing.value.id, input)
    else await knowledgeApi.create(input)
    dialogOpen.value = false
    ElMessage.success(editing.value ? '知识库已更新' : '知识库已创建')
    await store.load()
  }
  catch {
    ElMessage.error('保存失败，名称可能已存在')
  }
  finally {
    submitting.value = false
  }
}

async function removeKnowledge(knowledge: KnowledgeBase): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除“${knowledge.name}”后，其文档和向量数据也会清理。`,
      '确认删除知识库',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
    await knowledgeApi.remove(knowledge.id)
    ElMessage.success('知识库已删除')
    await store.load()
  }
  catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('删除失败，请重试')
  }
}
</script>

<template>
  <section class="knowledge-page" aria-labelledby="knowledge-heading">
    <header class="page-header">
      <div>
        <h2 id="knowledge-heading">企业知识库</h2>
        <p>集中管理企业文档，为 AI 问答提供可信资料。</p>
      </div>
      <el-button data-test="create-knowledge" type="primary" @click="openCreate">
        <el-icon><Plus /></el-icon>
        新建知识库
      </el-button>
    </header>

    <el-alert
      v-if="store.error"
      :title="store.error"
      type="error"
      :closable="false"
      show-icon
    >
      <template #default>
        <el-button text type="primary" @click="store.load">重新加载</el-button>
      </template>
    </el-alert>

    <div v-loading="store.loading" class="knowledge-content">
      <el-empty v-if="!store.loading && !store.error && store.rows.length === 0" description="还没有知识库">
        <el-button type="primary" @click="openCreate">新建第一个知识库</el-button>
      </el-empty>
      <div v-else class="knowledge-grid">
        <KnowledgeCard
          v-for="knowledge in store.rows"
          :key="knowledge.id"
          :knowledge="knowledge"
          @enter="router.push(`/knowledge-bases/${knowledge.id}/documents`)"
          @edit="openEdit(knowledge)"
          @remove="removeKnowledge(knowledge)"
        />
      </div>
    </div>

    <el-dialog
      v-model="dialogOpen"
      :title="editing ? '编辑知识库' : '新建知识库'"
      width="480px"
      destroy-on-close
    >
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="名称" :error="errors.name">
          <el-input v-model="form.name" aria-label="知识库名称" maxlength="60" />
        </el-form-item>
        <el-form-item label="描述" :error="errors.description">
          <el-input
            v-model="form.description"
            aria-label="知识库描述"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.knowledge-page {
  min-width: 0;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  color: var(--color-heading);
  font-size: 24px;
  line-height: 32px;
  font-weight: 600;
}

.page-header p {
  margin: 6px 0 0;
  color: var(--color-muted);
}

.knowledge-content {
  min-height: 240px;
}

.knowledge-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 1180px) {
  .knowledge-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
