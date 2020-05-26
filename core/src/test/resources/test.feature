Feature: Test invocation of Qontract from within Karate

Scenario: API call
  * def StubUtils = Java.type("run.qontract.stub.StubUtils")
#  * def httpStub = StubUtils.createStubFromContracts(["/Users/joel/Source/files/random.qontract"], [], "localhost", 9000)
#  * httpStub.createStub('{"http-request": {"method": "GET", "path": "/random"}, "mock-http-response": {"status": 200, "body": 10}}')
#  * url 'http://localhost:9000/random'
#  * method get
#  * status 200
#  * print response
#  * httpStub.close()
  * StubUtils.stubKafkaMessage("/Users/joel/Source/files/examples/kafka/test.qontract", '{"kafka-message": {"topic": "customer", "key": "name", "value": "test data"}}', "PLAINTEXT://localhost:9093")
  * StubUtils.testKafkaMessage("/Users/joel/Source/files/examples/kafka/test.qontract", "PLAINTEXT://localhost:9093", false)
