Feature: Contract for the balance API

Background:
  * url "http://localhost:8080"

  Scenario: api call
    When params {account_id: 10}
    And path "/balance"
    And method get
    Then status 200
    And match response == {calls_left: 10, messages_left: 20}

  Scenario: api call
    Given path "/_state_setup"
    And request {account_id: 10}
    And method POST
    And status 200
    When path "/balance"
    And params {account_id: 10}
    And method GET
    Then status 200

  Scenario: api call
    Given path "/locations_json"
    And request {"locations": [{"city": "Mumbai"}, {"city": "Bangalore"}]}
    When method POST
    Then status 200

  Scenario: api call
    Given path "/locations_json"
    When method GET
    Then status 200
    And match each response.locations == {"city": "#string", "id": "#number"}

  Scenario: api call
    Given path "/locations_xml"
    And request <locations><city><id>10</id><name>Mumbai</name></city></locations>
    When method POST
    Then status 200

#  Scenario: api call
#    Given path "/locations_xml"
#    When method GET
#    Then status 200
#    And print response
#    And match each //locations/city == <city><id>#number</id><name>#string</name></city>
