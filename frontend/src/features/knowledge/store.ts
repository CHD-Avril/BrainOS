import { defineStore } from 'pinia'
import { ref } from 'vue'
import { knowledgeApi, type KnowledgeBase } from './api'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const rows = ref<KnowledgeBase[]>([])
  const loading = ref(false)
  const error = ref('')

  async function load(): Promise<void> {
    loading.value = true
    error.value = ''
    try {
      rows.value = await knowledgeApi.list()
    }
    catch {
      error.value = '知识库加载失败，请重试'
    }
    finally {
      loading.value = false
    }
  }

  return { rows, loading, error, load }
})
