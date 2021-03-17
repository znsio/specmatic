Feature: Random Number API

  Scenario: Simple random number
    When GET /random
    Then status 200
    And response-body (number)
