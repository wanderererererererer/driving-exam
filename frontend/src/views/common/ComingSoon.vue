<script setup lang="ts">
import { useRoute } from 'vue-router'
import { Compass } from '@element-plus/icons-vue'

const route = useRoute()
</script>

<template>
  <div class="coming">
    <el-card shadow="never" class="coming__card">
      <el-empty description="该功能入口暂未开放">
        <template #image>
          <div class="coming__icon"><el-icon><Compass /></el-icon></div>
        </template>
        <p class="coming__path">当前路径：{{ route.path }}</p>
        <p class="coming__hint">请从左侧菜单进入已开放的功能页面。</p>
      </el-empty>
    </el-card>
  </div>
</template>

<style scoped>
.coming {
  display: flex;
  justify-content: center;
  padding-top: 40px;
}
.coming__card {
  width: min(560px, 100%);
}
.coming__icon {
  width: 72px;
  height: 72px;
  margin: 0 auto;
  display: grid;
  place-items: center;
  border-radius: 22px;
  background: var(--app-primary-soft);
  color: var(--app-primary-strong);
  font-size: 32px;
}
.coming__path {
  margin: 4px 0;
  color: var(--app-text-regular);
  font-size: 13px;
}
.coming__hint {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}
</style>
