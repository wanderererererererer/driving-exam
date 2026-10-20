<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  confirmStudentSuggestionApi,
  generateStudentAiSuggestApi,
  getStudentAiSuggestApi,
  getStudentLearningDataApi,
  getStudentStatusApi,
  getStudentWrongBookApi,
} from '@/api/coachStudent'
import type {
  AiDegradedReason,
  AiSuggestVO,
  CoachConfirmStatus,
  ExamReadySuggest,
  LearningDataVO,
  StudentVO,
  SubjectCode,
  SubjectPhaseStatus,
  SubjectStatusVO,
  WrongBookCategory,
  WrongBookItemVO,
} from '@/types'

/**
 * 教练端学员学习详情弹窗。
 *
 * <p>复用现有学员管理页面，不新增路由；所有 {bindId} 都是 coach_student.id。
 * 学情、错题、AI 建议都走真实接口；AI 失败时展示后端返回的规则降级结果。</p>
 */
const props = defineProps<{
  modelValue: boolean
  student: StudentVO | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirmed', payload: { bindId: number; subjectCode: SubjectCode; status: CoachConfirmStatus }): void
}>()

type TagType = '' | 'success' | 'info' | 'warning' | 'danger'
type DetailTab = 'status' | 'learning' | 'wrong' | 'ai'

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const SUBJECT_NAMES: Record<SubjectCode, string> = {
  SUBJECT_1: '科目一',
  SUBJECT_2: '科目二',
  SUBJECT_3: '科目三',
  SUBJECT_4: '科目四',
}

const PHASE_META: Record<SubjectPhaseStatus, { label: string; type: TagType }> = {
  NOT_STARTED: { label: '未开始', type: 'info' },
  LEARNING: { label: '学习中', type: 'warning' },
  READY_FOR_EXAM: { label: '已具备考试条件', type: '' },
  EXAM_PASSED: { label: '考试通过', type: 'success' },
  EXAM_FAILED: { label: '考试未通过', type: 'danger' },
}

const CATEGORY_OPTIONS: { label: string; value: WrongBookCategory | '' }[] = [
  { label: '全部', value: '' },
  { label: '待攻克', value: 'PENDING' },
  { label: '高频错', value: 'HIGH_FREQUENCY' },
  { label: '已攻克', value: 'MASTERED' },
]

const CATEGORY_META: Record<WrongBookCategory, { label: string; type: TagType }> = {
  PENDING: { label: '待攻克', type: 'warning' },
  HIGH_FREQUENCY: { label: '高频错', type: 'danger' },
  MASTERED: { label: '已攻克', type: 'success' },
}

const SUGGEST_META: Record<ExamReadySuggest, { label: string; type: TagType }> = {
  READY: { label: '建议可考', type: 'success' },
  NEED_MORE: { label: '建议继续练习', type: 'warning' },
  NEED_HELP: { label: '建议重点辅导', type: 'danger' },
}

const CONFIRM_META: Record<CoachConfirmStatus, { label: string; type: TagType }> = {
  CONFIRMED_READY: { label: '已确认可参加考试', type: 'success' },
  NOT_READY: { label: '暂不建议参加考试', type: 'warning' },
  NEED_HELP: { label: '需要进一步辅导', type: 'danger' },
}

const CONFIRM_OPTIONS = Object.keys(CONFIRM_META) as CoachConfirmStatus[]

function categoryMeta(category: WrongBookCategory): { label: string; type: TagType } {
  return CATEGORY_META[category] ?? { label: category, type: 'info' }
}

function suggestMeta(value: ExamReadySuggest | null): { label: string; type: TagType } {
  return value ? SUGGEST_META[value] : { label: '—', type: 'info' }
}

function subjectName(code: SubjectCode): string {
  return SUBJECT_NAMES[code] ?? code
}

