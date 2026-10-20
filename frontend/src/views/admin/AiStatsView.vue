<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAdminAiStatsApi, type AdminAiStatsVO } from '@/api/admin'

/** 管理员端 · AI 问答统计（只展示现有字段可支持的聚合）。 */
const loading = ref(false)
const loadError = ref(false)
const data = ref<AdminAiStatsVO | null>(null)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  try {
    data.value = await getAdminAiStatsApi()
  } catch {
    data.value = null
    loadError.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="aistat">
    <div class="aistat__head">
      <h2 class="aistat__title">AI 问答统计</h2>
      <span class="aistat__desc">
        仅统计 ai_chat_record 的实际记录；不提供满意度、回答正确率、成本等无可靠数据来源的指标。
      </span>
    </div>

    <el-result
      v-if="loadError"
      icon="error"
      title="AI 统计加载失败"
      sub-title="请检查网络或稍后重试"
    >
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <el-row v-else v-loading="loading" :gutter="16">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="aistat__label">累计问答数</div>
          <div class="aistat__value">{{ data ? data.totalCount : '—' }}</div>
          <div class="aistat__hint">全部 ai_chat_record 记录</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="aistat__label">今日问答数</div>
          <div class="aistat__value">{{ data ? data.todayCount : '—' }}</div>
          <div class="aistat__hint">按 create_time 当天 0 点起统计</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="aistat__label">参与用户数</div>
          <div class="aistat__value">{{ data ? data.distinctUsers : '—' }}</div>
          <div class="aistat__hint">去重后的 user_id 数量</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.aistat__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.aistat__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.aistat__desc {
  max-width: 760px;
  font-size: 13px;
  color: #687589;
}
.aistat__label {
  font-size: 13px;
  color: #687589;
}
.aistat__value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 600;
  color: #263449;
}
.aistat__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #a3adbb;
}
</style>
