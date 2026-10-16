-- MySQL dump 10.13  Distrib 8.0.31, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: driving_exam
-- ------------------------------------------------------
-- Server version	8.0.31

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ai_chat_record`
--

DROP TABLE IF EXISTS `ai_chat_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_chat_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '会话ID',
  `subject_code` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '科目：SUBJECT_1/SUBJECT_2/SUBJECT_3/SUBJECT_4/COMMON',
  `question` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户问题',
  `answer` mediumtext COLLATE utf8mb4_unicode_ci COMMENT 'AI回答',
  `references_json` json DEFAULT NULL COMMENT '引用来源JSON（第一版可为空）',
  `model` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型ID',
  `prompt_tokens` int DEFAULT NULL COMMENT '输入Token数',
  `completion_tokens` int DEFAULT NULL COMMENT '输出Token数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_subject_code` (`subject_code`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI问答记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `answer_record`
--

DROP TABLE IF EXISTS `answer_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `answer_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `subject_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '科目',
  `question_id` bigint NOT NULL COMMENT '题目ID',
  `user_answer` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户答案',
  `is_correct` tinyint NOT NULL COMMENT '是否正确：1是 0否',
  `source` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PRACTICE' COMMENT '来源：PRACTICE/EXAM',
  `exam_id` bigint DEFAULT NULL COMMENT '模拟考试ID',
  `duration` int DEFAULT NULL COMMENT '答题耗时秒',
  `answer_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '答题时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_subject` (`user_id`,`subject_code`),
  KEY `idx_question_id` (`question_id`),
  KEY `idx_answer_time` (`answer_time`),
  KEY `idx_exam_id` (`exam_id`)
) ENGINE=InnoDB AUTO_INCREMENT=99 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='答题记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `appointment`
--

