package com.driving.exam.server.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 全局配置：统一 {@link LocalDateTime} 的序列化 / 反序列化格式。
 *
 * <p><b>为什么全局配而不是逐字段加 {@code @JsonFormat}：</b>
 * 项目里时间字段会越来越多（wrong_book.last_wrong_time、mock_exam.start_time、
 * appointment.create_time 等），逐个加注解容易遗漏，全局一次配置覆盖所有 LocalDateTime 字段。</p>
 *
 * <p>注意：{@code LocalDateTime} 不携带时区信息，格式化时直接按字面值输出，不做时区换算；
 * 这里的 {@code timeZone} 设置作用于 {@code java.util.Date} / {@code Instant} 等带时区的类型。</p>
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String TIME_ZONE = "Asia/Shanghai";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
            // 作用于 Date / Instant 等带时区的类型（LocalDateTime 本身不受影响）
            builder.timeZone(TimeZone.getTimeZone(TIME_ZONE));
        };
    }
}
