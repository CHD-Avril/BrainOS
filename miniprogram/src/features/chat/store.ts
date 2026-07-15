import { defineStore } from 'pinia'
import { chatApi } from './api'
import type { ChatSession } from './types'

export const useChatStore = defineStore('chat', {
  state: (): { rows: ChatSession[]; loading: boolean; error: string } => ({
    rows: [],
    loading: false,
    error: '',
  }),
  actions: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const rows = await chatApi.list()
        this.rows = rows
      }
      catch (error) {
        this.error = error instanceof Error ? error.message : '会话加载失败'
        throw error
      }
      finally {
        this.loading = false
      }
    },
  },
})
