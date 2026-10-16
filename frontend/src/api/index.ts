/**
 * 接口层（api）
 *
 * Day 4 起按模块拆分，例如：
 *   auth.ts        认证（登录/登出/当前用户）
 *   question.ts    题库与刷题
 *   exam.ts        模拟考试
 *   appointment.ts 预约
 *   chat.ts        教练-学员聊天
 *   ai.ts          AI 问答
 *   等
 *
 * 统一通过 `@/utils/request` 中的 http 发起请求，返回值已是解包后的业务数据。
 */
export {}
