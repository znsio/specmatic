Specmatic
========
[![Maven Central](https://img.shields.io/maven-central/v/in.specmatic/specmatic-core.svg)](https://mvnrepository.com/artifact/in.specmatic/specmatic-core) [![GitHub release](https://img.shields.io/github/v/release/znsio/specmatic.svg)](https://github.com/znsio/specmatic/releases) ![CI Build](https://github.com/znsio/specmatic/workflows/CI%20Build/badge.svg) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=znsio_specmatic&metric=alert_status)](https://sonarcloud.io/dashboard?id=znsio_specmatic) [![Twitter Follow](https://img.shields.io/twitter/follow/specmatic.svg?style=social&label=Follow)](https://twitter.com/specmatic)

### Context

In a complex, interdependent eco-system, where each service is evolving rapidly, we want to make the dependencies between them explicit in the form of executable contracts. By doing so, [Contract Testing](https://specmatic.in/contract_testing.html) allows teams to get instantaneous feedback while making changes to avoid accidental breakage.

With this ability, we can now deploy, at will, any service at any time without having to depend on expensive and fragile integration tests.

### What is Specmatic
Specmatic is a [contract driven development tool](https://specmatic.in/faqs.html#what-is-contract-first) that allows us to turn our contracts into executable specification.

According to us there are 2 key advantages, which were never possible before:
* **Death of Integration Testing** - As long as the service provider and consumer adhere to the contract, you can be 100% confident that each of them can develop and deploy their parts independently. **No need for integration testing**. 
    - On the provider side, run **Specmatic in test mode** 
    - On the consumer side run **Specmatic in stub mode** - Specmatic ensures that the expectations you are setting on your stubs are in-fact valid as per the contract.
* **Backward Compatibility Verification** - Typically for the provider to ensure they've not broken backward compatibility, they need to test their new version of the service with the current versions of all the consumers. This is a complex and time consuming process. With Specmatic, you just need to run the new version of the contract with the previous version to check for backward compatibility (no consumer tests required.)

### Our Goal is to support various types of Interactions
Systems interact with each other through several means. Specmatic hopes to address all these mechanisms and not just web interactions.
* API calls (**JSon REST**, **SOAP XML**, gRPC, Thrift, other binary protocols)
* Events via Messaging (**Kafka**, Redis, ActiveMQ, RabbitMQ, Kinesis, etc.)
* DB, Other Data Stores
* File system
* Libraries, SDK 
* OS Level Pipes

### Key Features

* [**Contract First**](https://specmatic.in/faqs.html#what-is-contract-first) - With an API-first approach, you can use Consumer or Provider driven contracts, whatever suits your needs the best.
  - Once a Contract is written, both Consumers and Providers can start development in parallel
* **Human readable contracts** - Specmatic leverages **Gherkin**'s strength as a specification mechanism to define your services (APIs.) No additional language specific tooling required.
  - Anyone 1.Developer (Consumer or Provider), 2.Architect, 3.Tech Leads, 4.Developer, 5.Tester can author the contracts
* **Backward Compatibility Verification** - Contract vs Contract testing (cross version compatibility checks) etc.
* **Service Virtualisation** - Run your contract in stub mode and isolate yourself from downstream dependencies (also be sure that your stubs are 100% compatible with the actual provider)
* **Contract as Test** - Test drive your services (APIs) using a contract
* **Programmatic** (Kotlin, Java and JVM languages) **and Command line support**
* **Tight integration with CI** - Trigger Provider and Consumer CI builds when any of the contracts change.
* Versioning
* Support for **SOAP/XML**, **Kafka**, **callbacks** and more
* Already have a lot of APIs? Don't worry, we can take your **Postman Collection** and easily generate contracts from it

[Get started now](https://specmatic.in/getting_started.html)

### Sample Projects

To see how to use Specmatic in a Java application, take a look at the following sample projects.

1. [Order API](https://github.com/znsio/specmatic-order-api): This is a simple API that takes manages products and orders. It uses contract tests to guarantee successful integration with it's UI, described next.
2. [Order UI](https://github.com/znsio/specmatic-order-ui): This is a tiny UI project that uses the Order API project. It uses intelligent service virtualisation in it's tests to stay in sync with the specmatic-order-api contract, guaranteeing successful integration with order-api.
3. [Contracts](https://github.com/znsio/specmatic-order-contracts): This repository contains the contracts that specmatic-order-api and specmatic-order-ui use to stay in sync with eachother.
