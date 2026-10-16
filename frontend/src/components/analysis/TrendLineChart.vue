<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { TrendPointVO } from '@/types'

/**
 * 成绩趋势 —— 折线图（X 轴日期、Y 轴正确率 0~100%）。
 *
 * <p>后端只返回"有答题"的日期，不补空白日——折线按真实活动点相连。</p>
 */
const props = defineProps<{ data: TrendPointVO[] }>()

const el = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

function buildOption(): echarts.EChartsOption {
  return {
    grid: { left: 8, right: 20, top: 20, bottom: 8, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', boundaryGap: false, data: props.data.map((p) => p.date) },
    yAxis: { type: 'value', min: 0, max: 100, axisLabel: { formatter: '{value}%' } },
    series: [
      {
        name: '正确率',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        areaStyle: { opacity: 0.15 },
        data: props.data.map((p) => Math.round(p.correctRate * 100)),
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
  chart?.dispose()
  chart = null
})

watch(() => props.data, render, { deep: true })
</script>

<template>
  <div ref="el" class="tlc"></div>
</template>

<style scoped>
.tlc {
  width: 100%;
  height: 260px;
}
</style>
