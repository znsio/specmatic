FROM ubuntu:22.04

WORKDIR /usr/src/app

RUN apt-get update && \
    apt-get install -y openjdk-17-jre && \
    rm -rf /var/lib/apt/lists/*

RUN apt-get update && \
    apt-get install -y git && \
    rm -rf /var/lib/apt/lists/*

COPY ./application/build/libs/specmatic.jar /usr/src/app/specmatic.jar

ENTRYPOINT ["java", "-jar", "/usr/src/app/specmatic.jar"]

CMD []