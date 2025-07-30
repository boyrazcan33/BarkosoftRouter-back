FROM maven:3.9-openjdk-21 AS build
WORKDIR /app
COPY router/pom.xml .
COPY router/src ./src
RUN mvn clean package -DskipTests

FROM openjdk:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-Dspring.profiles.active=prod", "-jar", "app.jar"]