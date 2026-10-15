FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY .mvn/settings.xml .mvn/settings.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -s .mvn/settings.xml -Dmaven.wagon.http.retryHandler.count=3 dependency:resolve
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -s .mvn/settings.xml -Dmaven.wagon.http.retryHandler.count=3 -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S securebank && adduser -S securebank -G securebank
WORKDIR /app
COPY --from=build /build/target/securebank-api-*.jar app.jar
USER securebank
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