const DEGRADED_REASON_LABEL: Record<AiDegradedReason, string> = {
  AI_TIMEOUT: 'AI 服务响应超时',
  AI_CALL_FAILED: 'AI 服务调用失败',
  AI_QUOTA_EXCEEDED: 'AI 调用次数超限',
}

const activeTab = ref<DetailTab>('learning')
/** 只读：详情展示科目固定来自后端返回的当前进行科目，不提供修改入口。 */
const subject = computed<SubjectCode | null>(() => props.student?.currentSubjectCode ?? null)
const hasCurrentSubject = computed(() => subject.value !== null)

const statusLoading = ref(false)
const statusError = ref(false)
const statusList = ref<SubjectStatusVO[]>([])

const learningLoading = ref(false)
const learningError = ref(false)
const learningData = ref<LearningDataVO | null>(null)

const wrongLoading = ref(false)
const wrongError = ref(false)
const wrongList = ref<WrongBookItemVO[]>([])
const wrongTotal = ref(0)
const wrongPageNum = ref(1)
const wrongPageSize = 10
const wrongCategory = ref<WrongBookCategory | ''>('')

const aiLoading = ref(false)
const aiRestoring = ref(false)
const aiError = ref(false)
const aiRestoreError = ref(false)
const aiSuggestion = ref<AiSuggestVO | null>(null)
const aiFromSaved = ref(false)

const confirmStatus = ref<CoachConfirmStatus | ''>('')
const confirmSaving = ref(false)

let statusSeq = 0
let learningSeq = 0
let wrongSeq = 0
let aiSeq = 0

const studentName = computed(() => {
  const s = props.student
  return s ? s.realName || s.username || '—' : '学员'
})

const currentSubjectLabel = computed(() => {
  const code = subject.value
  return code ? SUBJECT_NAMES[code] ?? code : '暂无进行中科目'
})

const subjectLabel = computed(() => currentSubjectLabel.value)

const wrongCategoryLabel = computed(() => {
  return wrongCategory.value ? CATEGORY_META[wrongCategory.value].label : '全部'
})

function phaseMeta(status: SubjectPhaseStatus) {
  return PHASE_META[status] ?? { label: status, type: 'info' as TagType }
}

function percent(value: number | null | undefined): string {
  return value == null ? '—' : `${Math.round(value * 100)}%`
}

