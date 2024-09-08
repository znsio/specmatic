#!/bin/sh

./gradlew assemble

if [ "$1" == "edge" ]
then
  tag=edge
else
  tag=$(cat version.properties | head -1 | cut -d = -f 2)
fi

docker build -t znsio/specmatic:$tag .

