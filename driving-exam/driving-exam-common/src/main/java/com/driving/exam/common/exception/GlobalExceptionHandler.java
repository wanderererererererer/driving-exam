package com.driving.exam.common.exception;

import com.driving.exam.common.result.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * <p>HTTP 状态码策略：业务失败统一 HTTP 200 + body code；仅 401 / 403 返回真实 HTTP 状态码，
 * 供前端 Axios 拦截器自动清 token 并跳登录。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{} {}] code={} msg={}",
                request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return build(e.getCode(), e.getMessage());
    }

    /** @RequestBody 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                          HttpServletRequest request) {
        String msg = firstFieldError(e.getBindingResult().getFieldError());
        log.warn("参数校验失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), msg);
        return build(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /** 表单/查询参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e, HttpServletRequest request) {
        String msg = firstFieldError(e.getBindingResult().getFieldError());
        log.warn("参数绑定失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), msg);
        return build(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e,
                                                                HttpServletRequest request) {
        log.warn("缺少参数 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getParameterName());
        return build(ErrorCode.PARAM_ERROR.getCode(), "缺少必要参数：" + e.getParameterName());
    }

    /**
     * 请求体缺失或 JSON 格式错误（如 {@code @RequestBody} 必填但未传、或传了非法 JSON）。
     *
     * <p>不处理的话会落到兜底分支返回 500——那是不对的，这属于**参数错误**（1001）。
     * {@code @Valid} 校验的是"字段值"，本分支管的是"body 本身读不出来"，两者互补。</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(HttpMessageNotReadableException e,
                                                                      HttpServletRequest request) {
        log.warn("请求体不可读 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return build(ErrorCode.PARAM_ERROR.getCode(), "请求体缺失或格式不正确");
    }

    /**
     * 请求参数类型不匹配：如枚举参数传了非法值（category=XXX）、
     * 数值/日期参数传了非数字（knowledgePointId=abc）。
     *
     * <p>不处理的话会落到兜底分支返回 500——那是不对的，这属于**参数错误**。</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                                HttpServletRequest request) {
        log.warn("参数类型不匹配 [{} {}]: {}={}",
                request.getMethod(), request.getRequestURI(), e.getName(), e.getValue());
        return build(ErrorCode.PARAM_ERROR.getCode(), "参数类型不匹配：" + e.getName());
    }

    /** 上传文件超过 Spring multipart 限制 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e,
                                                                 HttpServletRequest request) {
        log.warn("上传文件过大 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return build(ErrorCode.PARAM_ERROR.getCode(), "上传文件过大，最大支持 10MB");
    }

    /** 缺少 multipart 文件字段（例如 Excel 导入未选择文件） */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException e,
                                                               HttpServletRequest request) {
        log.warn("缺少上传文件字段 [{} {}]: {}", request.getMethod(), request.getRequestURI(),
                e.getRequestPartName());
        return build(ErrorCode.PARAM_ERROR.getCode(), "缺少上传文件字段：" + e.getRequestPartName());
    }

    /** 其它 multipart 解析异常，统一作为参数错误反馈，避免落到通用 500 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException e,
                                                             HttpServletRequest request) {
        log.warn("文件上传解析失败 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return build(ErrorCode.PARAM_ERROR.getCode(), "文件上传失败，请检查文件格式");
    }

    /** 请求路径无对应资源（Spring Boot 3.2 静态资源未命中）：返回 404，不打完整堆栈 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e,
                                                                   HttpServletRequest request) {
        log.warn("资源不存在 [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{} {}]", request.getMethod(), request.getRequestURI(), e);
        return build(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
    }

    private String firstFieldError(FieldError fieldError) {
        return fieldError == null ? ErrorCode.PARAM_ERROR.getMessage() : fieldError.getDefaultMessage();
    }

    /** 401 / 403 返回真实 HTTP 状态码，其余统一 HTTP 200 */
    private ResponseEntity<ApiResponse<Void>> build(Integer code, String message) {
        HttpStatus status = HttpStatus.OK;
        if (ErrorCode.UNAUTHORIZED.getCode().equals(code)) {
            status = HttpStatus.UNAUTHORIZED;
        } else if (ErrorCode.FORBIDDEN.getCode().equals(code)) {
            status = HttpStatus.FORBIDDEN;
        }
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }
}
