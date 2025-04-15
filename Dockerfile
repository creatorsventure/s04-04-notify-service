# Use Maven base image to build the project
FROM maven:3.9.9-eclipse-temurin-23 as builder

# Set the working directory
WORKDIR /app

# Copy all files
COPY . .

# Download dependencies (this helps take advantage of Docker caching)
RUN mvn dependency:go-offline -B

# Package the application
RUN mvn package -DskipTests

# ---- Create a smaller image for running ----
FROM eclipse-temurin:23-jre

# ===== OCI-COMPLIANT LABELS =====
LABEL org.opencontainers.image.title="CV Spring Notify Service" \
      org.opencontainers.image.description="Spring Notify Service server for microservices to send email/sms" \
      org.opencontainers.image.source="https://github.com/creatorsventure/s04-04-notify-service" \
      #org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.authors="Ramakrishna R <ramakrishna@creatorsventure.com>" \
      org.opencontainers.image.documentation="https://github.com/creatorsventure/s04-04-notify-service"
      #org.opencontainers.image.licenses="MIT"

# Set the working directory
WORKDIR /app

# Copy the built jar file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
