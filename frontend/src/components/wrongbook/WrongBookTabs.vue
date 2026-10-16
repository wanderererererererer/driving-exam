<script setup lang="ts">
import type { WrongBookCategory, WrongBookSummaryVO } from '@/types'

/**
 * 错题本分类切换（三个 tab + 数量角标）。
 *
 * <p>角标数字由父组件从 {@code /wrong-book/summary} 拿到后传入，本组件不自己请求。</p>
 */
defineProps<{
  modelValue: WrongBookCategory
  /** 分类统计；未加载完时为 null（角标不显示） */
  summary: WrongBookSummaryVO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: WrongBookCategory): void
}>()

const TABS: { value: WrongBookCategory; label: string }[] = [
  { value: 'PENDING', label: '待攻克' },
  { value: 'HIGH_FREQUENCY', label: '高频错' },
  { value: 'MASTERED', label: '已攻克' },
]

function onChange(value: string | number | boolean) {
  emit('update:modelValue', value as WrongBookCategory)
}
</script>

<template>
  <el-radio-group :model-value="modelValue" size="default" @change="onChange">
    <el-radio-button v-for="t in TABS" :key="t.value" :value="t.value">
      {{ t.label }}
      <span v-if="summary" class="wbt__badge">{{ summary[
        t.value === 'PENDING' ? 'pending'
          : t.value === 'HIGH_FREQUENCY' ? 'highFrequency'
            : 'mastered'
      ] }}</span>
    </el-radio-button>
  </el-radio-group>
</template>

<style scoped>
.wbt__badge {
  display: inline-block;
  margin-left: 4px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 18px;
  border-radius: 9px;
  background: rgba(0, 0, 0, 0.08);
}
</style>
