import type { KnowledgeBaseInput } from './types'

interface KnowledgeInputErrors {
  name?: string
  description?: string
}

export interface KnowledgeInputValidation {
  value: KnowledgeBaseInput
  errors: KnowledgeInputErrors
}

export function validateKnowledgeInput(nameValue: string, descriptionValue: string): KnowledgeInputValidation {
  const name = nameValue.trim()
  const description = descriptionValue.trim()
  const errors: KnowledgeInputErrors = {}
  if (name.length < 2 || name.length > 60) errors.name = '名称请输入 2–60 个字符'
  if (description.length > 500) errors.description = '描述不能超过 500 个字符'
  return {
    value: { name, description: description || null },
    errors,
  }
}