DROP TABLE IF EXISTS `appointment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `appointment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` bigint NOT NULL COMMENT '学员ID',
  `coach_id` bigint NOT NULL COMMENT '教练ID',
  `subject_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '科目：SUBJECT_2/SUBJECT_3',
  `appointment_date` date NOT NULL COMMENT '预约日期',
  `time_slot` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '时间段',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/CONFIRMED/CANCELLED/COMPLETED',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学员备注',
  `coach_remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '教练备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_coach_id` (`coach_id`),
  KEY `idx_subject_date` (`subject_code`,`appointment_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `chat_message`
--

DROP TABLE IF EXISTS `chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `sender_id` bigint NOT NULL COMMENT '发送者ID',
  `sender_role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发送者角色：STUDENT/COACH',
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读：1是 0否',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `chat_session`
--

DROP TABLE IF EXISTS `chat_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` bigint NOT NULL COMMENT '学员ID',
  `coach_id` bigint NOT NULL COMMENT '教练ID',
  `last_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后一条消息内容',
  `last_message_time` datetime DEFAULT NULL COMMENT '最后消息时间',
  `student_unread` int NOT NULL DEFAULT '0' COMMENT '学员未读数',
  `coach_unread` int NOT NULL DEFAULT '0' COMMENT '教练未读数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_coach` (`student_id`,`coach_id`),
  KEY `idx_coach_id` (`coach_id`),
  KEY `idx_last_message_time` (`last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员教练会话表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `coach_student`
--

DROP TABLE IF EXISTS `coach_student`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `coach_student` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `coach_id` bigint NOT NULL COMMENT '教练ID',
  `student_id` bigint NOT NULL COMMENT '学员ID',
  `bind_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1有效 0解除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coach_student` (`coach_id`,`student_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教练学员关系表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exam_paper`
--

DROP TABLE IF EXISTS `exam_paper`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_paper` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `subject_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '科目：SUBJECT_1/SUBJECT_4',
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '试卷标题',
  `paper_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MOCK' COMMENT '类型：MOCK/CHAPTER',
  `total_score` int NOT NULL DEFAULT '100' COMMENT '总分',
  `pass_score` int NOT NULL DEFAULT '90' COMMENT '及格分',
  `duration` int NOT NULL DEFAULT '45' COMMENT '考试时长分钟',
  `question_count` int NOT NULL DEFAULT '0' COMMENT '题目数量',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_subject_type` (`subject_code`,`paper_type`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `exam_paper_question`
--

DROP TABLE IF EXISTS `exam_paper_question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_paper_question` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `paper_id` bigint NOT NULL COMMENT '试卷ID',
  `question_id` bigint NOT NULL COMMENT '题目ID',
  `score` int NOT NULL DEFAULT '1' COMMENT '本题分值',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_paper_question` (`paper_id`,`question_id`),
  KEY `idx_paper_id` (`paper_id`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=126 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='试卷题目关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `knowledge_point`
--

DROP TABLE IF EXISTS `knowledge_point`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_point` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `subject_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '科目：SUBJECT_1/SUBJECT_4',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父知识点ID，0为根',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识点名称',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '层级',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_subject_parent` (`subject_code`,`parent_id`),
  KEY `idx_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表（树形）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mock_exam`
--

DROP TABLE IF EXISTS `mock_exam`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mock_exam` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `subject_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '科目',
  `paper_id` bigint NOT NULL COMMENT '试卷ID',
  `start_time` datetime NOT NULL COMMENT '开始时间',
  `submit_time` datetime DEFAULT NULL COMMENT '交卷时间',
  `duration` int DEFAULT NULL COMMENT '实际用时秒',
  `score` int DEFAULT NULL COMMENT '得分',
  `total_score` int NOT NULL COMMENT '总分',
  `pass_score` int NOT NULL COMMENT '及格分',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ONGOING' COMMENT '状态：ONGOING/SUBMITTED/TIMEOUT',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_subject` (`user_id`,`subject_code`),
  KEY `idx_paper_id` (`paper_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模拟考试表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `mock_exam_answer`
--

DROP TABLE IF EXISTS `mock_exam_answer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mock_exam_answer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `exam_id` bigint NOT NULL COMMENT '模拟考试ID',
  `question_id` bigint NOT NULL COMMENT '题目ID',
  `user_answer` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户答案',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_question` (`exam_id`,`question_id`),
  KEY `idx_exam_id` (`exam_id`)
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模拟考试作答草稿表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question`
--

DROP TABLE IF EXISTS `question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `subject_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '科目：SUBJECT_1/SUBJECT_4',
  `knowledge_point_id` bigint NOT NULL COMMENT '知识点ID',
  `question_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题型：SINGLE/MULTIPLE/JUDGE',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '题干',
  `answer` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '正确答案，如A、AB、对/错',
  `analysis` text COLLATE utf8mb4_unicode_ci COMMENT '解析',
  `difficulty` tinyint NOT NULL DEFAULT '1' COMMENT '难度1-5',
  `media_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图片/视频URL',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_subject_kp` (`subject_code`,`knowledge_point_id`),
  KEY `idx_type` (`question_type`),
  KEY `idx_difficulty` (`difficulty`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question_option`
--

DROP TABLE IF EXISTS `question_option`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_option` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `question_id` bigint NOT NULL COMMENT '题目ID',
  `option_key` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项标识 A/B/C/D',
  `option_content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项内容',
  `is_correct` tinyint NOT NULL DEFAULT '0' COMMENT '是否正确答案：1是 0否',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_option` (`question_id`,`option_key`),
  KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=99 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目选项表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `student_subject_status`
--

DROP TABLE IF EXISTS `student_subject_status`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_subject_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `student_id` bigint NOT NULL COMMENT '学员ID',
  `subject_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '科目：SUBJECT_1/SUBJECT_2/SUBJECT_3/SUBJECT_4',
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态：NOT_STARTED/LEARNING/READY_FOR_EXAM/EXAM_PASSED/EXAM_FAILED',
  `mock_pass_count` int NOT NULL DEFAULT '0' COMMENT '模拟考连续及格次数',
  `last_mock_score` int DEFAULT NULL COMMENT '最近一次模拟考成绩',
  `study_progress` decimal(5,2) DEFAULT '0.00' COMMENT '题库完成率百分比',
  `exam_ready_suggest` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '系统建议：READY/NEED_MORE/NEED_HELP',
  `ai_suggest` text COLLATE utf8mb4_unicode_ci COMMENT 'AI生成的建议文本',
  `ai_suggest_time` datetime DEFAULT NULL COMMENT 'AI建议生成时间',
  `coach_confirm_status` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '教练确认：CONFIRMED_READY/NOT_READY/NEED_HELP',
  `coach_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '教练备注',
  `exam_result` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '正式考试结果：PASS/FAIL',
  `exam_date` date DEFAULT NULL COMMENT '正式考试日期',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_subject` (`student_id`,`subject_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学员科目进度表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录名',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（BCrypt）',
  `real_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '真实姓名',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT/COACH/ADMIN',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1正常 0禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`),
  KEY `idx_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `wrong_book`
--

DROP TABLE IF EXISTS `wrong_book`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wrong_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `subject_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '科目',
  `question_id` bigint NOT NULL COMMENT '题目ID',
  `wrong_count` int NOT NULL DEFAULT '1' COMMENT '错误次数',
  `last_wrong_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近错误时间',
  `mastered` tinyint NOT NULL DEFAULT '0' COMMENT '是否已掌握：1是 0否',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_question` (`user_id`,`question_id`),
  KEY `idx_user_subject_mastered` (`user_id`,`subject_code`,`mastered`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='错题本表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-08 17:26:17
