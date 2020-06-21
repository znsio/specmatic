Feature: Contract for the balance API

Background:
  * url "http://localhost:8080"

Scenario: api call
  Given path "/_qontract/stub_setup"
  And request {"http-request": {"method": "GET", "path": "/balance", "query": {"account_id": 10}}, "mock-http-response": {"status": 200, "body": {calls_left: 10, messages_left: 20}}}
  And method POST
  And status 200
  When path "/balance"
  And params {"account_id": 10}
  And method GET
  Then status 200
  And match response == {calls_left: 10, messages_left: 20}