function formatDuration(seconds: number | null): string {
  if (seconds == null) return '—'
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}分${s}秒`
}

function reasonLabel(reason: string | null): string {
  if (reason === 'LOW_MASTERY') return '掌握度偏低'
  if (reason === 'BASE_WEAK') return '基础不牢'
  return '—'
}

function degradedTitle(reason: AiDegradedReason | null, fromSaved = false): string {
  if (fromSaved) return '已保存的规则建议（历史 AI 失败原因未记录）'
  if (!reason) return 'AI 暂不可用，已返回规则建议'
  return `${DEGRADED_REASON_LABEL[reason]}，已返回规则建议`
}

async function loadStatus(bindId: number): Promise<void> {
  const seq = ++statusSeq
  statusLoading.value = true
  statusError.value = false
  statusList.value = []
  try {
    const data = await getStudentStatusApi(bindId)
    if (seq !== statusSeq) return
    statusList.value = data
  } catch {
    if (seq !== statusSeq) return
    statusList.value = []
    statusError.value = true
  } finally {
    if (seq === statusSeq) statusLoading.value = false
  }
}

async function loadLearning(bindId: number): Promise<void> {
  const seq = ++learningSeq
  learningLoading.value = true
  learningError.value = false
  learningData.value = null
  const requestSubject = subject.value
  if (!requestSubject) {
    if (seq === learningSeq) learningLoading.value = false
    return
  }
  try {
    const data = await getStudentLearningDataApi(bindId, requestSubject)
    if (seq !== learningSeq || subject.value !== requestSubject) return
    learningData.value = data
  } catch {
    if (seq !== learningSeq || subject.value !== requestSubject) return
    learningData.value = null
    learningError.value = true
  } finally {
    if (seq === learningSeq) learningLoading.value = false
  }
}

async function loadWrongBook(bindId: number): Promise<void> {
  const seq = ++wrongSeq
  wrongLoading.value = true
  wrongError.value = false
  const requestSubject = subject.value
  if (!requestSubject) {
    wrongList.value = []
    wrongTotal.value = 0
    if (seq === wrongSeq) wrongLoading.value = false
    return
  }
  try {
    const page = await getStudentWrongBookApi(bindId, {
      subjectCode: requestSubject,
      category: wrongCategory.value || undefined,
      pageNum: wrongPageNum.value,
      pageSize: wrongPageSize,
    })
    if (seq !== wrongSeq || subject.value !== requestSubject) return
    wrongList.value = page.records
    wrongTotal.value = page.total
    wrongPageNum.value = page.pageNum
  } catch {
    if (seq !== wrongSeq || subject.value !== requestSubject) return
    wrongList.value = []
    wrongTotal.value = 0
    wrongError.value = true
  } finally {
    if (seq === wrongSeq) wrongLoading.value = false
  }
}

function resetAi(): void {
  aiSeq += 1
  aiLoading.value = false
  aiRestoring.value = false
  aiSuggestion.value = null
  aiError.value = false
  aiRestoreError.value = false
  aiFromSaved.value = false
}

/** 打开详情/切换科目时只读已保存建议；绝不触发生成接口。 */
async function loadSavedAi(bindId: number, requestSubject: SubjectCode | null): Promise<void> {
  const seq = ++aiSeq
  aiRestoring.value = true
  aiRestoreError.value = false
  if (!requestSubject) {
    aiSuggestion.value = null
    aiFromSaved.value = false
    if (seq === aiSeq) aiRestoring.value = false
    return
  }
  try {
    const data = await getStudentAiSuggestApi(bindId, requestSubject)
    if (seq !== aiSeq || props.student?.bindId !== bindId || subject.value !== requestSubject) return
    aiSuggestion.value = data
    aiFromSaved.value = data != null
  } catch {
    if (seq !== aiSeq || props.student?.bindId !== bindId || subject.value !== requestSubject) return
    aiSuggestion.value = null
    aiFromSaved.value = false
    aiRestoreError.value = true
  } finally {
    if (seq === aiSeq) aiRestoring.value = false
  }
}

function restoreSavedAi(): void {
  if (!props.student) return
  void loadSavedAi(props.student.bindId, subject.value)
}

function syncConfirmStatus(): void {
  confirmStatus.value = hasCurrentSubject.value
      ? (props.student?.coachConfirmStatus ?? '')
      : ''
}

async function init(): Promise<void> {
  if (!props.student) return
  const bindId = props.student.bindId
  activeTab.value = 'learning'
  wrongCategory.value = ''
  wrongPageNum.value = 1
  resetAi()
  syncConfirmStatus()
  await Promise.all([
    loadStatus(bindId),
    loadLearning(bindId),
    loadWrongBook(bindId),
    loadSavedAi(bindId, subject.value),
  ])
}

async function onConfirmChange(value: CoachConfirmStatus | ''): Promise<void> {
  if (!props.student || !hasCurrentSubject.value || !value || confirmSaving.value) return
  const requestSubject = subject.value
  if (!requestSubject) return

  const previous = props.student.coachConfirmStatus ?? ''
  confirmSaving.value = true
  try {
    await confirmStudentSuggestionApi(props.student.bindId, requestSubject, value)
    ElMessage.success(`教练确认已更新：${CONFIRM_META[value].label}`)
    emit('confirmed', {
      bindId: props.student.bindId,
      subjectCode: requestSubject,
      status: value,
    })
  } catch {
    // 3102/1001 等由请求拦截器显示后端 message；失败时回退显示
    confirmStatus.value = previous
  } finally {
    confirmSaving.value = false
  }
}

function onWrongCategoryChange(): void {
  if (!props.student) return
  wrongPageNum.value = 1
  void loadWrongBook(props.student.bindId)
}

function onWrongPageChange(page: number): void {
  if (!props.student) return
  wrongPageNum.value = page
  void loadWrongBook(props.student.bindId)
}

async function generateAi(): Promise<void> {
  const requestSubject = subject.value
  if (!props.student || !requestSubject || aiLoading.value || aiRestoring.value) return
  const bindId = props.student.bindId
  const seq = ++aiSeq
  aiLoading.value = true
  aiRestoreError.value = false
  aiError.value = false
  aiFromSaved.value = false
  aiSuggestion.value = null
  try {
    const data = await generateStudentAiSuggestApi(bindId, requestSubject)
    if (seq !== aiSeq || props.student?.bindId !== bindId || subject.value !== requestSubject) return
    aiSuggestion.value = data
    if (data.source === 'RULE' || data.degraded) {
      ElMessage.warning('AI 暂不可用，已生成规则建议')
    } else {
      ElMessage.success('AI 学习建议已生成')
    }
  } catch {
    if (seq !== aiSeq || props.student?.bindId !== bindId || subject.value !== requestSubject) return
    // 错误提示由 request.ts 统一弹出；这里保留可重试的页面错误态
    aiError.value = true
  } finally {
    if (seq === aiSeq) aiLoading.value = false
  }
}

/** 打开或主动点击 AI 学习建议入口：每次点击都直接发起一次生成请求。 */
function openAiSuggest(): void {
  if (!hasCurrentSubject.value) {
    ElMessage.warning('该学员暂无进行中科目，无法生成 AI 建议')
    return
  }
  activeTab.value = 'ai'
  void generateAi()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) void init()
  },
)

watch(
  () => props.student?.bindId,
  () => {
    if (props.modelValue) void init()
  },
)

defineExpose({ reload: init })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="`${studentName} · 学习详情`"
    width="920px"
    :close-on-click-modal="false"
    append-to-body
    destroy-on-close
  >
    <div class="sld">
      <div class="sld__identity">
        <span>学员：{{ studentName }}</span>
        <span>当前学习科目：{{ currentSubjectLabel }}</span>
      </div>

      <div class="sld__toolbar">
        <span class="sld__label">查看科目</span>
        <span class="sld__subject">{{ currentSubjectLabel }}</span>
        <span class="sld__hint">详情只读；修改当前科目/学习阶段请使用学员列表的“修改学情”。</span>
        <el-button
          type="primary"
          class="sld__ai-entry"
          :loading="aiLoading"
          :disabled="!hasCurrentSubject || aiRestoring"
          @click="openAiSuggest"
        >
          AI 学习建议
        </el-button>
      </div>

      <div class="sld__confirm-row">
        <span class="sld__label">教练确认</span>
        <el-select
          v-model="confirmStatus"
          class="sld__confirm-select"
          placeholder="待确认"
          :disabled="!hasCurrentSubject || confirmSaving"
          :loading="confirmSaving"
          @change="onConfirmChange"
        >
          <el-option label="待确认" value="" disabled />
          <el-option
            v-for="item in CONFIRM_OPTIONS"
            :key="item"
            :label="CONFIRM_META[item].label"
            :value="item"
          />
        </el-select>
        <span class="sld__hint">
          {{
            hasCurrentSubject
              ? '确认只作用于当前学习科目；选择实际结果后才会保存。'
              : '暂无进行中科目，无法确认。'
          }}
        </span>
      </div>

      <el-alert
        v-if="!hasCurrentSubject"
        type="info"
        :closable="false"
        show-icon
        title="该学员暂无进行中科目"
        description="学习数据、错题和 AI 建议仅针对当前进行科目；请先在学员列表的“修改学情”中设置。"
      />

      <el-tabs v-model="activeTab">
        <!-- 科目阶段：保留原有四科学情只读表 -->
        <el-tab-pane label="科目阶段" name="status">
          <div v-loading="statusLoading" class="sld__pane">
            <el-alert
              v-if="statusError"
              type="error"
              :closable="false"
              show-icon
              title="科目阶段加载失败"
            >
              <el-button
                size="small"
                type="primary"
                plain
                @click="props.student && loadStatus(props.student.bindId)"
              >
                重试
              </el-button>
            </el-alert>

            <el-table v-else :data="statusList" size="small" style="width: 100%">
              <el-table-column label="科目" width="100">
                <template #default="{ row }">{{ subjectName(row.subjectCode) }}</template>
              </el-table-column>
              <el-table-column label="学习阶段" min-width="140">
                <template #default="{ row }">
                  <el-tag :type="phaseMeta(row.status).type" size="small">
                    {{ phaseMeta(row.status).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="mockPassCount" label="模拟考连续及格" width="130" />
              <el-table-column label="最近模考成绩" width="120">
                <template #default="{ row }">{{ row.lastMockScore ?? '—' }}</template>
              </el-table-column>
              <template #empty>
                <el-empty description="暂无可展示的科目阶段数据" />
              </template>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 学情数据：真实 learning-data -->
        <el-tab-pane label="学情数据" name="learning">
          <div v-loading="learningLoading" class="sld__pane">
            <el-alert
              v-if="learningError"
              type="error"
              :closable="false"
              show-icon
              title="学习数据加载失败"
            >
              <el-button
                size="small"
                type="primary"
                plain
                @click="props.student && loadLearning(props.student.bindId)"
              >
                重试
              </el-button>
            </el-alert>

            <template v-else-if="learningData">
              <el-row :gutter="12" class="sld__cards">
                <el-col :span="5">
                  <el-card shadow="never" class="sld__metric">
                    <div class="sld__metric-label">题目总数</div>
                    <div class="sld__metric-value">{{ learningData.overview.totalQuestions }}</div>
                  </el-card>
                </el-col>
                <el-col :span="5">
                  <el-card shadow="never" class="sld__metric">
                    <div class="sld__metric-label">已答题数</div>
                    <div class="sld__metric-value">{{ learningData.overview.answeredQuestions }}</div>
                  </el-card>
                </el-col>
                <el-col :span="5">
                  <el-card shadow="never" class="sld__metric">
                    <div class="sld__metric-label">正确率</div>
                    <div class="sld__metric-value">{{ percent(learningData.overview.correctRate) }}</div>
                  </el-card>
                </el-col>
                <el-col :span="5">
                  <el-card shadow="never" class="sld__metric">
                    <div class="sld__metric-label">练习进度</div>
                    <div class="sld__metric-value">{{ percent(learningData.overview.progress) }}</div>
                  </el-card>
                </el-col>
                <el-col :span="4">
                  <el-card shadow="never" class="sld__metric">
                    <div class="sld__metric-label">薄弱知识点</div>
                    <div class="sld__metric-value">{{ learningData.overview.weakCount }}</div>
                  </el-card>
                </el-col>
              </el-row>

              <div class="sld__section-title">错题概况</div>
              <div class="sld__tags">
                <el-tag type="warning">待攻克 {{ learningData.wrongBookSummary.pending }}</el-tag>
                <el-tag type="danger">高频错 {{ learningData.wrongBookSummary.highFrequency }}</el-tag>
                <el-tag type="success">已攻克 {{ learningData.wrongBookSummary.mastered }}</el-tag>
                <el-tag type="info">合计 {{ learningData.wrongBookSummary.total }}</el-tag>
              </div>

              <div class="sld__section-title">薄弱知识点</div>
              <el-table :data="learningData.weakKnowledgePoints" size="small" style="width: 100%">
                <el-table-column prop="knowledgePointName" label="知识点" min-width="180" />
                <el-table-column label="掌握度" width="100">
                  <template #default="{ row }">{{ percent(row.mastery) }}</template>
                </el-table-column>
                <el-table-column prop="answeredCount" label="答题数" width="90" />
                <el-table-column label="短板原因" width="120">
                  <template #default="{ row }">{{ reasonLabel(row.weakReason) }}</template>
                </el-table-column>
                <template #empty>
                  <el-empty description="暂无薄弱知识点" />
                </template>
              </el-table>

              <div class="sld__section-title">
                模拟考试历史
                <span class="sld__hint">
                  （共 {{ learningData.mockExamHistory.total }} 场，当前显示最近
                  {{ learningData.mockExamHistory.records.length }} 场）
                </span>
              </div>
              <el-table :data="learningData.mockExamHistory.records" size="small" style="width: 100%">
                <el-table-column prop="examId" label="考试 ID" width="90" />
                <el-table-column prop="paperTitle" label="试卷" min-width="180" />
                <el-table-column prop="score" label="得分" width="80" />
                <el-table-column label="结果" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.isPass ? 'success' : 'danger'" size="small">
                      {{ row.isPass ? '及格' : '未及格' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="submitTime" label="交卷时间" width="170" />
                <el-table-column label="用时" width="100">
                  <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
                </el-table-column>
                <template #empty>
                  <el-empty description="暂无已结束的模拟考试" />
                </template>
              </el-table>
            </template>
          </div>
        </el-tab-pane>

        <!-- 错题：真实 coach wrong-book -->
        <el-tab-pane label="错题" name="wrong">
          <div class="sld__pane">
            <div class="sld__filter">
              <span class="sld__label">分类</span>
              <el-radio-group v-model="wrongCategory" @change="onWrongCategoryChange">
                <el-radio-button
                  v-for="opt in CATEGORY_OPTIONS"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.label }}
                </el-radio-button>
              </el-radio-group>
              <span class="sld__hint">当前：{{ wrongCategoryLabel }}</span>
            </div>

            <el-alert
              v-if="wrongError"
              type="error"
              :closable="false"
              show-icon
              title="错题加载失败"
            >
              <el-button
                size="small"
                type="primary"
                plain
                @click="props.student && loadWrongBook(props.student.bindId)"
              >
                重试
              </el-button>
            </el-alert>

            <el-table v-else v-loading="wrongLoading" :data="wrongList" size="small" style="width: 100%">
              <el-table-column prop="content" label="题干" min-width="280" show-overflow-tooltip />
              <el-table-column prop="questionType" label="题型" width="90" />
              <el-table-column prop="wrongCount" label="错误次数" width="90" />
              <el-table-column label="分类" width="100">
                <template #default="{ row }">
                  <el-tag :type="categoryMeta(row.category).type" size="small">
                    {{ categoryMeta(row.category).label }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="lastWrongTime" label="最近答错" width="170" />
              <template #empty>
                <el-empty description="该分类下暂无错题" />
              </template>
            </el-table>

            <div v-if="wrongTotal > wrongPageSize" class="sld__pager">
              <el-pagination
                layout="prev, pager, next, total"
                :total="wrongTotal"
                :page-size="wrongPageSize"
                :current-page="wrongPageNum"
                @current-change="onWrongPageChange"
              />
            </div>
          </div>
        </el-tab-pane>

        <!-- AI 建议：真实 ai-suggest；教练确认不在本区域修改 -->
        <el-tab-pane label="AI 建议" name="ai">
          <div v-loading="aiRestoring" class="sld__pane">
            <el-alert
              v-if="aiRestoreError"
              type="error"
              :closable="false"
              show-icon
              title="已保存建议加载失败"
            >
              <el-button size="small" type="primary" plain @click="restoreSavedAi">重试</el-button>
            </el-alert>

            <el-alert
              v-if="aiError"
              type="error"
              :closable="false"
              show-icon
              title="AI 建议生成失败"
            >
              <el-button size="small" type="primary" plain @click="generateAi">重试</el-button>
            </el-alert>

            <div class="sld__ai-head">
              <div>
                <div class="sld__section-title sld__section-title--inline">AI 学习建议</div>
                <div class="sld__hint">
                  当前科目：{{ subjectLabel }}。此处显示已保存建议；点击顶部“AI 学习建议”入口才会生成新建议。
                </div>
              </div>
            </div>

            <el-empty
              v-if="!aiSuggestion && !aiLoading && !aiRestoring"
              description="尚无已保存建议；点击顶部 AI 学习建议生成"
            />

            <template v-else-if="aiSuggestion">
              <el-alert
                v-if="aiSuggestion.degraded"
                type="warning"
                :closable="false"
                show-icon
                :title="degradedTitle(aiSuggestion.degradedReason, aiFromSaved)"
              >
                <template #default>
                  <template v-if="aiSuggestion.suggestion">
                    以下内容由规则生成，不是 AI 成功返回的结果；确认后仍会记录教练确认状态。
                  </template>
                  <template v-else>
                    该建议只保存了规则等级，历史规则文本未持久化；可查看建议等级或重新生成建议。
                  </template>
                </template>
              </el-alert>

              <div class="sld__tags">
                <el-tag v-if="aiFromSaved" type="info">已保存</el-tag>
                <el-tag :type="aiSuggestion.source === 'AI' ? 'success' : 'warning'">
                  {{ aiSuggestion.source === 'AI' ? 'AI 生成' : '规则降级' }}
                </el-tag>
                <el-tag :type="suggestMeta(aiSuggestion.examReadySuggest).type">
                  {{ suggestMeta(aiSuggestion.examReadySuggest).label }}
                </el-tag>
                <span v-if="aiSuggestion.aiSuggestTime" class="sld__hint">
                  生成时间：{{ aiSuggestion.aiSuggestTime }}
                </span>
              </div>

              <el-card v-if="aiSuggestion.suggestion" shadow="never" class="sld__suggestion">
                <div class="sld__suggestion-text">{{ aiSuggestion.suggestion }}</div>
              </el-card>
              <el-alert
                v-else
                type="info"
                :closable="false"
                show-icon
                title="建议文本未保存"
                description="后端当前只持久化了规则建议等级；完整规则文本未保存，不能从历史数据还原。"
              />

              <el-alert
                type="info"
                :closable="false"
                show-icon
                title="教练确认状态请在学员列表操作"
                description="AI 建议与教练确认是两个独立职责；本区域不会修改确认状态。"
              />
            </template>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <template #footer>
      <el-button @click="visible = false">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.sld {
  min-height: 360px;
}
.sld__identity {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  color: #263449;
  font-size: 13px;
}
.sld__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f1f4f9;
}
.sld__ai-entry {
  margin-left: auto;
}
.sld__subject {
  color: #263449;
  font-weight: 500;
}
.sld__confirm-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0 0;
}
.sld__confirm-select {
  width: 220px;
}
.sld__label {
  color: #4a5a70;
  font-size: 13px;
}
.sld__hint {
  color: #687589;
  font-size: 12px;
}
.sld__pane {
  min-height: 260px;
  padding-top: 4px;
}
.sld__cards {
  margin-bottom: 18px;
}
.sld__metric {
  text-align: center;
}
.sld__metric-label {
  color: #687589;
  font-size: 12px;
}
.sld__metric-value {
  margin-top: 6px;
  color: #263449;
  font-size: 20px;
  font-weight: 600;
}
.sld__section-title {
  margin: 16px 0 10px;
  color: #263449;
  font-size: 14px;
  font-weight: 600;
}
.sld__section-title--inline {
  margin: 0 0 4px;
}
.sld__tags {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin: 8px 0 14px;
}
.sld__filter {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.sld__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
.sld__ai-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.sld__suggestion {
  background: #f8fafc;
}
.sld__suggestion-text {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #263449;
}
</style>
