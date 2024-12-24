FROM alpine:3.20.1

WORKDIR /usr/src/app

# Install OpenJDK 17, git, and curl in a single RUN command
RUN apk update && \
    apk add --no-cache openjdk17-jre git curl && \
    rm -rf /var/cache/apk/*

# Copy the Specmatic JAR file into the container
COPY ./application/build/libs/specmatic.jar /usr/src/app/specmatic.jar
COPY ./TMF621-Trouble_Ticket-v5.0.0.oas.yaml /usr/src/app/TMF621-Trouble_Ticket-v5.0.0.oas.yaml
# Set the entrypoint to run the Specmatic JAR
ENTRYPOINT ["java", "-jar", "/usr/src/app/specmatic.jar"]
CMD ["examples", "interactive", "--contract-file=TMF621-Trouble_Ticket-v5.0.0.oas.yaml"]

