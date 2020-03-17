**Documentation moved to project [website](qontract.run)**

About the tool
==============

Qontract is a "Contract First" contract testing tool.

* **Human readable contracts** - It leverages **Gherkin**'s strength as a specification mechanism to define APIs.
* **Backward Compatibility verification** - Contract vs Contract testing (cross version compatibility checks) etc.
* **Contract as Stub / Mock** - Run stubs / mocks that are based on your downstream contracts to isolate your development
* **Contract as Test** - Test drive your APIs with contract
* **Programmatic** (Kotlin, Java and JVM languages) **and Command line support**

Jump to examples by clicking [here] TODO

Motivation
----------
With an increasing number of applications moving to MicroServices / Distributed Architecture, it is becoming increasingly difficult to keep all of them on agreeable terms.

**Integration Testing is not an option** given the expense (cost and time) and effort involved in building and maintaining a comprehensive suite.

Below are features that we think are necessary to getting contract testing right.

* **Architecture Independence** - Should not be limited to REST or other simple API setup. It should support asynchronous integrations and integrations through other tools such as message queues, databases, memory, file etc.
* **Approach Independence** - Should not be limited to Client Before Server approach. It should suit equally well to a Client Before Server, Server Before Client or a Contract First approach.
* **Language / Tooling Independence** - Since MicroServices promote polyglot programming, it is important that the tool remains agnostic to any specific language or platform.

Philosophy
----------
We acknowledge that such a tool is not just solving the contract testing problem, it is also **Engineering the interaction between individuals and teams in an organisation**. While we have designed the tool such that it can be adopted with minimal changes to the way a team works, it is **opinionated towards promoting collaboration** while architecting APIs.

Quick Start
===========

Command Line
------------

This mode helps leverage the tool in language / platform agnostic manner. Say suppose you have a Python provider and a Ruby consumer you can still leverage the tool (even though the tool itself is written in Kotlin and needs only JVM to run).

### Setup

Clone this project and run "mvn clean install". Optionally, locate the "qontract-0.0.1-all.jar" file and alias it for convenience.

    'alias qontract='java -jar <basedir>/qontract/application/build/libs/qontract-0.0.1-all.jar'

TODO: Upload to Maven Repository

### Author a contract

Copy paste below text into a file with name "service.qontract". This as you can see uses the Gherkin syntax to describe a basic GET request. 

    Feature: Contract for the petstore service

    Scenario: Should be able to get a pet by petId url parameter
      When GET /pets/(petid:number)
      Then status 200
      And response-body {petid: "(number)"}

There are some extra keywords that make it easier to define APIs.
* GET and related URL
* status
* response-body
* (number) - placeholder for number datatype

These keywords are documented in the contract syntax reference. TODO

### Stub server

All you need at this point is the above qontract file and the jar file which you built (and optionally aliased). You can spin up a stuck server with these two ingredients.

    qontract stub --path=".. ../petstore/qontract/service.qontract" \
                    --host="localhost" \
                    --port="8000" \
               start

The command has defaults and necessary help to guide you through.

Once the stub server is running you can verify the API by accessing it through Postmane, Chrome, Curl etc. to see the response.

    curl http://localhost:8000/pets/123

The response will contain some auto-generate value that adhere to the datatypes defined in the contract. At this point you are ready to start consumer development with this stub server.

### Contract as a Test

Now lets start building the provider. Say suppose you have an empty provider API running at port 8000, we just need to point the contract as test command to it.

    qontract test --path=".. ../petstore/contract/service.qontract" \
                            --host="localhost" \
                            --port="8000" \
               run

This command will give a xunit like feedback about each scenario and what parts of the contract your provider is not satisfying.

    Tests run: 1, Failures: 0, Errors: 0
    Failed Interactions
    ===================
    Scenario: GET /pets/(petid:number) Error:
    	Response did not match
    	JSON object did not match Expected: {petid=12345} Actual: {petid=210}

As you can see it has rightly pointed out that we have not implemented the "/pets" endpoint. Once you build it the errors will disappear.
This is quite similar to how we start with a feaure file in BDD and then let it drive development.

#### Suggestions

Let us assume you are running the above test command against a real service. And let us also assume that only pet id 123 exists as per your data setup.
In this scenario when test generates a random petid you will most likely get a 404. So under these circumstances you can mention the petId as an example.

    Feature: Contract for the petstore service

    Scenario: Should be able to get a pet by petId url parameter
      When GET /pets/(petid:number)
      Then status 200
      And response-body {petid: "(number)"}
      Examples:
      | petid |
      | 123  |

What if the Examples vary by the environment. Example: Your staging environment may have petid 345.
Examples that vary by environment should be externalised to environment specific suggestion files.

In order to author a suggestion file, just copy over the same contract content to another file and remove everything except Feature, Scenario and Examples keywords.
Let us call it suggestions-staging.qontract just to indicate that this file is related to staging. You can call it anything you like.
Now we can remove the examples section in the contract. Below is an example.

