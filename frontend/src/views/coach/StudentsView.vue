<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  bindOrCreateStudentApi,
  listMyStudentsApi,
  unbindStudentApi,
  updateStudentSubjectApi,
} from '@/api/coachStudent'
import StudentLearningDialog from '@/components/coach/StudentLearningDialog.vue'
import type {
  CoachConfirmStatus,
  StudentBindParams,
  StudentUpdateParams,
  StudentVO,
  SubjectCode,
  SubjectPhaseStatus,
} from '@/types'

type TagType = '' | 'success' | 'info' | 'warning' | 'danger'

/**
 * 教练端 · 学员管理。
 *
 * <p>列表与四科学情走只读接口；绑定/创建、改学情/备注、解绑走后端既有写接口。
 * 学习详情（科目阶段、学情、错题、AI 建议与确认）由 `StudentLearningDialog` 统一接入。
 * 所有操作中的 `{bindId}` 都是 {@code coach_student.id}，不是学员的 `studentId`。</p>
 *
 * <p>后端限制（前端不绕过）：
 * <ul>
 *   <li>列表不支持关键字/科目筛选，只支持分页；</li>
 *   <li>绑定/创建是**同一个** `POST /api/coach/students` 的两种分支；</li>
 *   <li>改学情必须指定科目，且阶段/备注至少传一个；</li>
 *   <li>解绑是软解绑，不删除历史数据、不级联取消预约。</li>
 * </ul>
 * </p>
 */
const PAGE_SIZE = 10

const SUBJECT_OPTIONS: { label: string; value: SubjectCode }[] = [
  { label: '科目一', value: 'SUBJECT_1' },
  { label: '科目二', value: 'SUBJECT_2' },
  { label: '科目三', value: 'SUBJECT_3' },
  { label: '科目四', value: 'SUBJECT_4' },
]

const PHASE_OPTIONS: { label: string; value: SubjectPhaseStatus | '' }[] = [
  { label: '不修改', value: '' },
  { label: '未开始', value: 'NOT_STARTED' },
  { label: '学习中', value: 'LEARNING' },
  { label: '已具备考试条件', value: 'READY_FOR_EXAM' },
  { label: '考试通过', value: 'EXAM_PASSED' },
  { label: '考试未通过', value: 'EXAM_FAILED' },
]

const CONFIRM_META: Record<CoachConfirmStatus, { label: string; type: TagType }> = {
  CONFIRMED_READY: { label: '已确认可参加考试', type: 'success' },
  NOT_READY: { label: '暂不建议参加考试', type: 'warning' },
  NEED_HELP: { label: '需要进一步辅导', type: 'danger' },
}

function subjectLabel(code: SubjectCode | null | undefined): string {
  if (!code) return '暂无进行中科目'
  return SUBJECT_OPTIONS.find((opt) => opt.value === code)?.label ?? code
}

function phaseLabel(status: SubjectPhaseStatus | null | undefined): string {
  if (!status) return '—'
  return PHASE_OPTIONS.find((opt) => opt.value === status)?.label ?? status
}

function confirmMeta(status: CoachConfirmStatus | null): { label: string; type: TagType } {
  return status ? CONFIRM_META[status] : { label: '待确认', type: 'info' }
}

/* ------------------------------- 学员列表 ------------------------------- */

const students = ref<StudentVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const loading = ref(false)
const loadError = ref(false)
const unbindingId = ref<number | null>(null)

/** 列表请求序号：只应用最后一次请求的结果，避免翻页竞态 */
let listSeq = 0

async function loadStudents(): Promise<void> {
  const seq = ++listSeq
  loading.value = true
  loadError.value = false
  try {
    const page = await listMyStudentsApi({
      pageNum: pageNum.value,
      pageSize: PAGE_SIZE,
    })
    if (seq !== listSeq) return
    students.value = page.records
    total.value = page.total
  } catch {
    if (seq !== listSeq) return
    // 错误提示已由 request.ts 拦截器统一弹出；这里只落地错误态
    students.value = []
    total.value = 0
    loadError.value = true
  } finally {
    if (seq === listSeq) {
      loading.value = false
    }
  }
}

function onPageChange(page: number): void {
  pageNum.value = page
  void loadStudents()
}

function displayName(row: StudentVO): string {
  return row.realName || row.username || '—'
}

