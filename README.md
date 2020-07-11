Qontract
========
[![Maven Central](https://img.shields.io/maven-central/v/run.qontract/qontract-core.svg)](https://mvnrepository.com/artifact/run.qontract/qontract-core) [![GitHub release](https://img.shields.io/github/release/qontract/qontract.svg)](https://github.com/qontract/qontract/releases) ![CI Build](https://github.com/qontract/qontract/workflows/CI%20Build/badge.svg) [![Twitter Follow](https://img.shields.io/twitter/follow/qontract.svg?style=social&label=Follow)](https://twitter.com/qontract)

### Context

In a complex, interdependent eco-system, where each system’s components are evolving rapidly,
we want to make the dependencies between these components explicit in the form of contracts.
By doing so, [Contract Testing](https://qontract.run/contract_testing.html) helps us get instantaneous feedback when we make changes to avoid accidental breakage.

With this ability, we can now deploy, at will, any system’s component at any time without having to completely depend on expensive integration tests.

### What is Qontract
Qontract is a [contract driven development tool](https://qontract.run/faqs.html#what-is-contract-first) that allows us to turn our contracts into executable specification.

According to us there are 2 key advantages, which were never possible before:
* **Death of Integration Testing** - As long as the service provider and consumer adhere to the contract, you can be 100% confident that each of them can develop and deploy their parts independently. **No need for integration testing**. 
    - On the provider side, run **Qontract in test mode** 
    - On the consumer side run **Qontract in stub mode** - Qontract ensures that the expectations you are setting on your stubs are in-fact valid as per the contract.
* **Backward Compatibility Verification** - Typically for the provider to ensure they've not broken backward compatibility, they need to test their new version of the service with an existing version of the consumer. You might have multiple consumers. This is a complex and time consuming process. With Qontract, you just need to run the contract with new version of the contract with the previous version of the contract to check for backward compatibility.

### Our Goal is to support various types of Interactions
Systems interact with each other through several means. Qontract hopes to address all these mechanisms and not just web interactions.
* API calls (JSon Rest, SOAP XML, gRPC, Thrift, other binary protocols) - Current focus
* Events via Messaging (Kafka, )
* DB
* File system
* Libraries, SDK 
* OS Level Pipes

---
### Key Features

* **Human readable contracts** - Qontract leverages **Gherkin**'s strength as a specification mechanism to define your components (APIs.)
* **Backward Compatibility Verification** - Contract vs Contract testing (cross version compatibility checks) etc.
* **Service Virtualisation** - Run your contract in stub mode and isolate yourself from downstream dependencies (also be sure that your stubs are 100% compatible with the actual provider)
* **Contract as Test** - Test drive your components (APIs) using a contract
* **Programmatic** (Kotlin, Java and JVM languages) **and Command line support**
* Versioning
* Support for **callbacks**
* Converting from **Postman to Qontract**
* Support for **Kafka**

[Get started now](https://qontract.run/documentation/getting_started.html)
---
