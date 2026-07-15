<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => String(route.meta.title ?? 'BrainOS'))
const description = computed(() => String(route.meta.description ?? '页面功能将在后续阶段接入。'))
</script>

<template>
  <section class="placeholder" :aria-labelledby="`page-${String(route.name)}`">
    <h2 :id="`page-${String(route.name)}`">{{ title }}</h2>
    <p>{{ description }}</p>
  </section>
</template>

<style scoped>
.placeholder {
  min-height: 240px;
  padding: 32px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

h2 {
  margin: 0;
  color: var(--color-heading);
  font-size: 18px;
  line-height: 28px;
  font-weight: 600;
}

p {
  margin: 8px 0 0;
  color: var(--color-muted);
  font-size: 14px;
  line-height: 22px;
}
</style>
