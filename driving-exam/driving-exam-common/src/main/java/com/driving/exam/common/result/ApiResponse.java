package com.driving.exam.common.result;

import com.driving.exam.common.exception.ErrorCode;

import java.io.Serializable;

/**
 * 统一返回结构。仅 code / message / data 三个字段。
 *
 * @param <T> 业务数据类型
 */
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码，200 表示成功 */
    private Integer code;

    /** 提示信息 */
    private String message;

    /** 业务数据，无数据时为 null（保留字段） */
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /* ----------------------------- 成功 ----------------------------- */

    /** 成功，无数据 */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    /** 成功，带数据 */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /** 成功，自定义提示 + 数据 */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    /* ----------------------------- 失败 ----------------------------- */

    /** 失败，默认系统异常 */
    public static <T> ApiResponse<T> error() {
        return new ApiResponse<>(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage(), null);
    }

    /** 失败，自定义提示（默认系统异常码） */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ErrorCode.SYSTEM_ERROR.getCode(), message, null);
    }

    /** 失败，指定码与提示 */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /** 失败，按错误码枚举 */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 失败，枚举码 + 覆盖提示 */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }

    /* ---------------------------- getter / setter ---------------------------- */

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
