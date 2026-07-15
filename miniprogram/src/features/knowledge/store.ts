import { defineStore } from 'pinia'
import { knowledgeApi } from './api'
import type { KnowledgeBase } from './types'

export const useKnowledgeStore = defineStore('knowledge', {
  state: (): { rows: KnowledgeBase[]; loading: boolean; error: string } => ({
    rows: [],
    loading: false,
    error: '',
  }),
  actions: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const rows = await knowledgeApi.list()
        this.rows = rows
      }
      catch (error) {
        this.error = error instanceof Error ? error.message : '知识库加载失败'
        throw error
      }
      finally {
        this.loading = false
      }
    },
  },
})
