FROM eclipse-temurin:11
COPY ./application/build/libs/specmatic.jar .
ENTRYPOINT ["java", "-jar", "specmatic.jar"]
