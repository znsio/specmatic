Feature: Test invocation of Qontract from within Karate

  Background:
    * def API = Java.type("run.qontract.stub.API")

  Scenario: API call
    * def httpStub = API.createStubFromContracts(["./src/test/resources/random.qontract"], [], "localhost", 9000)
    * httpStub.createStub('{"http-request": {"method": "GET", "path": "/random"}, "mock-http-response": {"status": 200, "body": 10}}')
    * url 'http://localhost:9000/random'
    * method get
    * status 200
    * print response
    * httpStub.close()

#  Scenario: Kafka test
#    * API.stubKafkaMessage("/Users/joel/Source/files/examples/kafka/test.qontract", '{"kafka-message": {"topic": "customer", "key": "name", "value": "test data"}}', "PLAINTEXT://localhost:9093")
#    * API.testKafkaMessage("/Users/joel/Source/files/examples/kafka/test.qontract", "PLAINTEXT://localhost:9093", false)
