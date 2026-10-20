<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createAdminPaperApi,
  getAdminPaperApi,
  listAdminPapersApi,
  setAdminPaperStatusApi,
  updateAdminPaperApi,
  type AdminPaperParams,
  type AdminPaperVO,
} from '@/api/admin'

/** 管理员端 · 试卷管理（列表、组卷创建/编辑、启停用；不删除历史试卷）。 */
const PAGE_SIZE = 10
const subject = ref('')
const list = ref<AdminPaperVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadError = ref(false)
let seq = 0

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  subjectCode: 'SUBJECT_1',
  title: '',
  paperType: 'MOCK',
  duration: 20,
  passScore: 1 as number | undefined,
  status: 1,
  questions: [{ questionId: undefined as number | undefined, score: 1 as number | undefined }],
})
const rules: FormRules = {
  subjectCode: [{ required: true, message: '请选择科目', trigger: 'change' }],
  title: [{ required: true, message: '请输入试卷名称', trigger: 'blur' }],
}

async function load(): Promise<void> {
  const my = ++seq
  loading.value = true
  loadError.value = false
  try {
    const page = await listAdminPapersApi({
      subjectCode: subject.value || undefined,
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    if (my !== seq) return
    list.value = page.records
    total.value = page.total
  } catch {
    if (my !== seq) return
    list.value = []
    total.value = 0
    loadError.value = true
  } finally {
    if (my === seq) loading.value = false
  }
}

function search(): void {
  pageNum.value = 1
  void load()
}

function onPage(page: number): void {
  pageNum.value = page
  void load()
}

function addRow(): void {
  form.questions.push({ questionId: undefined, score: 1 })
}

function removeRow(index: number): void {
  if (form.questions.length <= 1) return
  form.questions.splice(index, 1)
}

function openCreate(): void {
  editingId.value = null
  form.subjectCode = 'SUBJECT_1'
  form.title = ''
  form.paperType = 'MOCK'
  form.duration = 20
  form.passScore = 1
  form.status = 1
  form.questions = [{ questionId: undefined, score: 1 }]
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function openEdit(row: AdminPaperVO): Promise<void> {
  try {
    const detail = await getAdminPaperApi(row.id)
    editingId.value = detail.id
    form.subjectCode = detail.subjectCode
    form.title = detail.title
    form.paperType = detail.paperType
    form.duration = detail.duration
    form.passScore = detail.passScore
    form.status = detail.status
    form.questions = (detail.questions ?? []).map((q) => ({
      questionId: q.questionId,
      score: q.score,
    }))
    if (form.questions.length === 0) {
      form.questions = [{ questionId: undefined, score: 1 }]
    }
    dialogVisible.value = true
    void nextTick(() => formRef.value?.clearValidate())
  } catch {
    // 错误提示由拦截器统一弹出
  }
}

function buildParams(): AdminPaperParams {
  return {
    subjectCode: form.subjectCode,
    title: form.title.trim(),
    paperType: form.paperType,
    passScore: form.passScore,
    duration: form.duration,
    status: form.status,
    questions: form.questions
      .filter((q) => q.questionId != null && q.score != null)
      .map((q, i) => ({
        questionId: q.questionId as number,
        score: q.score as number,
        sortOrder: i + 1,
      })),
  }
}

async function save(): Promise<void> {
  if (!formRef.value || saving.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    if (editingId.value == null) {
      await createAdminPaperApi(buildParams())
      ElMessage.success('试卷已创建')
      pageNum.value = 1
    } else {
      await updateAdminPaperApi(editingId.value, buildParams())
      ElMessage.success('试卷已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一弹出（已有考试记录的试卷禁止改题）
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: AdminPaperVO): Promise<void> {
  try {
    await setAdminPaperStatusApi(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success(row.status === 1 ? '已停用' : '已启用')
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
  <div class="apaper">
    <div class="apaper__head">
      <h2 class="apaper__title">试卷管理</h2>
      <el-button type="primary" class="apaper__new" @click="openCreate">新增试卷</el-button>
    </div>

    <el-card shadow="never">
      <div class="apaper__toolbar">
        <el-select v-model="subject" placeholder="全部科目" clearable style="width: 130px">
          <el-option label="科目一" value="SUBJECT_1" />
          <el-option label="科目二" value="SUBJECT_2" />
          <el-option label="科目三" value="SUBJECT_3" />
          <el-option label="科目四" value="SUBJECT_4" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
      </div>

      <el-result v-if="loadError" icon="error" title="试卷列表加载失败" sub-title="请稍后重试">
        <template #extra><el-button type="primary" @click="load">重试</el-button></template>
      </el-result>

      <template v-else>
        <el-table v-loading="loading" :data="list" stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="试卷名称" min-width="220" />
          <el-table-column prop="subjectCode" label="科目" width="90" />
          <el-table-column prop="paperType" label="类型" width="90" />
          <el-table-column prop="questionCount" label="题数" width="80" />
          <el-table-column prop="totalScore" label="总分" width="80" />
          <el-table-column prop="duration" label="时长(分)" width="90" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="toggleStatus(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无试卷" /></template>
        </el-table>

        <div v-if="total > PAGE_SIZE" class="apaper__pager">
          <el-pagination
            layout="prev, pager, next, total"
            :total="total"
            :page-size="PAGE_SIZE"
            :current-page="pageNum"
            @current-change="onPage"
          />
        </div>
      </template>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId == null ? '新增试卷' : '编辑试卷'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="已有模拟考试记录的试卷禁止修改题目组成；题目 ID 可先在题库管理中查看，后台会校验启用状态和科目一致性。"
        class="apaper__alert"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="名称" prop="title">
              <el-input v-model="form.title" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="科目" prop="subjectCode">
              <el-select v-model="form.subjectCode" style="width: 100%">
                <el-option label="科目一" value="SUBJECT_1" />
                <el-option label="科目二" value="SUBJECT_2" />
                <el-option label="科目三" value="SUBJECT_3" />
                <el-option label="科目四" value="SUBJECT_4" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="类型">
              <el-input v-model="form.paperType" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="时长(分)">
              <el-input-number v-model="form.duration" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="及格分">
              <el-input-number v-model="form.passScore" :min="1" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题目组成">
          <div class="apaper__rows">
            <div v-for="(q, i) in form.questions" :key="i" class="apaper__row">
              <el-input-number
                v-model="q.questionId"
                :min="1"
                :controls="false"
                placeholder="题目 ID"
                style="width: 150px"
              />
              <el-input-number v-model="q.score" :min="1" :controls="false" style="width: 110px" />
              <el-button link type="danger" @click="removeRow(i)">删除</el-button>
            </div>
            <el-button size="small" @click="addRow">添加题目</el-button>
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
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
.apaper__head {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.apaper__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.apaper__new {
  margin-left: auto;
}
.apaper__toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.apaper__pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.apaper__alert {
  margin-bottom: 16px;
}
.apaper__rows {
  width: 100%;
}
.apaper__row {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
  align-items: center;
}
</style>
