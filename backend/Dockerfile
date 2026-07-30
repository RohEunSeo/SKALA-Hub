# 1단계: Gradle로 빌드 (레이어 캐싱을 위해 의존성 파일 먼저 복사)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# 2단계: 빌드된 jar만 담아 실행 (이미지 용량 최소화)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

# Render가 PORT 환경변수로 리스닝 포트를 지정함 (application.yml에서 ${PORT} 참조)
EXPOSE 8080
# 컨테이너 기본 타임존이 UTC라서 LocalDateTime.now()가 UTC 벽시계 시각을 반환하는 문제 방지 → KST로 고정
ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "-jar", "app.jar"]
