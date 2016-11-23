- Create order API (called from `CREATEORDER_ENDPOINT_URL`) should be implemented as a GET service which accepts `encryptedCartId` query parameter with Blowfish encrypted cart id string, for instance:

  ```
  http://localhost:9000/API/order/createOrder?encryptedCartId=eDIeqi7seRTjdYRqm%2BOxy5Qcbc38dXaEgftHjQcr6M7HO125Tg%2BnUg%3D%3D
  ```
  
  The API returns:
  
  | Case                                                 |  Http Status | Http Body       |
  |------------------------------------------------------|--------------|-----------------|
  | A new order for the cart succesfully created         | 200 (OK)     | String order id |
  | The cart has ordered status and related order exists | 200 (OK)     | String order id |
  | <ul><li>cart id can't be decrypted</li> <li>cart can't be found by id</li> <li>cart doesn't have associated orderNumber (in `paymentInfo.payment[i].customField.reference`)</li> <li>cart has status `ORDERED`, but the order is not found</li> <li>cart doesn't have valid payment (charged or authorised)</li> <li>order can't be created for any reason (unexpected error</li></ul> | 400(bad request) | String short error message |


- Job runs every configured amount of minutes

- Gets ```lastProcessedMessageTimeStamp``` from custom-object endpoint (container "commercetools-payment-to-order-processor")

- If ```lastProcessedMessageTimeStamp``` is not defined than following query on message endpoint is used: ```type="PaymentTransactionStateChanged" AND state="Success"&sort=lastModifiedAt asc```

- If ```lastProcessedMessageTimeStamp``` is defined: ```type="PaymentTransactionStateChanged" AND state="Success" AND lastModifiedAt > (lastProcessedMessageTimeStamp - 2 minutes) &sort=lastModifiedAt asc```. 2 minutes should ensure that we also get messages, which has been created at the same time but were not returned on first query (edge case).

- PaymentTransactionStateChanged message example:
```
{
      "id": "043160d6-a86b-4ef9-af8d-a85071fefd17",
      "version": 1,
      "sequenceNumber": 5,
      "resource": {
        "typeId": "payment",
        "id": "d05451de-2063-44f0-a68a-fae04f852ef6"
      },
      "resourceVersion": 10,
      "type": "PaymentTransactionStateChanged",
      "transactionId": "8dde5bba-8eac-4324-9116-57b84c8fda94",
      "state": "Success",
      "createdAt": "2016-07-11T09:49:26.170Z",
      "lastModifiedAt": "2016-07-11T09:49:26.170Z"
}
```

- For each message with state value ```Success``` and TransactionType (http://dev.commercetools.com/http-api-projects-payments.html#transactiontype) defined in configuration as ```createOrderOn``` it should query for carts with message's payment ID and cart's state ```Active```. If cart's ```totalPrice``` equals transaction's ```amount``` it should create an order by cart. In case amounts do not match it should log an error:
```Cart with ID '043160d6-a86b-4ef9-af8d-a85071fefd17' and amount '150' EUR does not match its payment transaction amount of '100' EUR. Transaction ID '59463521-2c2e-4a45-a475-52d27a1b574c'```

- Created order should have (defined by the configuration rules) ```orderNumber``` and order's ```paymentState``` equals ```PAID``` set. In case of type ```custom-object``` it should get current value from defined container and key, increment by one and then use it for order creation. In case of type ```payment``` it should get ```orderNumber``` from ```payment.custom.fields.reference``` value.

Example configuration 1 (default):
```
{
	"orderNumber": {
    	"type": "paymentReference"
	},
    "createOrderOn": ["Authorization", "Charge"]
}
```

Example configuration 2:
```
{
	"orderNumber": {
    	"type": "custom-object",
    	"container": "numbers",
    	"key": "orderNumber"
	},
    "createOrderOn": ["Authorization"]
}
```

- After successful processing it should save ```lastProcessedMessageTimeStamp``` as custom-object
