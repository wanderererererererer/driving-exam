package com.driving.exam.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 绑定 / 创建学员入参（{@code POST /api/coach/students}）。
 *
 * <p><b>同一入口、两种模式</b>——由 {@code username} 是否已存在于 {@code sys_user} 分流：</p>
 * <ul>
 *   <li><b>绑定已有学员</b>：只传 {@code username} 即可；<b>不要求</b>重新提供姓名、电话与初始密码
 *       （这些已在库中）。</li>
 *   <li><b>创建新学员</b>：{@code username}、{@code realName}、{@code phone}、{@code password}
 *       <b>四项均必填</b>。</li>
 * </ul>
 *
 * <p><b>★ 为什么这里只有 {@code username} 是 {@code @NotBlank}</b>：本 DTO 同时服务两种模式，
 * 若把创建模式才需要的字段设成<b>无条件必填</b>，绑定模式就会被误拦。
 * 因此"创建模式四项必填"的判定<b>刻意下沉到 Service 手写判空</b>（缺失即抛既有的
 * {@code PARAM_ERROR(1001)}），而不使用 Bean Validation 分组——这是本次拍板的实现方式
 * （见 {@code doc/coach-student-design.md} §八 Q13）。</p>
 *
 * <p><b>密码</b>：仅创建模式使用；由 Service 用项目既有的 {@code PasswordEncoder}（BCrypt）加密后入库，
 * <b>不返回明文、不写日志</b>。{@code @Size} 在字段为 {@code null} 时不生效，故与"绑定模式不传密码"兼容。</p>
 *
 * <p>注：{@code @Pattern} 是<b>整串匹配</b>（内部用 {@code Matcher.matches()}），故正则无需写 {@code ^...$}。</p>
 */
@Data
public class StudentBindDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 学员登录名（两种模式都必填；已存在则绑定，不存在则创建） */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名不能超过 50 个字符")
    private String username;

    /** 学员真实姓名（<b>仅创建模式必填</b>，由 Service 判空；绑定模式可为 null） */
    @Size(max = 50, message = "姓名不能超过 50 个字符")
    private String realName;

    /** 学员手机号（<b>仅创建模式必填</b>，由 Service 判空；绑定模式可为 null） */
    @Pattern(regexp = "1[3-9]\\d{9}", message = "手机号格式不正确")
    @Size(max = 20, message = "手机号不能超过 20 个字符")
    private String phone;

    /** 初始密码（<b>仅创建模式必填并使用</b>，由 Service 判空；绑定模式可为 null） */
    @Size(min = 6, max = 64, message = "初始密码长度应为 6~64 位")
    private String password;
}