/** 操作后刷新；当前页被操作到空且不是第一页时回退一页 */
async function reloadAfterMutation(): Promise<void> {
  await loadStudents()
  if (!loadError.value && students.value.length === 0 && total.value > 0 && pageNum.value > 1) {
    pageNum.value -= 1
    await loadStudents()
  }
}

/* ------------------------------- 绑定/创建 ------------------------------- */

type BindMode = 'bind' | 'create'

const bindVisible = ref(false)
const bindMode = ref<BindMode>('bind')
const bindSaving = ref(false)
const bindFormRef = ref<FormInstance>()

const bindForm = reactive({
  username: '',
  realName: '',
  phone: '',
  password: '',
})

const bindRules = computed<FormRules>(() => {
  const rules: FormRules = {
    username: [
      { required: true, message: '请输入用户名', trigger: 'blur' },
      { max: 50, message: '用户名不能超过 50 个字符', trigger: 'blur' },
    ],
  }
  if (bindMode.value === 'create') {
    rules.realName = [
      { required: true, message: '请输入学员姓名', trigger: 'blur' },
      { max: 50, message: '姓名不能超过 50 个字符', trigger: 'blur' },
    ]
    rules.phone = [
      { required: true, message: '请输入手机号', trigger: 'blur' },
      { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
      { max: 20, message: '手机号不能超过 20 个字符', trigger: 'blur' },
    ]
    rules.password = [
      { required: true, message: '请输入初始密码', trigger: 'blur' },
      { min: 6, max: 64, message: '初始密码长度应为 6~64 位', trigger: 'blur' },
    ]
  }
  return rules
})

function openBind(): void {
  bindMode.value = 'bind'
  bindForm.username = ''
  bindForm.realName = ''
  bindForm.phone = ''
  bindForm.password = ''
  bindVisible.value = true
  void nextTick(() => bindFormRef.value?.clearValidate())
}

function onBindModeChange(): void {
  void nextTick(() => bindFormRef.value?.clearValidate())
}

async function submitBind(): Promise<void> {
  if (!bindFormRef.value || bindSaving.value) return
  const ok = await bindFormRef.value.validate().catch(() => false)
  if (!ok) return

  const data: StudentBindParams = {
    username: bindForm.username.trim(),
  }
  if (bindMode.value === 'create') {
    data.realName = bindForm.realName.trim()
    data.phone = bindForm.phone.trim()
    data.password = bindForm.password
  }

  bindSaving.value = true
  try {
    await bindOrCreateStudentApi(data)
    ElMessage.success('学员绑定操作已完成')
    bindVisible.value = false
    // 新绑定按 bind_time 倒序排在最前，回到第 1 页确保看到结果
    pageNum.value = 1
    await reloadAfterMutation()
  } catch {
    // 失败提示由拦截器统一弹出（1001/1105/1106/3101 等）
  } finally {
    bindSaving.value = false
  }
}

/* ------------------------------- 修改学情 ------------------------------- */

const editVisible = ref(false)
const editSaving = ref(false)
const editStudent = ref<StudentVO | null>(null)
const editFormRef = ref<FormInstance>()

const editForm = reactive({
  subjectCode: 'SUBJECT_1' as SubjectCode,
  phaseStatus: '' as SubjectPhaseStatus | '',
  coachRemark: '',
})

const editRules: FormRules = {
  subjectCode: [{ required: true, message: '请选择科目', trigger: 'change' }],
  coachRemark: [{ max: 500, message: '备注不能超过 500 个字符', trigger: 'blur' }],
}

function openEdit(row: StudentVO): void {
  if (unbindingId.value !== null) return
  editStudent.value = row
  editForm.subjectCode = row.currentSubjectCode ?? 'SUBJECT_1'
  editForm.phaseStatus = ''
  editForm.coachRemark = ''
  editVisible.value = true
  void nextTick(() => editFormRef.value?.clearValidate())
}

async function submitEdit(): Promise<void> {
  if (!editFormRef.value || !editStudent.value || editSaving.value) return
  const ok = await editFormRef.value.validate().catch(() => false)
  if (!ok) return

  const remark = editForm.coachRemark.trim()
  if (!editForm.phaseStatus && !remark) {
    ElMessage.warning('学习阶段和教练备注至少填写一项')
    return
  }

  const data: StudentUpdateParams = {
    subjectCode: editForm.subjectCode,
  }
  if (editForm.phaseStatus) {
    data.phaseStatus = editForm.phaseStatus
  }
  if (remark) {
    data.coachRemark = remark
  }

  const bindId = editStudent.value.bindId
  editSaving.value = true
  try {
    await updateStudentSubjectApi(bindId, data)
    ElMessage.success('学员学情已更新')
    editVisible.value = false
    await reloadAfterMutation()
    // 若当前正打开该学员的学习详情，用最新列表行更新当前科目后再刷新详情
    if (detailVisible.value && detailStudent.value?.bindId === bindId) {
      const updated = students.value.find((item) => item.bindId === bindId)
      if (updated) detailStudent.value = updated
      await detailRef.value?.reload()
    }
  } catch {
    // 失败提示由拦截器统一弹出（1001/3102 等）
  } finally {
    editSaving.value = false
  }
}

/* -------------------------------- 解绑 -------------------------------- */

async function unbind(row: StudentVO): Promise<void> {
  if (unbindingId.value !== null) return
  try {
    await ElMessageBox.confirm(
      `确定解除与「${displayName(row)}」的绑定吗？解绑后该学员不再出现在你的学员列表；` +
        '历史预约、学情记录和错题记录都会保留，已存在的预约不会被取消。',
      '解除绑定',
      { type: 'warning' },
    )
  } catch {
    return // 用户放弃
  }

  unbindingId.value = row.bindId
  try {
    await unbindStudentApi(row.bindId)
    ElMessage.success('已解除绑定')
    if (detailStudent.value?.bindId === row.bindId) {
      detailVisible.value = false
    }
    await reloadAfterMutation()
  } catch {
    // 失败提示由拦截器统一弹出（3102 等）
  } finally {
    unbindingId.value = null
  }
}

/* ------------------------------- 学习详情 ------------------------------- */

const detailVisible = ref(false)
const detailStudent = ref<StudentVO | null>(null)
const detailRef = ref<{ reload: () => Promise<void> } | null>(null)

function openDetail(row: StudentVO): void {
  if (unbindingId.value !== null) return
  detailStudent.value = row
  detailVisible.value = true
}

function onDetailConfirmed(payload: {
  bindId: number
  subjectCode: SubjectCode
  status: CoachConfirmStatus
}): void {
  const row = students.value.find((item) => item.bindId === payload.bindId)
  if (row) row.coachConfirmStatus = payload.status
  if (detailStudent.value?.bindId === payload.bindId) {
    detailStudent.value = { ...detailStudent.value, coachConfirmStatus: payload.status }
  }
}

/* -------------------------------- 初始化 -------------------------------- */

onMounted(() => {
  void loadStudents()
})
</script>

<template>
  <div class="csm">
    <div class="csm__head">
      <h2 class="csm__title">学员管理</h2>
      <span class="csm__desc">
        当前教练名下有效绑定的学员；列表显示当前学习科目和教练确认状态，学习详情、绑定/创建、
        修改学情和解绑均调用真实后端接口。
      </span>
      <el-button type="primary" class="csm__new" @click="openBind">绑定/创建学员</el-button>
    </div>

    <el-card shadow="never">
      <!-- 加载失败：错误态 + 重试 -->
      <el-result
        v-if="loadError"
        icon="error"
        title="学员列表加载失败"
        sub-title="请检查网络或稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="loadStudents">重试</el-button>
        </template>
      </el-result>

      <template v-else>
        <el-table v-loading="loading" :data="students" stripe style="width: 100%">
          <el-table-column label="学员" min-width="180">
            <template #default="{ row }">
              <div class="csm__name">{{ displayName(row) }}</div>
              <div class="csm__sub">{{ row.username }}</div>
            </template>
          </el-table-column>
          <el-table-column label="电话" min-width="140">
            <template #default="{ row }">
              {{ row.phone || '未填写' }}
            </template>
          </el-table-column>
          <el-table-column prop="bindTime" label="绑定时间" min-width="180" />
          <el-table-column label="当前学习科目" min-width="150">
            <template #default="{ row }">
              <div class="csm__name">{{ subjectLabel(row.currentSubjectCode) }}</div>
              <div class="csm__sub">{{ phaseLabel(row.currentPhaseStatus) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="教练确认" min-width="180">
            <template #default="{ row }">
              <el-tag :type="confirmMeta(row.coachConfirmStatus).type" size="small">
                {{ confirmMeta(row.coachConfirmStatus).label }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="360" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :disabled="unbindingId !== null"
                @click="openDetail(row)"
              >
                学习详情
              </el-button>
              <el-button
                link
                type="primary"
                :disabled="unbindingId !== null"
                @click="openEdit(row)"
              >
                修改学情
              </el-button>
              <el-button
                link
                type="danger"
                :loading="unbindingId === row.bindId"
                :disabled="unbindingId !== null && unbindingId !== row.bindId"
                @click="unbind(row)"
              >
                解绑
              </el-button>
            </template>
          </el-table-column>

          <template #empty>
            <el-empty description="暂无学员" />
          </template>
        </el-table>

        <div v-if="total > PAGE_SIZE" class="csm__pager">
          <el-pagination
            layout="prev, pager, next, total"
            :total="total"
            :page-size="PAGE_SIZE"
            :current-page="pageNum"
            @current-change="onPageChange"
          />
        </div>
      </template>
    </el-card>

    <!-- 绑定已有学员 / 创建新学员 -->
    <el-dialog
      v-model="bindVisible"
      title="绑定/创建学员"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="!bindSaving"
      :show-close="!bindSaving"
    >
      <el-radio-group v-model="bindMode" class="csm__mode" @change="onBindModeChange">
        <el-radio-button value="bind">绑定已有学员</el-radio-button>
        <el-radio-button value="create">创建新学员</el-radio-button>
      </el-radio-group>

      <el-alert
        v-if="bindMode === 'bind'"
        type="info"
        :closable="false"
        show-icon
        title="输入已存在学员的用户名即可绑定；用户名不存在时后端会返回参数错误。"
        class="csm__alert"
      />
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        title="新用户名将创建学员账号并绑定；若用户名已存在，后端会走绑定已有学员分支，姓名/手机号/密码不会覆盖原账号。"
        class="csm__alert"
      />

      <el-form ref="bindFormRef" :model="bindForm" :rules="bindRules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="bindForm.username" maxlength="50" placeholder="学员登录名" />
        </el-form-item>

        <template v-if="bindMode === 'create'">
          <el-form-item label="姓名" prop="realName">
            <el-input v-model="bindForm.realName" maxlength="50" placeholder="学员真实姓名" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="bindForm.phone" maxlength="20" placeholder="11 位手机号" />
          </el-form-item>
          <el-form-item label="初始密码" prop="password">
            <el-input
              v-model="bindForm.password"
              type="password"
              show-password
              maxlength="64"
              autocomplete="new-password"
              placeholder="6~64 位"
            />
          </el-form-item>
        </template>
      </el-form>

      <template #footer>
        <el-button :disabled="bindSaving" @click="bindVisible = false">取消</el-button>
        <el-button type="primary" :loading="bindSaving" @click="submitBind">
          提交
        </el-button>
      </template>
    </el-dialog>

    <!-- 修改学情 / 教练备注 -->
    <el-dialog
      v-model="editVisible"
      title="修改学员学情"
      width="560px"
      :close-on-click-modal="false"
      :close-on-press-escape="!editSaving"
      :show-close="!editSaving"
    >
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
        <el-form-item label="学员">
          <span v-if="editStudent">{{ displayName(editStudent) }}（{{ editStudent.username }}）</span>
        </el-form-item>
        <el-form-item label="科目" prop="subjectCode">
          <el-select v-model="editForm.subjectCode" style="width: 100%">
            <el-option
              v-for="opt in SUBJECT_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="学习阶段" prop="phaseStatus">
          <el-select v-model="editForm.phaseStatus" style="width: 100%">
            <el-option
              v-for="opt in PHASE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="教练备注" prop="coachRemark">
          <el-input
            v-model="editForm.coachRemark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="选填；当前备注无法读取，留空表示不修改"
          />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="学习阶段和教练备注至少填写一项；两者都留空将无法提交。"
        />
      </el-form>

      <template #footer>
        <el-button :disabled="editSaving" @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="submitEdit">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 学员学习详情：科目阶段 / 学情数据 / 错题 / AI 建议 -->
    <StudentLearningDialog
      ref="detailRef"
      v-model="detailVisible"
      :student="detailStudent"
      @confirmed="onDetailConfirmed"
    />
  </div>
</template>

<style scoped>
.csm__head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.csm__title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #263449;
}
.csm__desc {
  font-size: 13px;
  color: #687589;
}
.csm__new {
  margin-left: auto;
}
.csm__name {
  font-weight: 500;
  color: #263449;
}
.csm__sub {
  margin-top: 2px;
  font-size: 12px;
  color: #687589;
}
.csm__pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.csm__mode {
  margin-bottom: 12px;
}
.csm__alert {
  margin-bottom: 16px;
}
</style>
