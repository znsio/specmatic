FROM ubuntu:22.04

WORKDIR /usr/src/app

# Install OpenJDK 17, git, and curl in a single RUN command
RUN apt-get update && \
    apt-get install -y --no-install-recommends openjdk-17-jre git curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# Copy the Specmatic JAR file into the container
COPY ./application/build/libs/specmatic.jar /usr/src/app/specmatic.jar

# Set the entrypoint to run the Specmatic JAR
ENTRYPOINT ["java", "-jar", "/usr/src/app/specmatic.jar"]

CMD []