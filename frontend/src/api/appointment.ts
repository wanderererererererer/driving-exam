import { http } from '@/utils/request'
import type {
  AppointmentCreateParams,
  AppointmentRejectParams,
  AppointmentStatus,
  AppointmentVO,
  CoachVO,
  PageResult,
} from '@/types'

/**
 * 预约模块接口（科二 / 科三共用）。
 *
 * <p>后端以当前 JWT 识别身份（学员 / 教练），**前端不传 userId**；
 * 角色由拦截器层 `PathRoleRule` 按路径前缀控制（学员 `/api/student/**`、教练 `/api/coach/**`）。</p>
 *
 * <p>通用约定（详见 `doc/appointment-design.md` / 后端 `AppointmentServiceImpl`）：</p>
 * <ul>
 *   <li>`status` 过滤值取 {@link AppointmentStatus} 枚举名（**全大写**，小写会 1001）；不传=不过滤；</li>
 *   <li>分页 `pageNum` 默认 1、`pageSize` 默认 10，**后端上限 20**；</li>
 *   <li>列表排序固定为 `create_time DESC, id DESC`（按操作时间倒序），前端不得自行重排；</li>
 *   <li>错误码：3001 不存在/越权、3002 时段冲突、3003 未绑定教练、3004 仅可取消待确认、3005 状态不允许操作；
 *       均由全局请求拦截器统一提示，页面无需手动处理。</li>
 * </ul>
 */

/** 学员端接口前缀 */
const STUDENT_BASE = '/student/appointment'

/** 教练端接口前缀 */
const COACH_BASE = '/coach/appointments'

/* ============================== 学员端 ============================== */

/**
 * 1. 我的教练列表（仅"有效绑定"的教练；教练账号被删除时后端会跳过）。
 *
 * <p>未绑定任何教练时返回**空数组**（不是错误）；创建预约时 `coachId` 必须来自该列表，
 * 否则后端返回 3003。</p>
 */
export function listMyCoachesApi() {
  return http.get<CoachVO[]>(`${STUDENT_BASE}/coaches`)
}

/**
 * 2. 创建预约。
 *
 * <p><b>返回 `Void`（不返回新建 id）</b>——创建成功后前端需**重新拉取列表**。
 * `timeSlot` 只能传固定四值，`appointmentDate` 为 `yyyy-MM-dd` 且不早于今天。</p>
 */
export function createAppointmentApi(data: AppointmentCreateParams) {
  return http.post<void>(STUDENT_BASE, data)
}

/**
 * 3. 我的预约列表（分页，仅本人）。
 *
 * @param params.status      可选；枚举名全大写，不传=全部
 * @param params.subjectCode 可选；不传=全部科目
 */
export function getMyAppointmentsApi(params: {
  status?: AppointmentStatus
  subjectCode?: string
  pageNum?: number
  pageSize?: number
}) {
  return http.get<PageResult<AppointmentVO>>(STUDENT_BASE, params)
}

/**
 * 4. 预约详情。
 *
 * <p>越权（他人预约）与不存在返回同一个 3001，不泄露存在性。</p>
 */
export function getMyAppointmentApi(id: number) {
  return http.get<AppointmentVO>(`${STUDENT_BASE}/${id}`)
}

/**
 * 5. 取消预约（**仅 PENDING**）。
 *
 * <p>`CONFIRMED` 之后需联系教练；非 PENDING 取消返回 3004。</p>
 */
export function cancelAppointmentApi(id: number) {
  return http.put<void>(`${STUDENT_BASE}/${id}/cancel`)
}

/* ============================== 教练端 ============================== */

/**
 * 6. 教练名下预约列表（分页，仅本人名下；**不支持科目筛选**）。
 *
 * @param params.status 可选；枚举名全大写，不传=全部（页面建议默认传 `PENDING` 作为"待处理"视图）
 */
export function listCoachAppointmentsApi(params: {
  status?: AppointmentStatus
  pageNum?: number
  pageSize?: number
}) {
  return http.get<PageResult<AppointmentVO>>(COACH_BASE, params)
}

/** 7. 审批通过（**仅 PENDING**，否则 3005）。 */
export function approveAppointmentApi(id: number) {
  return http.put<void>(`${COACH_BASE}/${id}/approve`)
}

/**
 * 8. 拒绝预约（**仅 PENDING**，否则 3005）。
 *
 * <p><b>请求体必须存在</b>（后端 `@RequestBody` 默认必填）：不填原因时也要传 `{}`，
 * 完全不传 body 会返回 1001。本函数默认参数 `{}` 即为此目的。</p>
 */
export function rejectAppointmentApi(id: number, params: AppointmentRejectParams = {}) {
  return http.put<void>(`${COACH_BASE}/${id}/reject`, params)
}

/** 9. 标记完成（**仅 CONFIRMED**，否则 3005）。 */
export function completeAppointmentApi(id: number) {
  return http.put<void>(`${COACH_BASE}/${id}/complete`)
}
