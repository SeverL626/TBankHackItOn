FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

COPY . .

RUN chmod +x ./gradlew
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/build/install/meventus .

EXPOSE 8080

ENTRYPOINT ["bin/meventus"]