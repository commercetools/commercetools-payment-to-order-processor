# Create Order API Requirements

Create order API (called from `CREATEORDER_ENDPOINT_URL`) should be implemented as a GET service which accepts 
`cartId` query parameter with cart id string, for instance:

  ```
  http://localhost:9000/API/order/createOrder?cartId=159da30a-cb90-4238-96fd-9a35433c67f7
  ```
  
## API HTTP response

  - Response mime type is: **`application/json`**
  
  - Response body is an *optional* JSON value.
  
  - Response statuses: 
  
    | Http Status     | Meaning                                                           | How to process                                                                                                      |
    |-----------------|-------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
    | *201 (CREATED)* | A new order for the cart has been successfully created            | Mark the order as created and never call again                                                                      |
    | *200 (OK)*      | The request is correct, but an order can't be created             | Mark the order as not processable and never call again                                                              |
    | *any other*     | An order can't be created for the request because of some reason. | Figure out the issue (for instance, review the logs of the shop) and retry later. Don't mark the order as processed |
    
    As mentioned above, in all the cases the body is optional, but in a case of non-201 status it is recommended 
    to describe the reason why the order hasn't been created. Expected issues are:

    <table>
      <tr>
        <th>Http Status</th>
        <th>Error description</th>
        <th>Required action</th>
      </tr>
      <tr>
        <td rowspan='3' valign='middle'>200 (OK)</td>
        <td>cart has status <i>ORDERED</i></td>
        <td>potentially concurrent modification issue - an action is not required</td>
      </tr>
      <tr>
        <td>cart doesn't have a valid payment (charged or authorised)</td>
        <td>unexpected state, should be investigated independently for each case</td>
      </tr>
      <tr>
        <td>cart payment is revoked (cancelled or refunded)</td>
        <td>no special action required</td>
      </tr>
      <tr>
        <td rowspan='4' valign='middle'>not 200 or 201</td>
      </tr>
      <tr>
        <td>cart can't be found by id</td>
        <td>verify the cart fetching process</td>
      </tr>
      <tr>
        <td>cart doesn't have an associated orderNumber (in <i>paymentInfo.payment[i].customField.reference</i>)</td>
        <td>verify the cart creation workflow</td>
      </tr>
      <tr>
        <td>any other reason</td>
        <td>&nbsp;</td>
      </tr>
    </table>

## Processor Workflow

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

- For each message with state value ```Success``` and TransactionType (http://dev.commercetools.com/http-api-projects-payments.html#transactiontype) 
defined in configuration as ```createOrderOn``` it should query for carts with message's payment ID and cart's state ```Active```.
If the cart: 
  - has state *other* than `ORDERED`
  
    and
    
  - the cart's `totalPrice` equals transaction's `amount` 
it should create an order by cart. 
In case amounts do not match it should log an error:

    ```
    Cart with ID '043160d6-a86b-4ef9-af8d-a85071fefd17' and amount '150' EUR does not match its payment transaction amount of '100' EUR. 
    Transaction ID '59463521-2c2e-4a45-a475-52d27a1b574c'
    ```

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
