#! /bin/bash

set -e

export REPO="sphereio/payment-to-order-processor"
export DOCKER_TAG=`if [ "$TRAVIS_BRANCH" == "master" -a "$TRAVIS_PULL_REQUEST" = "false" ]; then echo "latest"; else echo ${TRAVIS_BRANCH/\//-} ; fi`

# used for debugging the build, may be suppressed in production
echo TRAVIS_BUILD_NUMBER=$TRAVIS_BUILD_NUMBER
echo TRAVIS_TAG=$TRAVIS_TAG
echo SHORT_COMMIT=$SHORT_COMMIT
echo REPO=$REPO
echo DOCKER_TAG=$DOCKER_TAG

echo "Building Docker image using tag '${REPO}:${SHORT_COMMIT}'."
docker build -t "${REPO}:${SHORT_COMMIT}" .

docker login -u="${DOCKER_USERNAME}" -p="${DOCKER_PASSWORD}"

echo "Adding additional tag '${REPO}:${DOCKER_TAG}' to already built Docker image '${REPO}:${SHORT_COMMIT}'."
docker tag $REPO:$SHORT_COMMIT $REPO:$DOCKER_TAG
echo "Adding additional tag '${REPO}:travis-${TRAVIS_BUILD_NUMBER}' to already built Docker image '${REPO}:${SHORT_COMMIT}'."
docker tag $REPO:$SHORT_COMMIT $REPO:travis-$TRAVIS_BUILD_NUMBER
if [ "$TRAVIS_TAG" ]; then
  echo "Adding additional tag '${REPO}:${TRAVIS_TAG}' to already built Docker image '${REPO}:${SHORT_COMMIT}'."
  docker tag $REPO:$SHORT_COMMIT $REPO:${TRAVIS_TAG};
fi
echo "Pushing Docker images to repository '${REPO}' (all local tags are pushed)."
docker push $REPO
docker logout
