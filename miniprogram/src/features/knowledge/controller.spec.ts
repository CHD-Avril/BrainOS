import { describe, expect, it } from 'vitest'
import { validateKnowledgeInput } from './controller'

describe('validateKnowledgeInput', () => {
  it('trims valid input and converts an empty description to null', () => {
    expect(validateKnowledgeInput('  人事制度  ', '   ')).toEqual({ value: { name: '人事制度', description: null }, errors: {} })
  })

  it('reports the backend length limits', () => {
    expect(validateKnowledgeInput('A', 'x'.repeat(501)).errors).toEqual({
      name: '名称请输入 2–60 个字符',
      description: '描述不能超过 500 个字符',
    })
  })
})
