import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MarkdownContent from './MarkdownContent.vue'

describe('MarkdownContent', () => {
  it('renders markdown and sanitizes unsafe HTML', () => {
    const wrapper = mount(MarkdownContent, {
      props: { content: '**制度答案** <img src="x" onerror="alert(1)">' },
    })

    expect(wrapper.get('strong').text()).toBe('制度答案')
    expect(wrapper.get('img').attributes('onerror')).toBeUndefined()
  })
})
