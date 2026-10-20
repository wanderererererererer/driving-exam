package com.driving.exam.common.exception;

/**
 * 业务错误码。
 *
 * <p>通用段仅保留 200 / 401 / 403 / 500，其余全部归入业务段，两段不混用。</p>
 */
public enum ErrorCode {

    /* --------------------------- 通用段（仅 4 个） --------------------------- */
    SUCCESS(200, "操作成功"),
    UNAUTHORIZED(401, "未认证或登录已失效"),
    FORBIDDEN(403, "无权限访问"),
    SYSTEM_ERROR(500, "系统异常，请稍后再试"),

    /* ------------------------- 1001-1009 通用请求错误 ------------------------ */
    PARAM_ERROR(1001, "参数校验失败"),
    RESOURCE_NOT_FOUND(1002, "请求的资源不存在"),
    DATA_CONFLICT(1003, "数据已存在或冲突"),
    OPERATION_NOT_ALLOWED(1004, "操作不允许"),

    /* -------------------------- 1101-1199 认证与用户 ------------------------- */
    LOGIN_FAILED(1101, "用户名或密码错误"),
    ACCOUNT_DISABLED(1102, "账号已被禁用"),
    TOKEN_INVALID(1103, "Token 无效或已过期"),
    OLD_PASSWORD_ERROR(1104, "原密码不正确"),
    USERNAME_EXISTS(1105, "用户名已存在"),
    ROLE_MISMATCH(1106, "该账号与所选角色不匹配"),

    /* --------------------------- 2001-2099 题库/知识点 ------------------------ */
    KNOWLEDGE_POINT_NOT_FOUND(2001, "知识点不存在"),
    QUESTION_NOT_FOUND(2002, "题目不存在"),
    WRONG_BOOK_NOT_FOUND(2003, "错题记录不存在"),

    /* --------------------------- 2101-2199 试卷/考试 ------------------------- */
    PAPER_NOT_FOUND(2101, "试卷不存在"),
    EXAM_SUBMITTED(2102, "考试已交卷"),
    EXAM_NO_PERMISSION(2103, "无权访问该试卷"),
    EXAM_NOT_FOUND(2104, "考试记录不存在"),
    EXAM_ONGOING(2105, "考试尚未结束"),
    EXAM_PAPER_INVALID(2106, "试卷数据异常，无法判分"),

    /* ----------------------------- 3001-3099 预约 --------------------------- */
    APPOINTMENT_NOT_FOUND(3001, "预约不存在"),
    APPOINTMENT_TIME_CONFLICT(3002, "该时段已被预约"),
    STUDENT_NOT_BOUND(3003, "学员未绑定教练"),
    APPOINTMENT_CANCEL_NOT_ALLOWED(3004, "仅可取消待确认的预约"),
    APPOINTMENT_STATUS_ILLEGAL(3005, "当前预约状态不允许此操作"),

    /* ------------------------- 3101-3199 教练-学员关系 ------------------------ */
    STUDENT_ALREADY_BOUND(3101, "该学员已绑定"),
    BINDING_NOT_FOUND(3102, "绑定关系不存在"),

    /* ------------------------------ 4001-4099 AI ---------------------------- */
    AI_CALL_FAILED(4001, "AI 服务调用失败"),
    AI_TIMEOUT(4002, "AI 服务响应超时"),
    AI_QUOTA_EXCEEDED(4003, "AI 调用次数超限"),
    AI_RECORD_NOT_FOUND(4004, "AI 问答记录不存在"),
    AI_RAG_UNAVAILABLE(4005, "知识库检索服务不可用，请稍后重试"),
    RAG_INGEST_FAILED(4006, "知识库入库失败"),

    /* ----------------------------- 5001-5099 聊天 --------------------------- */
    CHAT_SESSION_NOT_FOUND(5001, "会话不存在"),
    CHAT_NO_PERMISSION(5002, "无权访问该会话");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
