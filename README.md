[![Build Status](https://travis-ci.org/commercetools/commercetools-payment-to-order-processor.svg?branch=dev-docker-build)](https://travis-ci.org/commercetools/commercetools-payment-to-order-processor)

## Goal of the service
In general orders are created from carts by the frontend. For redirect payments like Credit card 3D Secure, Paypal or Sofortüberweisung front end is confronted with an issue that in some cases there is a valid payment but no order as user did not reach front end's success URL, which creates an order from current cart. One of the use cases would be lost internet connection or accidentally closed tab after successfully issued payment. Scheduled processor ensures that for every successful payment and valid cart an order is created. For details can be found in [requirements](https://github.com/commercetools/commercetools-payment-to-order-processor/blob/master/doc/REQUIREMENTS.MD) document.

The service polls `PaymentTransactionStateChanged` messages from the commercetools platform since the `lastProcessedMessageTimeStamp` stored in a custom object in the platform.
If the PaymentTransaction matches the configured values and the TotalPrice of the Cart equals the amount of the transaction and is not already ordered then the service has to trigger the order generation.

## Creating the order
This service does not creates orders itself because that would result in a duplicated implementation of order generation. In the shop and in this service. Therefore it just calls an URL with encrypted cart.

The shop needs to provide an URL that accepts post request with encrypted body. The encryption algorithm is `Blowfish` with `Base64` String encoding.
## Using this service
Just start the docker container with required configuration (environment) variables set.
```
docker run commercetools/payment-to-order-processor:latest
```
The service will then run once. The best approach to run the service regularly would be using a tool like iron.io.

## Congfiguration values
### Required values

* Credentials for the commercetools platform
* Encryption Key and URL used for order creation API (***Handle your credentials and the encryption key with care.***)

Example part of a shell script:
```
export CTP_CREDENTIALS_CLIENTID=...
export CTP_CREDENTIALS_CLIENTSECRET=...
export CTP_CREDENTIALS_PROJECTKEY=...
export CREATEORDER_ENCRYPTIONKEY=YOUR_SECRET_ENCRYPTION_KEY
export CREATEORDER_ENDPOINT_URL=https://localhost/createOrder
```

### Optional values

* Comma seperated list -> on which paymenttransactions will be an order created.
* timeout for requests to the platform
* the time overlap prior to lastproccessed timestamp -> to eliminate problems at edge cases
* the container for the custom object (saving the timestamp)

Example part of a shell script:
```
export CREATEORDER_CREATEORDERON=AUTHORIZATION,CHARGE
export CTP_TIMEOUT=30000
export CTP_MESSAGEREADER_MINUTESOVERLAPPING=2
export CTP_CUSTOM_OBJECT_CONTAINERNAME=commercetools-payment-to-order-processor
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

For Travis CI use [build settings page](https://travis-ci.org/commercetools/commercetools-payment-to-order-processor/settings).

## Docker image
Following Docker images (tags) are created after successful Travis CI build:
 
 - on any commit - tags `travis-[build #]` and commit hash i.e.: `afd348f`
 - on _master_ commit with PR - tag `latest`
 - on other branches or master without PR - tag name `wip-` prefix (Work In Progress) followed by the branch name, i.e.: `wip-development`
 - on git tag push - additionally to above tags will be added:
    - docker tag equal to git tag name, i.e.: `v1.0.0`
    - docker tag `production`
    
###Docker image tags table:
    
  | Commit                          | commit hash<br/>`travis-[build #]` | `latest` | `wip-[branch-name]` | git tag | `production` | 
  |---------------------------------|:----------------------------------:|:--------:|:-------------------:|:-------:|:------------:|
  | master + PR                     |        **+**                       |   **+**  |          -          |    -    |      -       |
  | non-master or master without PR |        **+**                       |     -    |       **+**         |    -    |      -       |
  | git tag                         |        **+**                       |     -    |          -          |  **+**  |     **+**    |

`travis-build.sh` is used to build and deploy the docker images. 
The only things you should provide are:
  - build application jar to `target/payment-to-order-processor.jar`. 
    The project maven _pom.xml_ is setup like this by default. 
  - keep _Dockerfile_ in the root of the project. 
  Setup _Dockerfile_ to use target jar from location mentioned in the step above.
  - setup `$DOCKER_USERNAME` and `$DOCKER_PASSWORD` variables in the 
  [travis build settings](https://travis-ci.org/commercetools/commercetools-payment-to-order-processor/settings) 
  to deploy the new images to the hub.
  - _travis-build.sh_ will be started from _.travis.yml_ after successful project build and test.
    - the script requires `${SHORT_COMMIT}` environment variable, 
    which is used to be set in _.travis.yml_
    - the script will automatically assign image tags based on git branch, tag 
    and pull request values
  - push to `master` to build _latest_ image, push git tag to build image with docker tag reflecting git tag.
