<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createAdminKnowledgePointApi,
  deleteAdminKnowledgePointApi,
  getAdminKnowledgeTreeApi,
  updateAdminKnowledgePointApi,
} from '@/api/admin'
import type { KPTreeNodeVO } from '@/types'

/** 管理员端 · 知识点管理（树查询、增改删；删除受子节点/题目引用约束）。 */
const subject = ref('SUBJECT_1')
const tree = ref<KPTreeNodeVO[]>([])
const loading = ref(false)
const loadError = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  subjectCode: 'SUBJECT_1',
  parentId: 0,
  name: '',
  sortOrder: 0,
  description: '',
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入知识点名称', trigger: 'blur' }],
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = false
  tree.value = []
  try {
    tree.value = await getAdminKnowledgeTreeApi(subject.value)
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

function onSubjectChange(): void {
  void load()
}

function openCreate(parentId: number): void {
  editingId.value = null
  form.subjectCode = subject.value
  form.parentId = parentId
  form.name = ''
  form.sortOrder = 0
  form.description = ''
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

function openEdit(node: KPTreeNodeVO): void {
  editingId.value = node.id
  form.subjectCode = subject.value
  form.parentId = node.parentId
  form.name = node.name
  form.sortOrder = node.sortOrder
  form.description = node.description ?? ''
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function save(): Promise<void> {
  if (!formRef.value || saving.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    const data = {
      subjectCode: form.subjectCode,
      parentId: form.parentId,
      name: form.name.trim(),
      sortOrder: form.sortOrder,
      // 空字符串用于明确清空描述；后端只在 null 时保留旧值
      description: form.description.trim(),
    }
    if (editingId.value == null) {
      await createAdminKnowledgePointApi(data)
      ElMessage.success('知识点已创建')
    } else {
      await updateAdminKnowledgePointApi(editingId.value, data)
      ElMessage.success('知识点已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    saving.value = false
  }
}

async function remove(node: KPTreeNodeVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除知识点「${node.name}」吗？有子知识点或被题目引用时后端会拒绝。`,
      '删除知识点',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteAdminKnowledgePointApi(node.id)
    ElMessage.success('知识点已删除')
    await load()
  } catch {
    // 错误提示由拦截器统一弹出
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="akp">
    <div class="akp__head">
      <h2 class="akp__title">知识点管理</h2>
      <div class="akp__actions">
        <el-select v-model="subject" style="width: 120px" @change="onSubjectChange">
          <el-option label="科目一" value="SUBJECT_1" />
          <el-option label="科目二" value="SUBJECT_2" />
          <el-option label="科目三" value="SUBJECT_3" />
          <el-option label="科目四" value="SUBJECT_4" />
        </el-select>
        <el-button type="primary" @click="openCreate(0)">新增根知识点</el-button>
      </div>
    </div>

    <el-card v-loading="loading" shadow="never">
      <el-result v-if="loadError" icon="error" title="知识点树加载失败" sub-title="请稍后重试">
        <template #extra><el-button type="primary" @click="load">重试</el-button></template>
      </el-result>

      <el-tree
        v-else
        :data="tree"
        node-key="id"
        :props="{ label: 'name', children: 'children' }"
        default-expand-all
        empty-text="暂无知识点"
      >
        <template #default="{ data }">
          <span class="akp__node">
            <span class="akp__name">{{ data.name }}</span>
            <span class="akp__count">{{ data.questionCount ?? 0 }} 题</span>
            <span class="akp__ops">
              <el-button link type="primary" size="small" @click.stop="openCreate(data.id)">添加子节点</el-button>
              <el-button link type="primary" size="small" @click.stop="openEdit(data)">编辑</el-button>
              <el-button link type="danger" size="small" @click.stop="remove(data)">删除</el-button>
            </span>
          </span>
        </template>
      </el-tree>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? '新增知识点' : '编辑知识点'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="科目">
          <el-tag>{{ form.subjectCode }}</el-tag>
        </el-form-item>
        <el-form-item label="父节点 ID">
          <el-input-number v-model="form.parentId" :min="0" :controls="false" style="width: 100%" />
          <span class="akp__hint">0 表示根节点；后端会校验科目和循环引用。</span>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="saving" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.akp__head {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.akp__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.akp__actions {
  margin-left: auto;
  display: flex;
  gap: 10px;
}
.akp__node {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.akp__name {
  flex: 1;
}
.akp__count {
  font-size: 12px;
  color: #687589;
}
.akp__ops {
  display: flex;
  gap: 0;
}
.akp__hint {
  display: block;
  width: 100%;
  font-size: 12px;
  color: #687589;
}
</style>
