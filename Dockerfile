FROM alpine:3.21.3

WORKDIR /usr/src/app

# Install OpenJDK 17, git, and curl in a single RUN command
RUN apk update && \
    apk add --no-cache openjdk17-jre git curl && \
    rm -rf /var/cache/apk/*

ARG VERSION

# Copy the Specmatic JAR file into the container
COPY ./application/build/libs/application-${VERSION}-all-unobfuscated.jar /usr/src/app/specmatic.jar

# Set the entrypoint to run the Specmatic JAR
ENTRYPOINT ["java", "-jar", "/usr/src/app/specmatic.jar"]
