#!/bin/bash

set -e

./gradlew assemble

docker build -t znsio/specmatic:$(cat version.properties | head -1 | cut -d = -f 2) .

