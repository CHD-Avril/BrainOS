<script setup lang="ts">
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { DailyCount } from './api'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const props = defineProps<{ data: DailyCount[] }>()
const container = ref<HTMLElement>()
let chart: ECharts | undefined
let observer: ResizeObserver | undefined

onMounted(async () => {
  await nextTick()
  if (!container.value) return
  chart = init(container.value)
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(container.value)
  render()
})

watch(() => props.data, render, { deep: true })

onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
})

function render(): void {
  if (!chart) return
  chart.setOption({
    animationDuration: 280,
    grid: { left: 42, right: 18, top: 20, bottom: 34 },
    tooltip: {
      trigger: 'axis',
      formatter: (items: unknown) => {
        const list = items as Array<{ axisValueLabel: string, value: number }>
        return `${list[0]?.axisValueLabel ?? ''}<br/>提问量：${list[0]?.value ?? 0}`
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: props.data.map(item => item.date.slice(5).replace('-', '/')),
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: { color: '#64748b' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f7' } },
    },
    series: [{
      type: 'line',
      data: props.data.map(item => item.count),
      smooth: 0.32,
      symbol: 'circle',
      symbolSize: 7,
      showSymbol: false,
      lineStyle: { width: 3, color: '#2563eb' },
      itemStyle: { color: '#2563eb' },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(37, 99, 235, 0.22)' },
            { offset: 1, color: 'rgba(37, 99, 235, 0.02)' },
          ],
        },
      },
    }],
  })
}
</script>

<template>
  <div ref="container" class="trend-chart" role="img" aria-label="最近七天 AI 提问量折线图" />
</template>

<style scoped>
.trend-chart {
  width: 100%;
  height: 300px;
}
</style>
