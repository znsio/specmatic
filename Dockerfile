FROM openjdk:17-slim

WORKDIR /usr/src/app

# Install git and curl, and clean up
RUN apt-get update && \
    apt-get install -y --no-install-recommends git curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Copy the Specmatic JAR file into the container
COPY ./application/build/libs/specmatic.jar /usr/src/app/specmatic.jar

# Set the entrypoint to run the Specmatic JAR
ENTRYPOINT ["java", "-jar", "/usr/src/app/specmatic.jar"]
