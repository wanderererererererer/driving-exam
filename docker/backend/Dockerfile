# syntax=docker/dockerfile:1
# 后端构建：Java 17 + Maven 多模块；运行镜像只包含可执行 jar 与外部配置模板。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY driving-exam /build/driving-exam
WORKDIR /build/driving-exam
RUN mvn -pl driving-exam-server -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /build/driving-exam/driving-exam-server/target/driving-exam-server-1.0.0.jar /app/app.jar
# 只使用环境变量占位模板；不复制本机 application.yml，避免把数据库/密钥带入镜像。
COPY driving-exam/driving-exam-server/src/main/resources/application.yml.example /app/application.yml
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.config.location=file:/app/application.yml"]
