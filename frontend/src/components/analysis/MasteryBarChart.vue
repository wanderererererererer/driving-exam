<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { KPStatVO } from '@/types'

/**
 * 知识点掌握度 —— **横向柱状图**（按掌握度升序，最薄弱在最上；短板标红）。
 *
 * <p>为什么不用雷达图：本项目知识点只有 4 个（维度不够），且知识点是父子树、
 * 雷达要求各维度相互独立（父子维度重叠）。见 {@code doc/analysis-design.md} §5.2。</p>
 */
const props = defineProps<{ data: KPStatVO[] }>()

const el = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

function buildOption(): echarts.EChartsOption {
  // 升序（最弱在前）；yAxis.inverse=true 让索引 0 显示在最上方
  const sorted = [...props.data].sort((a, b) => a.mastery - b.mastery)
  return {
    grid: { left: 8, right: 52, top: 8, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
    yAxis: {
      type: 'category',
      inverse: true,
      data: sorted.map((k) => k.knowledgePointName),
      axisTick: { show: false },
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 22,
        label: { show: true, position: 'right', formatter: '{c}%' },
        data: sorted.map((k) => ({
          value: Math.round(k.mastery * 100),
          itemStyle: { color: k.isWeak ? '#c96a6a' : '#7597d5' }, // 短板红 / 正常蓝
        })),
      },
    ],
  }
}

function render() {
  if (!el.value) return
  if (!chart) {
    chart = echarts.init(el.value)
  }
  chart.setOption(buildOption(), true)
}

function onResize() {
  chart?.resize()
}

onMounted(() => {
  render()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose() // 必须 dispose，避免内存泄漏
  chart = null
})

watch(() => props.data, render, { deep: true })
</script>

<template>
  <div ref="el" class="mbc"></div>
</template>

<style scoped>
.mbc {
  width: 100%;
  height: 260px;
}
</style>
