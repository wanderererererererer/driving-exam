package com.driving.exam.server.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.pojo.entity.Question;
import com.driving.exam.server.mapper.QuestionMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理员端 Excel 题库导入。
 *
 * <p>模板列固定；全部行先校验，存在任何错误则不写入并返回错误清单，
 * 无错误时在同一事务中写入；不覆盖、不删除已有题目。</p>
 *
 * <p>重复题口径：同科目 + 完全相同的题干（不区分库内已有/文件内重复）拒绝导入；
 * 现有数据库没有唯一约束，因此该判断为应用层保护。</p>
 *
 * <p>语义校验复用 {@link AdminService#validateQuestion}，因此 Excel 和单条编辑对
 * 科目、知识点、题型、选项、答案、难度、状态的口径一致；所有错误都在写阶段之前收集。</p>
 */
@Service
public class AdminQuestionExcelService {

    private static final List<String> HEADERS = List.of(
            "科目编码", "知识点ID", "知识点名称", "题型", "题干",
            "选项A", "选项B", "选项C", "选项D",
            "正确答案", "解析", "难度", "媒体URL", "状态");

    private static final int MAX_ROWS = 2000;
    private static final long MAX_FILE_BYTES = 10 * 1024 * 1024L;

    private final AdminService adminService;
    private final QuestionMapper questionMapper;

    public AdminQuestionExcelService(AdminService adminService, QuestionMapper questionMapper) {
        this.adminService = adminService;
        this.questionMapper = questionMapper;
    }

    /** 生成只含表头的 xlsx 模板。 */
    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("题库导入");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                header.createCell(i).setCellValue(HEADERS.get(i));
                sheet.setColumnWidth(i, i == 4 ? 60 * 256 : 18 * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 模板失败", e);
        }
    }

    /**
     * 导入 Excel。任何一行不合法时不写入任何数据，返回错误行；全部合法才写入。
     */
    @Transactional(rollbackFor = Exception.class)
    public AdminDtos.ImportResultVO importQuestions(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择非空的 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null
                || (!filename.toLowerCase().endsWith(".xlsx") && !filename.toLowerCase().endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持 .xlsx 或 .xls 文件");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件不能超过 10MB");
        }

        List<AdminDtos.ImportErrorVO> errors = new ArrayList<>();
        List<AdminDtos.QuestionDTO> valid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int total = 0;

        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            Row header = sheet.getRow(0);
            if (header == null || !headersValid(header)) {
                errors.add(error(1, "表头", "缺少必需列或列顺序与模板不一致"));
                return result(0, errors);
            }
            int last = sheet.getLastRowNum();
            if (last > MAX_ROWS) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            DataFormatter formatter = new DataFormatter();
            for (int r = 1; r <= last; r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlank(row, formatter)) {
                    continue;
                }
                total++;
                try {
                    AdminDtos.QuestionDTO dto = parseRow(row, formatter);
                    // 复用题目单条编辑的语义校验：非法科目/知识点不存在/选项与答案不一致等
                    // 在写入任何数据之前变成行级错误，避免整批写入阶段异常且缺少失败明细。
                    adminService.validateQuestion(dto);
                    String key = dto.getSubjectCode() + "\u001F" + dto.getContent();
                    if (!seen.add(key)) {
                        throw new IllegalArgumentException("文件内题干重复");
                    }
                    Long exists = questionMapper.selectCount(new LambdaQueryWrapper<Question>()
                            .eq(Question::getSubjectCode, dto.getSubjectCode())
                            .eq(Question::getContent, dto.getContent()));
                    if (exists != null && exists > 0) {
                        throw new IllegalArgumentException("题库中已存在相同题干");
                    }
                    valid.add(dto);
                } catch (RuntimeException ex) {
                    errors.add(error(r + 1, "行", message(ex)));
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        if (!errors.isEmpty()) {
            return result(total, errors);
        }
        for (AdminDtos.QuestionDTO dto : valid) {
            adminService.createQuestion(dto);
        }
        AdminDtos.ImportResultVO result = new AdminDtos.ImportResultVO();
        result.setTotal(total);
        result.setSuccess(valid.size());
        result.setFailed(0);
        result.setErrors(new ArrayList<>());
        return result;
    }

    private boolean headersValid(Row header) {
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < HEADERS.size(); i++) {
            Cell cell = header.getCell(i);
            if (cell == null || !HEADERS.get(i).equals(formatter.formatCellValue(cell).trim())) {
                return false;
            }
        }
        return true;
    }

    private AdminDtos.QuestionDTO parseRow(Row row, DataFormatter formatter) {
        String subject = cell(row, 0, formatter).toUpperCase();
        String kpText = cell(row, 1, formatter);
        String type = cell(row, 3, formatter).toUpperCase();
        String content = cell(row, 4, formatter);
        String answer = cell(row, 9, formatter);
        if (subject.isEmpty() || kpText.isEmpty() || type.isEmpty() || content.isEmpty() || answer.isEmpty()) {
            throw new IllegalArgumentException("科目/知识点ID/题型/题干/正确答案不能为空");
        }
        Long kpId;
        try {
            kpId = Long.valueOf(kpText);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("知识点ID必须是数字");
        }
        int difficulty = 1;
        String diff = cell(row, 11, formatter);
        if (!diff.isEmpty()) {
            try {
                difficulty = Integer.parseInt(diff);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("难度必须是 1~5 的整数");
            }
        }
        int status = 1;
        String statusText = cell(row, 13, formatter);
        if (!statusText.isEmpty()) {
            try {
                status = Integer.parseInt(statusText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("状态必须是 0 或 1");
            }
        }

        AdminDtos.QuestionDTO dto = new AdminDtos.QuestionDTO();
        dto.setSubjectCode(subject);
        dto.setKnowledgePointId(kpId);
        dto.setQuestionType(type);
        dto.setContent(content);
        dto.setAnswer(answer);
        dto.setAnalysis(cell(row, 10, formatter));
        dto.setDifficulty(difficulty);
        dto.setMediaUrl(cell(row, 12, formatter));
        dto.setStatus(status);

        List<AdminDtos.QuestionOptionDTO> options = new ArrayList<>();
        if (!"JUDGE".equals(type)) {
            String[] keys = {"A", "B", "C", "D"};
            for (int i = 0; i < keys.length; i++) {
                String value = cell(row, 5 + i, formatter);
                if (!value.isEmpty()) {
                    AdminDtos.QuestionOptionDTO option = new AdminDtos.QuestionOptionDTO();
                    option.setOptionKey(keys[i]);
                    option.setOptionContent(value);
                    option.setSortOrder(i + 1);
                    options.add(option);
                }
            }
        }
        dto.setOptions(options);
        return dto;
    }

    private String cell(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private boolean isBlank(Row row, DataFormatter formatter) {
        for (int i = 0; i < HEADERS.size(); i++) {
            if (!cell(row, i, formatter).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private AdminDtos.ImportErrorVO error(int row, String field, String message) {
        AdminDtos.ImportErrorVO e = new AdminDtos.ImportErrorVO();
        e.setRow(row);
        e.setField(field);
        e.setMessage(message);
        return e;
    }

    private AdminDtos.ImportResultVO result(int total, List<AdminDtos.ImportErrorVO> errors) {
        AdminDtos.ImportResultVO result = new AdminDtos.ImportResultVO();
        result.setTotal(total);
        result.setSuccess(0);
        result.setFailed(errors.size());
        result.setErrors(errors);
        return result;
    }

    private String message(RuntimeException ex) {
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? "数据不合法" : msg;
    }
}
