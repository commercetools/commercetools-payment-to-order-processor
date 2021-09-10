# commercetools-payment-to-order-processor Service

![Build Status](https://travis-ci.com/commercetools/commercetools-payment-to-order-processor.svg?branch=master)
[![Docker Pulls](https://img.shields.io/docker/pulls/commercetools/payment-to-order-processor)](https://hub.docker.com/r/commercetools/payment-to-order-processor)

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
  - [Goal of the service](#goal-of-the-service)
  - [Creating the order](#creating-the-order)
  - [Using this service](#using-this-service)
  - [Congfiguration values](#congfiguration-values)
    - [Required values](#required-values)
    - [Optional values](#optional-values)
- [Build and release](#build-and-release)
  - [Build](#build)
  - [Run tests](#run-tests)
  - [Travis build settings](#travis-build-settings)
  - [Local run and debug](#local-run-and-debug)
  - [Docker image](#docker-image)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## Goal of the service
In general orders are created from carts by the frontend. For redirect payments like Credit card 3D Secure, Paypal or Sofortüberweisung shop front end is confronted with an issue that in some cases there is a valid payment but no order as user did not reach front end's success URL, which creates an order from current cart. One of the use cases would be lost internet connection or accidentally closed tab after successfully issued payment. Scheduled processor ensures that for every successful payment and valid cart an order can be still asynchronously created. More details on the process can be found [here](https://github.com/commercetools/commercetools-payment-to-order-processor/blob/master/doc/REQUIREMENTS.MD)

The service polls `PaymentTransactionStateChanged` and `PaymentTransactionAdded` messages from the commercetools platform since the `lastProcessedMessageTimeStamp` stored in a custom object in the platform.
If the PaymentTransaction type matches the configured values and the total price of the cart equals the amount of the transaction and is not already ordered then the service has to trigger order creation.

## Creating the order
This service does not create orders itself, because that would result in duplicated implementation of order 
creation: In the shop code base and in this service. Therefore, it just calls, over `HTTP GET`, a configured URL 
(i.e.: shop front end) with the encrypted cart ID as parameter "encryptedCartId". The encryption algorithm is `Blowfish` with `Base64` String encoding.

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
### Required values

* Credentials for the commercetools platform
* Encryption Key and URL used for order creation API (***Handle your credentials and the encryption key with care.***)

Example part of a shell script:
```
export CTP_CREDENTIALS_CLIENTID=...
export CTP_CREDENTIALS_CLIENTSECRET=...
export CTP_CREDENTIALS_PROJECTKEY=...
export CREATEORDER_ENCRYPTION_SECRET=YOUR_SECRET_ENCRYPTION_KEY
export CREATEORDER_ENDPOINT_URL=https://localhost/createOrder
```

### Optional values

* Comma seperated list -> on which paymenttransactions will be an order created.
* timeout for requests to the platform
* the time overlap prior to lastproccessed timestamp -> to eliminate problems at edge cases
* the container for the custom object (saving the timestamp)
* basic HTTP authentication for create order API endpoint
* if true, messages with type `PaymentTransactionAdded` will be processed (default: `true`)
* if true, messages with type `PaymentTransactionStateChanged` will be processed (default: `true`)

Example part of a shell script:
```
export CREATEORDER_CREATEORDERON=AUTHORIZATION,CHARGE
export CTP_TIMEOUT=30000
export CTP_MESSAGEREADER_MINUTESOVERLAPPING=2
export CTP_CUSTOM_OBJECT_CONTAINERNAME=commercetools-payment-to-order-processor
export CREATEORDER_ENDPOINT_AUTHENTICATION=<username>:<password>
export CTP_MESSAGES_PROCESSTRANSACTIONADDEDMESSAGES=true
export CTP_MESSAGES_PROCESSTRANSACTIONSTATECHANGEDMESSAGES=true
```

# Build and release

## Build
For build `maven` tool is used. `mvn verify` usually is enough for final build, installing may be skipped.

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


## Docker image
Following Docker images (tags) are created after successful Travis CI build:
 
 - on any commit - tags `travis-[build #]` and commit hash i.e.: `afd348f`
 - on _master_ commit with PR or git tag commit - docker tag `latest`
 - on other branches or master without PR - tag name `wip-` prefix (Work In Progress) followed by the branch name, i.e.: `wip-development`
 - on git tag push - additionally to above tags will be added:
    - docker tag equal to git tag name, i.e.: `v1.0.0`
    - docker tag `production`
    
### Docker image tags table:
    
| Commit                          | commit hash<br/>`travis-[build #]` | `latest` | `wip-[branch-name]` | git tag | `production` | 
|---------------------------------|:----------------------------------:|:--------:|:-------------------:|:-------:|:------------:|
| master + PR                     |        **+**                       |   **+**  |          -          |    -    |      -       |
| non-master or master without PR |        **+**                       |     -    |       **+**         |    -    |      -       |
| git tag                         |        **+**                       |   **+**  |          -          |  **+**  |     **+**    |

`travis-build.sh` is used to build and deploy the docker images. 
The only things you should provide are:
  - build application jar to `target/payment-to-order-processor.jar`. 
    The project maven _pom.xml_ is setup like this by default. 
  - keep _Dockerfile_ in the root of the project. 
  Setup _Dockerfile_ to use target jar from location mentioned in the step above.
  - setup `$DOCKER_USERNAME` and `$DOCKER_PASSWORD` variables in the 
  [travis build settings](https://travis-ci.com/commercetools/commercetools-payment-to-order-processor/settings) 
  to deploy the new images to the hub.
  - _travis-build.sh_ will be started from _.travis.yml_ after successful project build and test.
    - the script requires `${SHORT_COMMIT}` environment variable, 
    which is used to be set in _.travis.yml_
    - the script will automatically assign image tags based on git branch, tag 
    and pull request values
  - push to `master` to build _latest_ image, push git tag to build image with docker tag reflecting git tag.
