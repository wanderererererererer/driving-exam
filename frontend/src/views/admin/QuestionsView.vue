<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createAdminQuestionApi,
  deleteAdminQuestionApi,
  downloadQuestionTemplateApi,
  getAdminQuestionApi,
  importAdminQuestionsApi,
  listAdminQuestionsApi,
  setAdminQuestionStatusApi,
  updateAdminQuestionApi,
  type AdminImportResultVO,
  type AdminQuestionParams,
  type AdminQuestionVO,
} from '@/api/admin'
import type { QuestionType } from '@/types'

/** 管理员端 · 题库管理（列表、编辑、启停用、逻辑删除、Excel 导入）。 */
const PAGE_SIZE = 10
const subject = ref('')
const type = ref<'' | QuestionType>('')
const keyword = ref('')
const list = ref<AdminQuestionVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadError = ref(false)
let seq = 0

const SUBJECT_NAMES: Record<string, string> = {
  SUBJECT_1: '科目一',
  SUBJECT_2: '科目二',
  SUBJECT_3: '科目三',
  SUBJECT_4: '科目四',
}
const TYPE_NAMES: Record<string, string> = {
  SINGLE: '单选',
  MULTIPLE: '多选',
  JUDGE: '判断',
}

function subjectLabel(code: string): string {
  return Object.prototype.hasOwnProperty.call(SUBJECT_NAMES, code) ? SUBJECT_NAMES[code] : code
}