service.qontract

    Feature: Contract for the petstore service
   
       Scenario: Should be able to get a pet by petId url parameter
         When GET /pets/(petid:number)
         Then status 200
         And response-body {petid: "(number)"}

suggestions-staging.qontract

    Feature: Contract for the petstore service

      Scenario: Should be able to get a pet by petId url parameter
        Examples:
        | petid |
        | 345   |
        
Note: It is mandatory to name the scenario and also scenario names must be unique when using suggestions. This is because test command needs to lookup the scenario by name in the contract and map the Examples in suggestions to it.    

    qontract test --path=".. ../petstore/qontract/service.qontract" \
                    --suggestions=".. ../petstore/qontract/suggestions-staging.qontract" \
                                --host="staging-server" \
                                --port="8000" \
                   run
                   
### Mock server

The stub server we ran above will give you a response that includes autogenerated values for placehoders such as numbers, strings etc. Since response is not predictable we will not be able to write a unit test test for the consumer that will inturn invoke the unpredictable stub.

Mock server command solves this problem by letting you set expectations of what requests our consumer code will make and the responses that should be returned.

TODO: suggestions file to setup the mock interactions.

Programmatic Implementation
---------------------------

If you are building your applicatio in Java, Kotlin (TODO other JVM languages that may just work), you are in luck, because you can spawn the stub server or run the contract as test programmatically.

### Consumer - Leveraging Mock Server

Let us try building a Pet Store Consumer through Test First appraoch.

```java
public class PetStoreConsumerTest {
    private static ContractMock petStoreMock;

    @BeforeAll
    public static void setup() throws Throwable {
        //Start a mock server based on the contract
        String gherkin = Utilities.readFile("<baseDir>/petstore/qontract/service.qontract");
        petStoreMock = ContractMock.fromGherkin(gherkin, 9003);
        petStoreMock.start();
    }

    @Test
    public void shouldGetPetByPetId() throws IOException {
        //Arrange - Setup the mock to respond to the request we expect PetStoreConsumer to make
        HttpRequest httpRequest = new HttpRequest().setMethod("GET").updatePath("/pets/123");
        HttpResponse httpResponse = HttpResponse.Companion.jsonResponse("{petid:123}");
        Map<String, Object> serverState = new HashMap<>();
        // This line makes sure the request and response we are setting up are in line with the contract
        petStoreMock.tryToMockExpectation(new MockScenario(httpRequest, httpResponse, serverState));

        //Act
        PetStoreConsumer petStoreConsumer = new PetStoreConsumer("http://localhost:9003");
        Pet pet = petStoreConsumer.getPet(123);

        //Assert
        Assert.assertEquals(123, pet.getPetid());
    }

    @AfterAll
    public static void tearDown() {
        petStoreMock.close();
    }
}
```

The test injects mock pet store service url to the PetStoreConsumer. Let us now look at how the PetStoreConsumer and Pet code looks.

```java
public class PetStoreConsumer {
    private String petStoreUrl;

    public PetStoreConsumer(String petStoreUrl) {
        this.petStoreUrl = petStoreUrl;
    }

    public Pet getPet(int petId) {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Pet> response = restTemplate.exchange(URI.create(petStoreUrl + "/pets/" + petId), HttpMethod.GET, null, Pet.class);
        return response.getBody();
    }
}

public class Pet {
    private int petid;

    public void setPetid(int id) {
        this.petid = id;
    }

    public int getPetid() {
        return this.petid;
    }
}
```

### Provider - Runinng Contract as a test

Qontract leverages testing Frameworks to let you run contract as a test.
At the moment JUnit is supported. Each Scenario in translated to a junit test so that you get IDE support to run your contract.

Add a test class that extends "run.qontract.test.ContractAsATest". In the setUp method point to the location of the contract file and host and port of the provider.

```java
public class PetStoreContractTest extends QontractJUnitSupport {
    @BeforeAll
    public static void setUp() {
        File contract = new File("contract/service.contract");
        System.setProperty("path", contract.getAbsolutePath());
        System.setProperty("host", "localhost");
        System.setProperty("port", "port");
    }
}
```

Now all you have to do is make sure your provider app is running and then run your PetStoreContractTest just like any other JUnit test suite.
And results are displayed like JUnit tests.

You can also start and stop your application in the setUp and tearDown. Example: Here we are starting a Spring Provider Application.

```java
public class PetStoreContractTest extends QontractJUnitSupport {
    private static ConfigurableApplicationContext context;

    @BeforeAll
    public static void setUp() {
        File contract = new File("contract/service.contract");
        System.setProperty("path", contract.getAbsolutePath());
        System.setProperty("host", "localhost");
        System.setProperty("port", "8080");

        context = SpringApplication.run(Application.class);
    }

    @AfterAll
    public static void tearDown() {
        context.stop();
    }
}
```

