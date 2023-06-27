Feature: Test invocation of Qontract from within Karate

  Background:
    * def API = Java.type("in.specmatic.stub.API")

  Scenario: API call
    * def httpStub = API.createStubFromContracts(["./src/test/resources/random.spec"], [], "localhost", 9000)
    * httpStub.setExpectation('{"http-request": {"method": "GET", "path": "/random"}, "mock-http-response": {"status": 200, "body": 10}}')
    * url 'http://localhost:9000/random'
    * method get
    * status 200
    * print response
    * httpStub.close()
