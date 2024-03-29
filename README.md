# commercetools-payment-to-order-processor Service

[![CD](https://github.com/commercetools/commercetools-payment-to-order-processor/actions/workflows/cd.yml/badge.svg)](https://github.com/commercetools/commercetools-payment-to-order-processor/actions/workflows/cd.yml)
[![CI](https://github.com/commercetools/commercetools-payment-to-order-processor/actions/workflows/ci.yml/badge.svg)](https://github.com/commercetools/commercetools-payment-to-order-processor/actions/workflows/ci.yml)
[![Docker Pulls](https://img.shields.io/docker/pulls/commercetools/payment-to-order-processor)](https://hub.docker.com/r/commercetools/payment-to-order-processor)

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
  - [Goal of the service](#goal-of-the-service)
  - [Creating the order](#creating-the-order)
  - [Using this service](#using-this-service)
    - [Configure first processed message time bound](#configure-first-processed-message-time-bound)
  - [Congfiguration values](#congfiguration-values)
    - [Required](#required)
    - [Optional](#optional)
- [Build and release](#build-and-release)
  - [Build](#build)
  - [Configuration values for tests](#configuration-values-for-tests)
  - [Run tests](#run-tests)
  - [Local run and debug](#local-run-and-debug)
  - [Build and deploy](#build-and-deploy)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Goal of the service
In general orders are created from carts by the frontend. For redirect payments like Credit card 3D Secure, Paypal or Sofortüberweisung shop front end is confronted with an issue that in some cases there is a valid payment but no order as user did not reach front end's success URL, which creates an order from current cart. One of the use cases would be lost internet connection or accidentally closed tab after successfully issued payment. Scheduled processor ensures that for every successful payment and valid cart an order can be still asynchronously created. More details on the process can be found [here](https://github.com/commercetools/commercetools-payment-to-order-processor/blob/master/doc/REQUIREMENTS.MD)

The service polls `PaymentTransactionStateChanged` and `PaymentTransactionAdded` messages from the commercetools platform since the `lastProcessedMessageTimeStamp` stored in a custom object in the platform.
If the PaymentTransaction type matches the configured values and the cart is not already ordered then the service has to trigger order creation.

## Creating the order
This service does not create orders itself, because that would result in duplicated implementation of order 
creation: in the shop code base and in this service. Therefore, it just calls, over `HTTP GET`, a configured URL 
(i.e.: shop front end) with the cart ID as parameter "cartId".

## Using this service
Just start the docker container with required configuration (environment) variables set.
```
docker run commercetools/payment-to-order-processor:latest
```
Sevice has to be scheduled and executed i.e.: every 5 minutes. This can be achieved by using simple cron or services like iron.io.

### Configure first processed message time bound

If the service runs for the first time by default the service tries to fetch **ALL** the messages from the project,
which is usually not intended/expected and a customer wants to process payments only for the last couple of days/hours.

In this case **`lastUpdated`** key should be set in
[Custom Objects](https://docs.commercetools.com/http-api-projects-custom-objects.html#custom-objects)
endpoint to desired _first processed message (payment)_ time limit.

In the example below we want to start messages processing from _Friday, April 6, 2018 00:00:00 GMT_
(**Note**: CTP stores dates in GMT timezone)

```json
{
  "container": "commercetools-payment-to-order-processor",
  "key": "lastUpdated",
  "value": {
    "lastTimeStamp": "2018-04-06T00:00:00.000Z"
  }
}
```

## Congfiguration values

### Required
| Name | Description | Default value |
| --- | --- | --- |
| CTP_CREDENTIALS_CLIENTID | OAuth 2.0 `client_id` and can be used to obtain a token. |  |
| CTP_CREDENTIALS_CLIENTSECRET | OAuth 2.0 `client_secret` and can be used to obtain a token.	 | |
| CTP_CREDENTIALS_PROJECTKEY | commercetools project key | |
| CREATEORDER_ENDPOINT_URL | URL used for order creation API | |

### Optional
| Name | Description | Default value |
| --- | --- | --- |
| CREATEORDER_CREATEORDERON | Comma separated list of payment status, on which an order will be created. | `AUTHORIZATION,CHARGE` |
| CTP_CREATEORDERAPI_TIMEOUT | Milliseconds to wait for create order API response | 40000 |
| CREATEORDER_RESPONSE_LOGGINGLENGTHLIMIT | Max number of character to log from API response body. | 500
| CTP_TIMEOUT | Timeout for requests to CTP in ms | 30000 | 
| CTP_MESSAGEREADER_MINUTESOVERLAPPING | The time overlap prior to last proccessed timestamp in minute. The goal is to eliminate problems at edge cases caused by eventual consistency. | 2 | 
| CTP_CUSTOM_OBJECT_CONTAINERNAME | The container name for the custom object containing the processed message IDs | `commercetools-payment-to-order-processor` | 
| CREATEORDER_ENDPOINT_AUTHENTICATION | Basic HTTP authentication for create order API endpoint. | | 
| CTP_MESSAGES_PROCESSTRANSACTIONADDEDMESSAGES | If true, messages with type `PaymentTransactionAdded` will be processed | true | 
| CTP_MESSAGES_PROCESSTRANSACTIONSTATECHANGEDMESSAGES | If true, messages with type `PaymentTransactionStateChanged` will be processed | true |

# Build and release

## Build
For build `maven` tool is used. `mvn verify` usually is enough for final build, installing may be skipped.

## Configuration values for tests
| Name | Description | Required | Default value |
| --- | --- | --- | --- |
| IT_PROJECT_KEY | OAuth 2.0 `client_id` and can be used to obtain a token. | YES | |
| IT_CLIENT_ID | OAuth 2.0 `client_secret` and can be used to obtain a token.	 | YES ||
| IT_CLIENT_SECRET | commercetools project key | YES ||

## Run tests
The Integration Test needs credentials for the platform that are provided via OS env variables. 
One can use the following script.

```
#!/bin/bash
export IT_PROJECT_KEY=
export IT_CLIENT_ID=
export IT_CLIENT_SECRET=
mvn clean test
```

## Local run and debug

Follow the documentation how to run
[Spring Boot Maven Plugin](https://docs.spring.io/spring-boot/docs/current/maven-plugin/examples/run-debug.html) 

Additionally to simplify build:
  
  - in _Intellij IDEA_: use `Run/Debug` configuration to run `PaymentToOrderApplication` class.
  
  - it is possible to simplify Spring configuration avoiding environment variables setup:
  
    following Spring [24.3 Application Property Files](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html#boot-features-external-config-application-property-files)
    order it is easy and flexible to override default (environment variables) configuration 
    using custom local `application.properties` file. For this copy file
    [`src/main/resources/config/application.properties.skeleton`](/src/main/resources/config/application.properties.skeleton)
    to `application.properties` and fill required values.
    
    ```bash
    cp -i src/main/resources/config/application.properties.skeleton src/main/resources/config/application.properties
    ```
    
    The same approach could be used to _Run/Debug_ tests and integration tests locally, 
    but respectively in `/src/test/resources/config/` directory.

## Build and deploy

- This module is deployed as docker image to dockerhub.

- The build and deployment of the docker are done using github actions.

- On each push to the remote github repository, the github action [ci](https://github.com/commercetools
/commercetools-payment-to-order-processor/actions/workflows/ci.yml) is triggered, which builds the project and
 executes it`s tests. 
 
- The github action [cd](https://github.com/commercetools/commercetools-payment-to-order-processor/actions/workflows/cd
.yml) is used to create the docker-image and deploy it to dockerhub. This action is triggered when a git release tag is
 created.

 There are two ways to create the release-tag:
 - via command line

 ```bash
 git tag -a v1.0.1 -m "Minor text adjustments."
 ```

- via [Github UI](https://github.com/commercetools/commercetools-payment-to-order-processor/releases
