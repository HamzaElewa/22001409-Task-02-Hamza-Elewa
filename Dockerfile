FROM eclipse-temurin:25.0.2_10-jdk

WORKDIR /app

COPY target/*.jar app.jar

ENV USER_NAME=Docker_Hamza_Elewa
ENV ID=Docker_22001409

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