async function load(): Promise<void> {
  const my = ++seq
  loading.value = true
  loadError.value = false
  try {
    const page = await listAdminQuestionsApi({
      subjectCode: subject.value || undefined,
      type: type.value || undefined,
      keyword: keyword.value.trim() || undefined,
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

/* ------------------------------- 编辑弹窗 ------------------------------- */

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  subjectCode: 'SUBJECT_1',
  knowledgePointId: 1 as number | undefined,
  questionType: 'SINGLE' as QuestionType,
  content: '',
  answer: '',
  analysis: '',
  difficulty: 1,
  mediaUrl: '',
  status: 1,
})
const options = reactive([
  { key: 'A', content: '' },
  { key: 'B', content: '' },
  { key: 'C', content: '' },
  { key: 'D', content: '' },
])

const rules: FormRules = {
  subjectCode: [{ required: true, message: '请选择科目', trigger: 'change' }],
  knowledgePointId: [{ required: true, message: '请输入知识点 ID', trigger: 'change' }],
  questionType: [{ required: true, message: '请选择题型', trigger: 'change' }],
  content: [{ required: true, message: '请输入题干', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入正确答案', trigger: 'blur' }],
}

function resetOptions(): void {
  options.forEach((o) => {
    o.content = ''
  })
}

function openCreate(): void {
  editingId.value = null
  form.subjectCode = 'SUBJECT_1'
  form.knowledgePointId = 1
  form.questionType = 'SINGLE'
  form.content = ''
  form.answer = ''
  form.analysis = ''
  form.difficulty = 1
  form.mediaUrl = ''
  form.status = 1
  resetOptions()
  dialogVisible.value = true
  void nextTick(() => formRef.value?.clearValidate())
}

async function openEdit(row: AdminQuestionVO): Promise<void> {
  try {
    const detail = await getAdminQuestionApi(row.id)
    editingId.value = detail.id
    form.subjectCode = detail.subjectCode
    form.knowledgePointId = detail.knowledgePointId
    form.questionType = detail.questionType
    form.content = detail.content
    form.answer = detail.answer
    form.analysis = detail.analysis ?? ''
    form.difficulty = detail.difficulty
    form.mediaUrl = detail.mediaUrl ?? ''
    form.status = detail.status
    resetOptions()
    for (const o of detail.options ?? []) {
      const target = options.find((x) => x.key === o.optionKey)
      if (target) target.content = o.optionContent
    }
    dialogVisible.value = true
    void nextTick(() => formRef.value?.clearValidate())
  } catch {
    // 错误提示由拦截器统一弹出
  }
}

function buildParams(): AdminQuestionParams {
  const opts = form.questionType === 'JUDGE'
    ? []
    : options
        .filter((o) => o.content.trim())
        .map((o, i) => ({ optionKey: o.key, optionContent: o.content.trim(), sortOrder: i + 1 }))
  return {
    subjectCode: form.subjectCode,
    knowledgePointId: form.knowledgePointId as number,
    questionType: form.questionType,
    content: form.content.trim(),
    answer: form.answer.trim(),
    analysis: form.analysis.trim() || undefined,
    difficulty: form.difficulty,
    mediaUrl: form.mediaUrl.trim() || undefined,
    status: form.status,
    options: opts,
  }
}

async function save(): Promise<void> {
  if (!formRef.value || saving.value) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    if (editingId.value == null) {
      await createAdminQuestionApi(buildParams())
      ElMessage.success('题目已创建')
      pageNum.value = 1
    } else {
      await updateAdminQuestionApi(editingId.value, buildParams())
      ElMessage.success('题目已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: AdminQuestionVO): Promise<void> {
  try {
    await setAdminQuestionStatusApi(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success(row.status === 1 ? '已停用' : '已启用')
    await load()
  } catch {
    // 错误提示由拦截器统一弹出
  }
}

async function remove(row: AdminQuestionVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定逻辑删除题目 #${row.id} 吗？历史答题记录不会物理删除。`, '删除题目', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteAdminQuestionApi(row.id)
    ElMessage.success('题目已删除')
    await load()
  } catch {
    // 错误提示由拦截器统一弹出
  }
}

/* ------------------------------- Excel 导入 ------------------------------- */

const fileInput = ref<HTMLInputElement | null>(null)
const importing = ref(false)
const importVisible = ref(false)
const importResult = ref<AdminImportResultVO | null>(null)

/** 与后端 AdminQuestionExcelService 的限制保持一致 */
const MAX_IMPORT_BYTES = 10 * 1024 * 1024
const IMPORT_EXTENSIONS = ['.xlsx', '.xls']

function pickFile(): void {
  fileInput.value?.click()
}

async function onFileSelected(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || importing.value) return
  const lowerName = file.name.toLowerCase()
  if (!IMPORT_EXTENSIONS.some((ext) => lowerName.endsWith(ext))) {
    ElMessage.error('仅支持 .xlsx 或 .xls 文件')
    return
  }
  if (file.size > MAX_IMPORT_BYTES) {
    ElMessage.error('文件不能超过 10MB')
    return
  }
  importing.value = true
  try {
    const result = await importAdminQuestionsApi(file)
    importResult.value = result
    importVisible.value = true
    if (result.failed === 0) {
      ElMessage.success(`导入成功：${result.success} 题`)
      await load()
    } else {
      ElMessage.warning(`导入未写入：${result.failed} 行有错误`)
    }
  } catch {
    // 错误提示由拦截器统一弹出
  } finally {
    importing.value = false
  }
}

async function downloadTemplate(): Promise<void> {
  try {
    const blob = await downloadQuestionTemplateApi()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'question-template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('模板下载失败')
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="aq">
    <div class="aq__head">
      <h2 class="aq__title">题库管理</h2>
      <div class="aq__actions">
        <el-button :loading="importing" @click="pickFile">Excel 导入</el-button>
        <el-button @click="downloadTemplate">下载模板</el-button>
        <el-button type="primary" @click="openCreate">新增题目</el-button>
        <input ref="fileInput" type="file" accept=".xlsx,.xls" hidden @change="onFileSelected" />
      </div>
    </div>

    <el-card shadow="never">
      <div class="aq__toolbar">
        <el-select v-model="subject" placeholder="全部科目" clearable style="width: 130px">
          <el-option label="科目一" value="SUBJECT_1" />
          <el-option label="科目二" value="SUBJECT_2" />
          <el-option label="科目三" value="SUBJECT_3" />
          <el-option label="科目四" value="SUBJECT_4" />
        </el-select>
        <el-select v-model="type" placeholder="全部题型" clearable style="width: 130px">
          <el-option label="单选" value="SINGLE" />
          <el-option label="多选" value="MULTIPLE" />
          <el-option label="判断" value="JUDGE" />
        </el-select>
        <el-input v-model="keyword" placeholder="题干关键词" clearable style="width: 220px" @keyup.enter="search" />
        <el-button type="primary" @click="search">查询</el-button>
      </div>

      <el-result v-if="loadError" icon="error" title="题目列表加载失败" sub-title="请稍后重试">
        <template #extra><el-button type="primary" @click="load">重试</el-button></template>
      </el-result>

      <template v-else>
        <el-table v-loading="loading" :data="list" stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="科目" width="90">
            <template #default="{ row }">{{ subjectLabel(row.subjectCode) }}</template>
          </el-table-column>
          <el-table-column label="题型" width="80">
            <template #default="{ row }">{{ TYPE_NAMES[row.questionType] ?? row.questionType }}</template>
          </el-table-column>
          <el-table-column prop="content" label="题干" min-width="260" show-overflow-tooltip />
          <el-table-column prop="answer" label="答案" width="90" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="primary" @click="toggleStatus(row)">
                {{ row.status === 1 ? '停用' : '启用' }}
              </el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty><el-empty description="暂无题目" /></template>
        </el-table>

        <div v-if="total > PAGE_SIZE" class="aq__pager">
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
      :title="editingId == null ? '新增题目' : '编辑题目'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="12">
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
          <el-col :span="12">
            <el-form-item label="知识点 ID" prop="knowledgePointId">
              <el-input-number v-model="form.knowledgePointId" :min="1" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="题型" prop="questionType">
              <el-select v-model="form.questionType" style="width: 100%">
                <el-option label="单选" value="SINGLE" />
                <el-option label="多选" value="MULTIPLE" />
                <el-option label="判断" value="JUDGE" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难度">
              <el-input-number v-model="form.difficulty" :min="1" :max="5" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="题干" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="3" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="正确答案" prop="answer">
          <el-input v-model="form.answer" placeholder="单选 A；多选 AB；判断 对/错" />
        </el-form-item>
        <template v-if="form.questionType !== 'JUDGE'">
          <el-form-item v-for="opt in options" :key="opt.key" :label="`选项 ${opt.key}`">
            <el-input v-model="opt.content" maxlength="500" />
          </el-form-item>
        </template>
        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="2" maxlength="2000" show-word-limit />
        </el-form-item>
        <el-form-item label="媒体 URL">
          <el-input v-model="form.mediaUrl" maxlength="255" />
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

    <el-dialog v-model="importVisible" title="Excel 导入结果" width="620px">
      <template v-if="importResult">
        <el-alert
          :type="importResult.failed === 0 ? 'success' : 'warning'"
          :closable="false"
          show-icon
          :title="`总行数 ${importResult.total}，成功 ${importResult.success}，失败 ${importResult.failed}${importResult.failed > 0 ? '（本次未写入任何数据）' : ''}`"
        />
        <el-table v-if="importResult.errors.length" :data="importResult.errors" size="small" class="aq__errors">
          <el-table-column prop="row" label="行号" width="80" />
          <el-table-column prop="field" label="字段" width="100" />
          <el-table-column prop="message" label="原因" />
        </el-table>
      </template>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.aq__head {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}
.aq__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.aq__actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.aq__toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.aq__pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.aq__errors {
  margin-top: 12px;
}
</style>
