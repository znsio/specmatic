Feature: StockQuoteService

    Scenario: GetLastTradePrice
        Given type GetLastTradePriceInput
        """
        <SPECMATIC_TYPE>
            <tickerSymbol>(string)</tickerSymbol>
        </SPECMATIC_TYPE>
        """
        And type GetLastTradePriceOutput
        """
        <SPECMATIC_TYPE>
            <price>(number)</price>
        </SPECMATIC_TYPE>
        """
        When POST /stockquote
        And request-header SOAPAction "http://example.com/GetLastTradePrice"
        And request-body
        """
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header specmatic_occurs="optional"/><soapenv:Body><TradePriceRequest specmatic_type="GetLastTradePriceInput"/></soapenv:Body></soapenv:Envelope>
        """
        Then status 200
        And response-body
        """
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema"><soapenv:Header specmatic_occurs="optional"/><soapenv:Body><TradePrice specmatic_type="GetLastTradePriceOutput"/></soapenv:Body></soapenv:Envelope>
        """
