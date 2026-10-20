package com.driving.exam.server.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.common.result.PageResult;
import com.driving.exam.pojo.entity.CoachStudent;
import com.driving.exam.pojo.entity.ExamPaper;
import com.driving.exam.pojo.entity.ExamPaperQuestion;
import com.driving.exam.pojo.entity.KnowledgePoint;
import com.driving.exam.pojo.entity.MockExam;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.pojo.entity.QuestionOption;
import com.driving.exam.pojo.entity.User;
import com.driving.exam.pojo.vo.KPTreeNodeVO;
import com.driving.exam.pojo.vo.UserVO;
import com.driving.exam.server.mapper.AiChatRecordMapper;
import com.driving.exam.server.mapper.AnswerRecordMapper;
import com.driving.exam.server.mapper.AppointmentMapper;
import com.driving.exam.server.mapper.CoachStudentMapper;
import com.driving.exam.server.mapper.ExamPaperMapper;
import com.driving.exam.server.mapper.ExamPaperQuestionMapper;
import com.driving.exam.server.mapper.KnowledgePointMapper;
import com.driving.exam.server.mapper.MockExamMapper;
import com.driving.exam.server.mapper.QuestionMapper;
import com.driving.exam.server.mapper.QuestionOptionMapper;
import com.driving.exam.server.mapper.UserMapper;
import com.driving.exam.server.enums.ExamStatus;
import com.driving.exam.server.service.KnowledgePointService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员端业务 Service（管理员专属查询与管理操作）。
 *
 * <p>尽量复用现有实体/Mapper/Service；不修改学生、教练既有接口语义。
 * 高风险操作采取保守策略：用户不物理删除、绑定软解绑、题目逻辑删除、
 * 已有模拟考试记录的试卷禁止改写题目组成。</p>
 */
