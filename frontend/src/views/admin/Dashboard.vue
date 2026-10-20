<script setup lang="ts">
const cards = [
  { label: '用户总数', value: '--' },
  { label: '题目总数', value: '--' },
  { label: '预约总数', value: '--' },
  { label: 'AI 问答次数', value: '--' },
]
</script>

<template>
  <div class="dashboard">
    <el-alert
      title="管理端数据概览（占位）"
      type="info"
      show-icon
      :closable="false"
      description="Day 4 起接入用户/题库/答题/考试/预约/AI 问答等聚合统计与图表。"
    />

    <el-row :gutter="16" class="dashboard__cards">
      <el-col v-for="item in cards" :key="item.label" :span="6">
        <el-card shadow="hover">
          <div class="dashboard__label">{{ item.label }}</div>
          <div class="dashboard__value">{{ item.value }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.dashboard__cards {
  margin-top: 16px;
}
.dashboard__label {
  color: #687589;
  font-size: 13px;
}
.dashboard__value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
</style>
