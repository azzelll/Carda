FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/auth-service-0.1.0-SNAPSHOT.jar /app/auth-service.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/auth-service.jar"]