@Service
public class AdminService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 20;
    private static final int MIN_DURATION_MINUTES = 1;
    private static final int MAX_DURATION_MINUTES = 240;
    private static final Set<String> ROLES = Set.of("STUDENT", "COACH", "ADMIN");
    private static final Set<String> QUESTION_TYPES = Set.of("SINGLE", "MULTIPLE", "JUDGE");
    private static final Set<String> SUBJECTS = Set.of("SUBJECT_1", "SUBJECT_2", "SUBJECT_3", "SUBJECT_4");

    private final UserMapper userMapper;
    private final CoachStudentMapper coachStudentMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final ExamPaperMapper examPaperMapper;
    private final ExamPaperQuestionMapper examPaperQuestionMapper;
    private final MockExamMapper mockExamMapper;
    private final AppointmentMapper appointmentMapper;
    private final AiChatRecordMapper aiChatRecordMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final KnowledgePointService knowledgePointService;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserMapper userMapper,
                        CoachStudentMapper coachStudentMapper,
                        KnowledgePointMapper knowledgePointMapper,
                        QuestionMapper questionMapper,
                        QuestionOptionMapper questionOptionMapper,
                        ExamPaperMapper examPaperMapper,
                        ExamPaperQuestionMapper examPaperQuestionMapper,
                        MockExamMapper mockExamMapper,
                        AppointmentMapper appointmentMapper,
                        AiChatRecordMapper aiChatRecordMapper,
                        AnswerRecordMapper answerRecordMapper,
                        KnowledgePointService knowledgePointService,
                        PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.coachStudentMapper = coachStudentMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.examPaperMapper = examPaperMapper;
        this.examPaperQuestionMapper = examPaperQuestionMapper;
        this.mockExamMapper = mockExamMapper;
        this.appointmentMapper = appointmentMapper;
        this.aiChatRecordMapper = aiChatRecordMapper;
        this.answerRecordMapper = answerRecordMapper;
        this.knowledgePointService = knowledgePointService;
        this.passwordEncoder = passwordEncoder;
    }

    private int current(int pageNum) {
        return Math.max(pageNum, 1);
    }

    private int size(int pageSize) {
        return pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /* ============================== 概览 / AI 统计 ============================== */

    public AdminDtos.DashboardVO dashboard() {
        AdminDtos.DashboardVO vo = new AdminDtos.DashboardVO();
        vo.setUserCount(userMapper.selectCount(null));
        vo.setQuestionCount(questionMapper.selectCount(
                new LambdaQueryWrapper<Question>().eq(Question::getStatus, 1)));
        vo.setAppointmentCount(appointmentMapper.selectCount(null));
        vo.setAiChatCount(aiChatRecordMapper.selectCount(null));
        return vo;
    }

    public AdminDtos.AiStatsVO aiStats() {
        AdminDtos.AiStatsVO vo = new AdminDtos.AiStatsVO();
        vo.setTotalCount(aiChatRecordMapper.selectCount(null));
        vo.setTodayCount(aiChatRecordMapper.selectCount(
                new LambdaQueryWrapper<com.driving.exam.pojo.entity.AiChatRecord>()
                        .ge(com.driving.exam.pojo.entity.AiChatRecord::getCreateTime,
                                LocalDate.now().atStartOfDay())));
        vo.setDistinctUsers(aiChatRecordMapper.countDistinctUsers());
        return vo;
    }

    /* ============================== 用户管理 ============================== */

    public PageResult<UserVO> listUsers(String role, String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(role)) {
            wrapper.eq(User::getRole, role.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            wrapper.and(w -> w.like(User::getUsername, k).or().like(User::getRealName, k));
        }
        wrapper.orderByDesc(User::getId);
        Page<User> page = userMapper.selectPage(new Page<>(current(pageNum), size(pageSize)), wrapper);
        List<UserVO> records = page.getRecords().stream().map(this::toUserVO).collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    public void createUser(AdminDtos.UserCreateDTO dto) {
        String username = dto.getUsername().trim();
        String role = dto.getRole().trim().toUpperCase();
        if (!ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色不合法");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态必须为 0 或 1");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setPhone(dto.getPhone());
        user.setRole(role);
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        userMapper.insert(user);
    }

    public UserVO getUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return toUserVO(user);
    }

    public void updateUser(Long adminId, Long id, AdminDtos.UserUpdateDTO dto) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String role = dto.getRole() == null ? null : dto.getRole().trim().toUpperCase();
        if (role != null && !ROLES.contains(role)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色不合法");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态必须为 0 或 1");
        }
        // 防止管理员把自己降级/禁用，避免失去最后一个可用管理入口
        if (adminId.equals(id)) {
            if (role != null && !"ADMIN".equals(role)) {
                throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED);
            }
            if (dto.getStatus() != null && dto.getStatus() != 1) {
                throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED);
            }
        }
        if (dto.getRealName() != null) {
            user.setRealName(dto.getRealName());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (role != null) {
            user.setRole(role);
        }
        if (dto.getStatus() != null) {
            user.setStatus(dto.getStatus());
        }
        userMapper.updateById(user);
    }

    public void setUserStatus(Long adminId, Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (adminId.equals(id) && status == 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED);
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        return vo;
    }

    /* ============================== 绑定管理 ============================== */

    public PageResult<AdminDtos.BindingVO> listBindings(Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<CoachStudent> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(CoachStudent::getStatus, status);
        }
        wrapper.orderByDesc(CoachStudent::getBindTime).orderByDesc(CoachStudent::getId);
        Page<CoachStudent> page = coachStudentMapper.selectPage(
                new Page<>(current(pageNum), size(pageSize)), wrapper);
        Map<Long, User> users = loadUsers(page.getRecords());
        List<AdminDtos.BindingVO> records = page.getRecords().stream()
                .map(b -> toBindingVO(b, users))
                .collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createBinding(AdminDtos.BindingCreateDTO dto) {
        User coach = userMapper.selectById(dto.getCoachId());
        User student = userMapper.selectById(dto.getStudentId());
        if (coach == null || student == null
                || !"COACH".equals(coach.getRole()) || !"STUDENT".equals(student.getRole())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (coach.getStatus() == null || coach.getStatus() != 1
                || student.getStatus() == null || student.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        CoachStudent existing = coachStudentMapper.selectOne(new LambdaQueryWrapper<CoachStudent>()
                .eq(CoachStudent::getCoachId, dto.getCoachId())
                .eq(CoachStudent::getStudentId, dto.getStudentId()));
        if (existing != null) {
            if (existing.getStatus() != null && existing.getStatus() == 1) {
                throw new BusinessException(ErrorCode.STUDENT_ALREADY_BOUND);
            }
            existing.setStatus(1);
            existing.setBindTime(LocalDateTime.now());
            coachStudentMapper.updateById(existing);
            return;
        }
        CoachStudent bind = new CoachStudent();
        bind.setCoachId(dto.getCoachId());
        bind.setStudentId(dto.getStudentId());
        bind.setStatus(1);
        coachStudentMapper.insert(bind);
    }

    public void unbind(Long bindId) {
        CoachStudent bind = coachStudentMapper.selectById(bindId);
        if (bind == null || bind.getStatus() == null || bind.getStatus() != 1) {
            throw new BusinessException(ErrorCode.BINDING_NOT_FOUND);
        }
        bind.setStatus(0);
        coachStudentMapper.updateById(bind);
    }

    private AdminDtos.BindingVO toBindingVO(CoachStudent bind, Map<Long, User> users) {
        AdminDtos.BindingVO vo = new AdminDtos.BindingVO();
        vo.setBindId(bind.getId());
        vo.setCoachId(bind.getCoachId());
        vo.setStudentId(bind.getStudentId());
        vo.setStatus(bind.getStatus());
        vo.setBindTime(bind.getBindTime());
        User coach = users.get(bind.getCoachId());
        User student = users.get(bind.getStudentId());
        if (coach != null) {
            vo.setCoachName(coach.getRealName());
            vo.setCoachPhone(coach.getPhone());
        }
        if (student != null) {
            vo.setStudentName(student.getRealName());
            vo.setStudentPhone(student.getPhone());
        }
        return vo;
    }

    private Map<Long, User> loadUsers(Collection<CoachStudent> binds) {
        Set<Long> ids = new HashSet<>();
        for (CoachStudent b : binds) {
            ids.add(b.getCoachId());
            ids.add(b.getStudentId());
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, User> map = new HashMap<>();
        for (User u : userMapper.selectBatchIds(ids)) {
            map.put(u.getId(), u);
        }
        return map;
    }

    /* ============================== 知识点管理 ============================== */

    public List<KPTreeNodeVO> knowledgeTree(String subjectCode) {
        if (!SUBJECTS.contains(subjectCode)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return knowledgePointService.getKnowledgeTree(subjectCode);
    }

    public void createKnowledgePoint(AdminDtos.KnowledgePointDTO dto) {
        String subject = dto.getSubjectCode().trim().toUpperCase();
        if (!SUBJECTS.contains(subject)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        KnowledgePoint parent = null;
        if (dto.getParentId() != null && dto.getParentId() != 0) {
            parent = knowledgePointMapper.selectById(dto.getParentId());
            if (parent == null || !subject.equals(parent.getSubjectCode())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
        }
        KnowledgePoint kp = new KnowledgePoint();
        kp.setSubjectCode(subject);
        kp.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        kp.setName(dto.getName().trim());
        kp.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        kp.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        kp.setDescription(dto.getDescription());
        knowledgePointMapper.insert(kp);
    }

    public void updateKnowledgePoint(Long id, AdminDtos.KnowledgePointDTO dto) {
        KnowledgePoint kp = knowledgePointMapper.selectById(id);
        if (kp == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        String subject = dto.getSubjectCode().trim().toUpperCase();
        if (!SUBJECTS.contains(subject) || !subject.equals(kp.getSubjectCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId.equals(id)) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED);
        }
        KnowledgePoint parent = null;
        if (parentId != 0) {
            parent = knowledgePointMapper.selectById(parentId);
            if (parent == null || !subject.equals(parent.getSubjectCode()) || isDescendant(parentId, id)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
        }
        kp.setParentId(parentId);
        kp.setName(dto.getName().trim());
        kp.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        kp.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        // 知识点树 VO 现在返回 description；仍保留“未传(null)时保持旧值，显式空串可清空”的兼容语义
        if (dto.getDescription() != null) {
            kp.setDescription(dto.getDescription());
        }
        knowledgePointMapper.updateById(kp);
    }

    private boolean isDescendant(Long candidateParentId, Long currentId) {
        Long cursor = candidateParentId;
        int guard = 0;
        while (cursor != null && cursor != 0 && guard++ < 100) {
            if (cursor.equals(currentId)) {
                return true;
            }
            KnowledgePoint p = knowledgePointMapper.selectById(cursor);
            cursor = p == null ? 0L : p.getParentId();
        }
        return false;
    }

    public void deleteKnowledgePoint(Long id) {
        KnowledgePoint kp = knowledgePointMapper.selectById(id);
        if (kp == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Long children = knowledgePointMapper.selectCount(
                new LambdaQueryWrapper<KnowledgePoint>().eq(KnowledgePoint::getParentId, id));
        Long questions = questionMapper.selectCount(
                new LambdaQueryWrapper<Question>().eq(Question::getKnowledgePointId, id));
        if ((children != null && children > 0) || (questions != null && questions > 0)) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED);
        }
        knowledgePointMapper.deleteById(id);
    }

    /* ============================== 题库管理 ============================== */

    public PageResult<AdminDtos.QuestionVO> listQuestions(String subjectCode, String type,
                                                          String keyword, int pageNum, int pageSize) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(subjectCode)) {
            wrapper.eq(Question::getSubjectCode, subjectCode.trim().toUpperCase());
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(Question::getQuestionType, type.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Question::getContent, keyword.trim());
        }
        wrapper.orderByDesc(Question::getId);
        Page<Question> page = questionMapper.selectPage(
                new Page<>(current(pageNum), size(pageSize)), wrapper);
        Map<Long, List<QuestionOption>> options = loadOptions(
                page.getRecords().stream().map(Question::getId).collect(Collectors.toList()));
        List<AdminDtos.QuestionVO> records = page.getRecords().stream()
                .map(q -> toQuestionVO(q, options.getOrDefault(q.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    public AdminDtos.QuestionVO getQuestion(Long id) {
        Question q = questionMapper.selectById(id);
        if (q == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        List<QuestionOption> options = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>().eq(QuestionOption::getQuestionId, id)
                        .orderByAsc(QuestionOption::getSortOrder));
        return toQuestionVO(q, options);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createQuestion(AdminDtos.QuestionDTO dto) {
        validateQuestion(dto);
        Question q = new Question();
        applyQuestion(q, dto);
        questionMapper.insert(q);
        insertOptions(q.getId(), dto);
        return q.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateQuestion(Long id, AdminDtos.QuestionDTO dto) {
        Question existing = questionMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        // 题目已进入任何一场考试后冻结内容/答案/状态，避免历史报告和正在进行的考试被改写
        assertQuestionNotUsedByExam(id);
        // 已有答题记录时，禁止修改标准答案，避免历史判分/报告口径被静默改写
        Long answered = answerRecordMapper.selectCount(
                new LambdaQueryWrapper<com.driving.exam.pojo.entity.AnswerRecord>()
                        .eq(com.driving.exam.pojo.entity.AnswerRecord::getQuestionId, id));
        if (answered != null && answered > 0) {
            String oldAnswer = "JUDGE".equalsIgnoreCase(existing.getQuestionType())
                    ? existing.getAnswer() : normalizeChoiceAnswer(existing.getAnswer());
            String newAnswer = "JUDGE".equalsIgnoreCase(existing.getQuestionType())
                    ? dto.getAnswer().trim() : normalizeChoiceAnswer(dto.getAnswer());
            if (!oldAnswer.equals(newAnswer)) {
                throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED);
            }
        }
        validateQuestion(dto);
        applyQuestion(existing, dto);
        questionMapper.updateById(existing);
        questionOptionMapper.delete(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, id));
        insertOptions(id, dto);
    }

    public void setQuestionStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        Question q = questionMapper.selectById(id);
        if (q == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        assertQuestionNotUsedByExam(id);
        q.setStatus(status);
        questionMapper.updateById(q);
    }

    public void deleteQuestion(Long id) {
        Question q = questionMapper.selectById(id);
        if (q == null) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_FOUND);
        }
        // 已进入考试题目的试卷结构不可因题库删除而缺题，保护历史成绩/报告
        assertQuestionNotUsedByExam(id);
        // 已有答题记录的题目不删除，避免历史记录失去题目关联
        Long answered = answerRecordMapper.selectCount(
                new LambdaQueryWrapper<com.driving.exam.pojo.entity.AnswerRecord>()
                        .eq(com.driving.exam.pojo.entity.AnswerRecord::getQuestionId, id));
        if (answered != null && answered > 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED);
        }
        // 逻辑删除：错题等引用仍保留
        questionMapper.deleteById(id);
    }

    private void assertQuestionNotUsedByExam(Long questionId) {
        Long used = examPaperQuestionMapper.countExamUsageByQuestionId(questionId);
        if (used != null && used > 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED);
        }
    }

    /** 将单选/多选答案折叠为半角大写并去掉分隔符，兼容历史种子中的全角答案。 */
    private String normalizeChoiceAnswer(String answer) {
        return Normalizer.normalize(answer == null ? "" : answer, Normalizer.Form.NFKC)
                .replace(",", "")
                .replace("，", "")
                .replaceAll("\\s+", "")
                .toUpperCase();
    }

    /**
     * 校验题目 DTO 的完整语义（管理员单条编辑和 Excel 导入共用）。
     *
     * <p>Excel 导入在写库之前逐行调用本方法，从而把“非法科目/知识点不存在/选项与答案不一致”
     * 等语义错误变成行级错误，而不是等写入阶段抛异常导致整批回滚且没有明细。</p>
     */
    public void validateQuestion(AdminDtos.QuestionDTO dto) {
        String subject = dto.getSubjectCode() == null ? "" : dto.getSubjectCode().trim().toUpperCase();
        String type = dto.getQuestionType() == null ? "" : dto.getQuestionType().trim().toUpperCase();
        if (!SUBJECTS.contains(subject)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "科目编码不合法");
        }
        if (!QUESTION_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "题型必须为 SINGLE/MULTIPLE/JUDGE");
        }
        if (dto.getKnowledgePointId() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "知识点不能为空");
        }
        KnowledgePoint kp = knowledgePointMapper.selectById(dto.getKnowledgePointId());
        if (kp == null || !subject.equals(kp.getSubjectCode())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "知识点不存在或与科目不匹配");
        }
        String answer = dto.getAnswer() == null ? "" : dto.getAnswer().trim();
        if (!StringUtils.hasText(dto.getContent())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "题干不能为空");
        }
        if (!StringUtils.hasText(answer)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "答案不能为空");
        }
        if (dto.getDifficulty() != null && (dto.getDifficulty() < 1 || dto.getDifficulty() > 5)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "难度必须为 1~5 的整数");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态必须为 0 或 1");
        }
        List<AdminDtos.QuestionOptionDTO> options = dto.getOptions() == null
                ? Collections.emptyList() : dto.getOptions();
        if ("JUDGE".equals(type)) {
            if (!"对".equals(answer) && !"错".equals(answer)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "判断题答案必须为 对/错");
            }
            return;
        }
        if (options.size() < 2) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "单选/多选题至少需要两个选项");
        }
        Set<String> keys = new HashSet<>();
        for (AdminDtos.QuestionOptionDTO o : options) {
            if (o == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "选项不能为空");
            }
            String key = o.getOptionKey() == null ? "" : o.getOptionKey().trim().toUpperCase();
            if (!key.matches("[A-D]") || !keys.add(key) || !StringUtils.hasText(o.getOptionContent())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "选项标识必须为 A-D 且内容不能为空");
            }
        }
        String normalized = normalizeChoiceAnswer(answer);
        if ("SINGLE".equals(type)) {
            if (!keys.contains(normalized) || normalized.length() != 1) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "单选题答案必须是一个有效选项");
            }
        } else {
            if (normalized.length() < 2) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "多选题答案至少包含两个有效选项");
            }
            Set<Character> chars = new HashSet<>();
            for (char c : normalized.toCharArray()) {
                if (!keys.contains(String.valueOf(c)) || !chars.add(c)) {
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "多选题答案包含无效或重复选项");
                }
            }
        }
    }

    private void applyQuestion(Question q, AdminDtos.QuestionDTO dto) {
        String type = dto.getQuestionType().trim().toUpperCase();
        q.setSubjectCode(dto.getSubjectCode().trim().toUpperCase());
        q.setKnowledgePointId(dto.getKnowledgePointId());
        q.setQuestionType(type);
        q.setContent(dto.getContent().trim());
        String answer = dto.getAnswer().trim();
        if ("SINGLE".equals(type) || "MULTIPLE".equals(type)) {
            answer = normalizeChoiceAnswer(answer);
        }
        q.setAnswer(answer);
        q.setAnalysis(dto.getAnalysis());
        q.setDifficulty(dto.getDifficulty() == null ? 1 : dto.getDifficulty());
        q.setMediaUrl(dto.getMediaUrl());
        q.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private void insertOptions(Long questionId, AdminDtos.QuestionDTO dto) {
        if ("JUDGE".equals(dto.getQuestionType().trim().toUpperCase())) {
            return;
        }
        String answer = normalizeChoiceAnswer(dto.getAnswer());
        int sort = 1;
        for (AdminDtos.QuestionOptionDTO o : dto.getOptions()) {
            String key = o.getOptionKey().trim().toUpperCase();
            QuestionOption option = new QuestionOption();
            option.setQuestionId(questionId);
            option.setOptionKey(key);
            option.setOptionContent(o.getOptionContent().trim());
            option.setIsCorrect(answer.contains(key) ? 1 : 0);
            option.setSortOrder(o.getSortOrder() == null ? sort : o.getSortOrder());
            questionOptionMapper.insert(option);
            sort++;
        }
    }

    private Map<Long, List<QuestionOption>> loadOptions(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<QuestionOption> list = questionOptionMapper.selectList(
                new LambdaQueryWrapper<QuestionOption>()
                        .in(QuestionOption::getQuestionId, questionIds)
                        .orderByAsc(QuestionOption::getSortOrder));
        Map<Long, List<QuestionOption>> map = new HashMap<>();
        for (QuestionOption o : list) {
            map.computeIfAbsent(o.getQuestionId(), k -> new ArrayList<>()).add(o);
        }
        return map;
    }

    private AdminDtos.QuestionVO toQuestionVO(Question q, List<QuestionOption> options) {
        AdminDtos.QuestionVO vo = new AdminDtos.QuestionVO();
        vo.setId(q.getId());
        vo.setSubjectCode(q.getSubjectCode());
        vo.setKnowledgePointId(q.getKnowledgePointId());
        vo.setQuestionType(q.getQuestionType());
        vo.setContent(q.getContent());
        vo.setAnswer(q.getAnswer());
        vo.setAnalysis(q.getAnalysis());
        vo.setDifficulty(q.getDifficulty());
        vo.setMediaUrl(q.getMediaUrl());
        vo.setStatus(q.getStatus());
        vo.setOptions(options.stream().map(o -> {
            AdminDtos.AdminOptionVO ovo = new AdminDtos.AdminOptionVO();
            ovo.setOptionKey(o.getOptionKey());
            ovo.setOptionContent(o.getOptionContent());
            ovo.setIsCorrect(o.getIsCorrect());
            ovo.setSortOrder(o.getSortOrder());
            return ovo;
        }).collect(Collectors.toList()));
        return vo;
    }

    /* ============================== 试卷管理 ============================== */

    public PageResult<AdminDtos.PaperVO> listPapers(String subjectCode, int pageNum, int pageSize) {
        LambdaQueryWrapper<ExamPaper> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(subjectCode)) {
            wrapper.eq(ExamPaper::getSubjectCode, subjectCode.trim().toUpperCase());
        }
        wrapper.orderByDesc(ExamPaper::getId);
        Page<ExamPaper> page = examPaperMapper.selectPage(
                new Page<>(current(pageNum), size(pageSize)), wrapper);
        List<AdminDtos.PaperVO> records = page.getRecords().stream()
                .map(p -> toPaperVO(p, null)).collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), page.getPages(), page.getCurrent(), page.getSize(), records);
    }

    public AdminDtos.PaperVO getPaper(Long id) {
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null) {
            throw new BusinessException(ErrorCode.PAPER_NOT_FOUND);
        }
        List<ExamPaperQuestion> links = examPaperQuestionMapper.selectList(
                new LambdaQueryWrapper<ExamPaperQuestion>()
                        .eq(ExamPaperQuestion::getPaperId, id)
                        .orderByAsc(ExamPaperQuestion::getSortOrder));
        return toPaperVO(paper, links);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createPaper(AdminDtos.PaperDTO dto) {
        List<ExamPaperQuestion> links = validatePaper(dto);
        ExamPaper paper = new ExamPaper();
        applyPaper(paper, dto, links);
        examPaperMapper.insert(paper);
        insertPaperQuestions(paper.getId(), links);
        return paper.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePaper(Long id, AdminDtos.PaperDTO dto) {
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null) {
            throw new BusinessException(ErrorCode.PAPER_NOT_FOUND);
        }
        Long used = mockExamMapper.selectCount(
                new LambdaQueryWrapper<MockExam>().eq(MockExam::getPaperId, id));
        if (used != null && used > 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED);
        }
        List<ExamPaperQuestion> links = validatePaper(dto);
        applyPaper(paper, dto, links);
        examPaperMapper.updateById(paper);
        examPaperQuestionMapper.delete(new LambdaQueryWrapper<ExamPaperQuestion>()
                .eq(ExamPaperQuestion::getPaperId, id));
        insertPaperQuestions(id, links);
    }

    public void setPaperStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null) {
            throw new BusinessException(ErrorCode.PAPER_NOT_FOUND);
        }
        if (status == 0) {
            Long ongoing = mockExamMapper.selectCount(new LambdaQueryWrapper<MockExam>()
                    .eq(MockExam::getPaperId, id)
                    .eq(MockExam::getStatus, ExamStatus.ONGOING.name()));
            if (ongoing != null && ongoing > 0) {
                throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED);
            }
        }
        paper.setStatus(status);
        examPaperMapper.updateById(paper);
    }

    private List<ExamPaperQuestion> validatePaper(AdminDtos.PaperDTO dto) {
        String subject = dto.getSubjectCode() == null ? "" : dto.getSubjectCode().trim().toUpperCase();
        if (!SUBJECTS.contains(subject)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        List<Long> ids = dto.getQuestions().stream()
                .filter(item -> item != null && item.getQuestionId() != null)
                .map(AdminDtos.PaperQuestionDTO::getQuestionId).collect(Collectors.toList());
        List<Question> questions = questionMapper.selectBatchIds(ids);
        Map<Long, Question> map = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        List<ExamPaperQuestion> links = new ArrayList<>();
        Set<Long> seenQuestionIds = new HashSet<>();
        int total = 0;
        int sort = 1;
        for (AdminDtos.PaperQuestionDTO item : dto.getQuestions()) {
            if (item == null || item.getQuestionId() == null
                    || !seenQuestionIds.add(item.getQuestionId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            Question q = map.get(item.getQuestionId());
            if (q == null || q.getStatus() == null || q.getStatus() != 1
                    || !subject.equals(q.getSubjectCode())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            int score = item.getScore() == null ? 0 : item.getScore();
            if (score <= 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            ExamPaperQuestion link = new ExamPaperQuestion();
            link.setQuestionId(item.getQuestionId());
            link.setScore(score);
            link.setSortOrder(item.getSortOrder() == null ? sort : item.getSortOrder());
            links.add(link);
            total += score;
            sort++;
        }
        if (dto.getDuration() != null
                && (dto.getDuration() < MIN_DURATION_MINUTES || dto.getDuration() > MAX_DURATION_MINUTES)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (dto.getTotalScore() != null && dto.getTotalScore() != total) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (dto.getPassScore() != null && (dto.getPassScore() < 0 || dto.getPassScore() > total)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        return links;
    }

    private void applyPaper(ExamPaper paper, AdminDtos.PaperDTO dto, List<ExamPaperQuestion> links) {
        int total = links.stream().mapToInt(ExamPaperQuestion::getScore).sum();
        paper.setSubjectCode(dto.getSubjectCode().trim().toUpperCase());
        paper.setTitle(dto.getTitle().trim());
        paper.setPaperType(StringUtils.hasText(dto.getPaperType()) ? dto.getPaperType() : "MOCK");
        paper.setTotalScore(total);
        paper.setPassScore(dto.getPassScore() == null ? total : dto.getPassScore());
        paper.setDuration(dto.getDuration() == null ? 20 : dto.getDuration());
        paper.setQuestionCount(links.size());
        paper.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private void insertPaperQuestions(Long paperId, List<ExamPaperQuestion> links) {
        for (ExamPaperQuestion link : links) {
            link.setId(null);
            link.setPaperId(paperId);
            examPaperQuestionMapper.insert(link);
        }
    }

    private AdminDtos.PaperVO toPaperVO(ExamPaper paper, List<ExamPaperQuestion> links) {
        AdminDtos.PaperVO vo = new AdminDtos.PaperVO();
        vo.setId(paper.getId());
        vo.setSubjectCode(paper.getSubjectCode());
        vo.setTitle(paper.getTitle());
        vo.setPaperType(paper.getPaperType());
        vo.setTotalScore(paper.getTotalScore());
        vo.setPassScore(paper.getPassScore());
        vo.setDuration(paper.getDuration());
        vo.setQuestionCount(paper.getQuestionCount());
        vo.setStatus(paper.getStatus());
        if (links != null) {
            vo.setQuestions(links.stream().map(l -> {
                AdminDtos.PaperQuestionDTO d = new AdminDtos.PaperQuestionDTO();
                d.setQuestionId(l.getQuestionId());
                d.setScore(l.getScore());
                d.setSortOrder(l.getSortOrder());
                return d;
            }).collect(Collectors.toList()));
        }
        return vo;
    }
}
