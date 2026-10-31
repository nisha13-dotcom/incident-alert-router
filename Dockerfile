# Use Java 21 runtime
FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy Spring Boot application JAR
COPY target/incident-alert-router-0.0.1-SNAPSHOT.jar app.jar

# Application port
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]