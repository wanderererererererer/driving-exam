package com.driving.exam.server.admin;

import com.driving.exam.common.exception.BusinessException;
import com.driving.exam.common.exception.ErrorCode;
import com.driving.exam.server.mapper.QuestionMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Excel 导入解析/校验/事务边界测试（不访问数据库）。
 *
 * <p>AdminService 的语义校验用 Mockito 模拟；题目写入也只 verify 调用，
 * 真实数据库写入和独立测试库集成测试不在本类中执行。</p>
 */
@DisplayName("Excel 题库导入（离线）")
class AdminQuestionExcelServiceTest {

    private static final String[] HEADERS = {
            "科目编码", "知识点ID", "知识点名称", "题型", "题干",
            "选项A", "选项B", "选项C", "选项D",
            "正确答案", "解析", "难度", "媒体URL", "状态"
    };

    private AdminService adminService;
    private QuestionMapper questionMapper;
    private AdminQuestionExcelService service;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminService.class);
        questionMapper = mock(QuestionMapper.class);
        service = new AdminQuestionExcelService(adminService, questionMapper);
    }

    @Test
    @DisplayName("模板表头与导入列顺序一致")
    void templateHeadersMatch() throws Exception {
        byte[] bytes = service.template();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Row header = wb.getSheetAt(0).getRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                assertThat(header.getCell(i).getStringCellValue()).isEqualTo(HEADERS[i]);
            }
        }
    }

    @Test
    @DisplayName("正常导入：语义校验通过后写库并返回成功统计")
    void validImportWrites() throws Exception {
        when(questionMapper.selectCount(any())).thenReturn(0L);
        MockMultipartFile file = file("questions.xlsx", workbook(row("SUBJECT_1", "1", "SINGLE",
                "红灯亮时应当怎样通行？", "A", "B", "A", "1")));

        AdminDtos.ImportResultVO result = service.importQuestions(file);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isZero();
        assertThat(result.getErrors()).isEmpty();
        verify(adminService).validateQuestion(any());
        verify(adminService).createQuestion(any());
    }

    @Test
    @DisplayName("语义错误：知识点不存在等由 AdminService 返回行级错误，不写任何题目")
    void semanticErrorBecomesRowError() throws Exception {
        doThrow(new BusinessException(ErrorCode.PARAM_ERROR, "知识点不存在或与科目不匹配"))
                .when(adminService).validateQuestion(any());
        MockMultipartFile file = file("questions.xlsx", workbook(row("SUBJECT_1", "1", "SINGLE",
                "红灯亮时应当怎样通行？", "A", "B", "A", "1")));

        AdminDtos.ImportResultVO result = service.importQuestions(file);

        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getRow()).isEqualTo(2);
        assertThat(result.getErrors().get(0).getMessage()).contains("知识点不存在");
        verify(adminService, never()).createQuestion(any());
    }

    @Test
    @DisplayName("文件内重复题干：返回行级错误，不写任何题目")
    void duplicateWithinFileRejected() throws Exception {
        when(questionMapper.selectCount(any())).thenReturn(0L);
        MockMultipartFile file = file("questions.xlsx", workbook(
                row("SUBJECT_1", "1", "SINGLE", "重复题干", "A", "B", "A", "1"),
                row("SUBJECT_1", "1", "SINGLE", "重复题干", "A", "B", "A", "1")));

        AdminDtos.ImportResultVO result = service.importQuestions(file);

        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("文件内题干重复");
        verify(adminService, never()).createQuestion(any());
    }

    @Test
    @DisplayName("题库已有相同题干：返回行级错误，不写任何题目")
    void duplicateInDatabaseRejected() throws Exception {
        when(questionMapper.selectCount(any())).thenReturn(1L);
        MockMultipartFile file = file("questions.xlsx", workbook(row("SUBJECT_1", "1", "SINGLE",
                "已在题库中的题干", "A", "B", "A", "1")));

        AdminDtos.ImportResultVO result = service.importQuestions(file);

        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getMessage()).contains("题库中已存在");
        verify(adminService, never()).createQuestion(any());
    }

    @Test
    @DisplayName("一行合法、一行缺字段：整批不写入，失败行返回明细")
    void anyErrorPreventsAllWrites() throws Exception {
        when(questionMapper.selectCount(any())).thenReturn(0L);
        MockMultipartFile file = file("questions.xlsx", workbook(
                row("SUBJECT_1", "1", "SINGLE", "合法题干", "A", "B", "A", "1"),
                row("SUBJECT_1", "1", "SINGLE", "", "A", "B", "A", "1")));

        AdminDtos.ImportResultVO result = service.importQuestions(file);

        assertThat(result.getSuccess()).isZero();
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getErrors().get(0).getRow()).isEqualTo(3);
        verify(adminService, never()).createQuestion(any());
    }

    @Test
    @DisplayName("非 Excel 扩展名：拒绝，不解析题目")
    void wrongExtensionRejected() {
        MockMultipartFile file = file("questions.txt", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.importQuestions(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.PARAM_ERROR.getCode()));
        verify(adminService, never()).validateQuestion(any());
        verify(adminService, never()).createQuestion(any());
    }

    /* ============================== helpers ============================== */

    private static MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private static String[] row(String subject, String kpId, String type, String content,
                                String a, String b, String answer, String status) {
        return new String[]{
                subject, kpId, "知识点", type, content,
                a, b, "", "", answer, "", "1", "", status
        };
    }

    private static byte[] workbook(String[]... rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("题库导入");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < HEADERS.length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
