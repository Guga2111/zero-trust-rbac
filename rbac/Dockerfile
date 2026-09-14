# Maven build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the root code and compile .jar skipping tests
COPY src ./src
RUN mvn clean package -DskipTests

# Image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the .jar
COPY --from=build /app/target/*.jar app.jar

# Expose the internal container port
EXPOSE 8080

# Command por running Spring Boot
ENTRYPOINT ["java", "-jar", "app.jar"]