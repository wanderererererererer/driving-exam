<script setup lang="ts">
const cards = [
  { label: '待处理预约', value: '--' },
  { label: '今日预约', value: '--' },
  { label: '绑定学员', value: '--' },
  { label: '未读消息', value: '--' },
]
</script>

<template>
  <div class="dashboard">
    <el-alert
      title="教练工作台（占位）"
      type="info"
      show-icon
      :closable="false"
      description="Day 4 起接入待处理预约、今日预约、建议可考学员与最近预约。"
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
  color: #909399;
  font-size: 13px;
}
.dashboard__value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
</style>
