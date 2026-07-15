# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/devpulse-0.0.1-SNAPSHOT.jar app.jar

# Create temp directory and install JDK tools inside the container (since the sandbox needs 'javac')
RUN apk add --no-cache openjdk21
RUN mkdir temp && chmod 777 temp

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
