FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# 1. 빌드 스크립트와 설정 파일만 먼저 복사 → 의존성 캐싱
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN ./gradlew dependencies -x test

# 2. 소스 코드 복사 후 빌드 (소스 변경 시에도 의존성은 캐시 재사용)
COPY src src
RUN ./gradlew bootJar -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]