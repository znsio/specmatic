Specmatic
========
[![Maven Central](https://img.shields.io/maven-central/v/in.specmatic/specmatic-core.svg)](https://mvnrepository.com/artifact/in.specmatic/specmatic-core) [![GitHub release](https://img.shields.io/github/v/release/znsio/specmatic.svg)](https://github.com/znsio/specmatic/releases) ![CI Build](https://github.com/znsio/specmatic/workflows/CI%20Build/badge.svg) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=znsio_specmatic&branch=main&metric=alert_status)](https://sonarcloud.io/dashboard?id=znsio_specmatic&branch=main) [![Twitter Follow](https://img.shields.io/twitter/follow/specmatic.svg?style=social&label=Follow)](https://twitter.com/specmatic) [![Docker Pulls](https://img.shields.io/docker/pulls/znsio/specmatic.svg)](https://hub.docker.com/r/znsio/specmatic)

### Context

In a complex, interdependent eco-system, where each service is evolving rapidly, we want to make the dependencies between them explicit in the form of executable contracts. [Contract Driven Development](https://specmatic.in/contract_driven_development.html) leverages API specifications like OpenAPI, AsyncAPI, etc. as executable contracts allowing teams to get instantaneous feedback while making changes to avoid accidental breakage.

With this ability, we can now independently deploy, at will, any service at any time without having to depend on expensive and fragile integration tests.

### What is Specmatic
Specmatic embodies [contract driven development](https://specmatic.in/contract_driven_development.html) (CDD) by leveraging API specifications as executable contracts.
* [Contract as Tests](https://specmatic.in/#contract-as-test) - Generating free tests on the fly based only on you API specifications making sure that your implementation is always adhering to your API specification
* [Contract as Stub](https://specmatic.in/#contract-as-stub) - Stubbing a service based on its API specification, that too with the guarantee that your response mappings will always be in sync with your API specs
* [Backward Compatibility Testing](https://specmatic.in/#contract-vs-contract) (#NOCODE) - Identify backward breaking changes well ahead of time even before write any code based only on your API specifications
* [Central Contract Repository](https://specmatic.in/#contract-as-code) - Single source of truth for your API Specifications
* [API First Design](https://youtu.be/uaaevRw0TN4?list=PL9Z-JgiTsOYRERcsy9o3y6nsi5yK3IB_w&t=1306) - CDD encourages putting API design first rather than design being a byproduct of service implementation

### Our Goal is to support various types of Interactions
Systems interact with each other through several means. Specmatic hopes to address all these mechanisms and not just web interactions.
* API calls (**JSon REST**, **SOAP XML**, gRPC, Thrift, other binary protocols)
* Events via Messaging (**Kafka**, Redis, ActiveMQ, RabbitMQ, Kinesis, etc.)
* DB, Other Data Stores
* File system
* Libraries, SDK 
* OS Level Pipes

[Specmatic's Product Roadmap](https://specmatic.in/roadmap/)

Learn more at [specmatic.in](https://specmatic.in/#features)

[Get started now](https://specmatic.in/getting_started.html)

[![Specmatic - Contract Driven Development - YouTube playlist](https://img.youtube.com/vi/KWCnTi-ifME/0.jpg)](https://www.youtube.com/watch?v=KWCnTi-ifME&list=PL9Z-JgiTsOYRERcsy9o3y6nsi5yK3IB_w&index=2)

[Youtube playlist](https://www.youtube.com/watch?v=3HPgpvd8MGg&list=PL9Z-JgiTsOYRERcsy9o3y6nsi5yK3IB_w)
