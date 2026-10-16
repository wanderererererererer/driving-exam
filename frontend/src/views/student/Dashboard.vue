<script setup lang="ts">
const cards = [
  { label: '科目一进度', value: '--' },
  { label: '科目二进度', value: '--' },
  { label: '今日练习', value: '--' },
  { label: '未读消息', value: '--' },
]
</script>

<template>
  <div class="dashboard">
    <el-alert
      title="学员端首页（占位）"
      type="info"
      show-icon
      :closable="false"
      description="Day 4 起接入真实的科目进度、学习数据卡片、快捷入口与最近学习记录。"
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
