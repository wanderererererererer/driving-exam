package com.driving.exam.server.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员端请求/响应 DTO 集合。
 *
 * <p>集中放置以减少简单 CRUD 的模板文件；字段与前端页面一一对应。
 * 管理员预约接口复用既有 {@code AppointmentVO}，用户列表复用 {@code UserVO}，
 * 知识点树复用 {@code KPTreeNodeVO}。</p>
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    @Data
    public static class DashboardVO {
        private Long userCount;
        private Long questionCount;
        private Long appointmentCount;
        private Long aiChatCount;
    }

    @Data
    public static class AiStatsVO {
        private Long totalCount;
        private Long todayCount;
        private Long distinctUsers;
    }

    @Data
    public static class UserCreateDTO {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
        private String realName;
        private String phone;
        @NotBlank(message = "角色不能为空")
        private String role;
        private Integer status;
    }

    @Data
    public static class UserUpdateDTO {
        private String realName;
        private String phone;
        private String role;
        private Integer status;
    }

    @Data
    public static class UserStatusDTO {
        @NotNull(message = "状态不能为空")
        private Integer status;
    }

    @Data
    public static class StatusDTO {
        @NotNull(message = "状态不能为空")
        private Integer status;
    }

    @Data
    public static class BindingVO {
        private Long bindId;
        private Long coachId;
        private String coachName;
        private String coachPhone;
        private Long studentId;
        private String studentName;
        private String studentPhone;
        private Integer status;
        private LocalDateTime bindTime;
    }

    @Data
    public static class BindingCreateDTO {
        @NotNull(message = "教练不能为空")
        private Long coachId;
        @NotNull(message = "学员不能为空")
        private Long studentId;
    }

    @Data
    public static class KnowledgePointDTO {
        @NotBlank(message = "科目不能为空")
        private String subjectCode;
        @NotNull(message = "父节点不能为空")
        private Long parentId;
        @NotBlank(message = "知识点名称不能为空")
        private String name;
        private Integer sortOrder;
        private String description;
    }

    @Data
    public static class QuestionOptionDTO {
        @NotBlank(message = "选项标识不能为空")
        private String optionKey;
        @NotBlank(message = "选项内容不能为空")
        private String optionContent;
        private Integer sortOrder;
    }

    @Data
    public static class QuestionDTO {
        @NotBlank(message = "科目不能为空")
        private String subjectCode;
        @NotNull(message = "知识点不能为空")
        private Long knowledgePointId;
        @NotBlank(message = "题型不能为空")
        private String questionType;
        @NotBlank(message = "题干不能为空")
        private String content;
        @NotBlank(message = "答案不能为空")
        private String answer;
        private String analysis;
        private Integer difficulty;
        private String mediaUrl;
        private Integer status;
        private List<QuestionOptionDTO> options;
    }

    @Data
    public static class AdminOptionVO {
        private String optionKey;
        private String optionContent;
        private Integer isCorrect;
        private Integer sortOrder;
    }

    @Data
    public static class QuestionVO {
        private Long id;
        private String subjectCode;
        private Long knowledgePointId;
        private String questionType;
        private String content;
        private String answer;
        private String analysis;
        private Integer difficulty;
        private String mediaUrl;
        private Integer status;
        private List<AdminOptionVO> options;
    }

    @Data
    public static class PaperQuestionDTO {
        @NotNull(message = "题目不能为空")
        private Long questionId;
        private Integer score;
        private Integer sortOrder;
    }

    @Data
    public static class PaperDTO {
        @NotBlank(message = "科目不能为空")
        private String subjectCode;
        @NotBlank(message = "试卷名称不能为空")
        private String title;
        private String paperType;
        private Integer totalScore;
        private Integer passScore;
        private Integer duration;
        private Integer status;
        private List<PaperQuestionDTO> questions;
    }

    @Data
    public static class PaperVO {
        private Long id;
        private String subjectCode;
        private String title;
        private String paperType;
        private Integer totalScore;
        private Integer passScore;
        private Integer duration;
        private Integer questionCount;
        private Integer status;
        private List<PaperQuestionDTO> questions;
    }

    @Data
    public static class ImportErrorVO {
        private Integer row;
        private String field;
        private String message;
    }

    @Data
    public static class ImportResultVO {
        private int total;
        private int success;
        private int failed;
        private List<ImportErrorVO> errors;
    }
}
