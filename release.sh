#!/bin/bash

BRANCH=$(git rev-parse --abbrev-ref HEAD)

JAVA_VERSION=$(java -version 2>&1)

if [[ "$JAVA_VERSION" == *'17.0.'* ]]
then
	echo √ Using JDK17
else
  echo
  echo Error: JDK version is not 17
  echo
  echo To fix this, ensure that the result of "java -version" shows java 17.0.*
  exit 1
fi

if [ $BRANCH != "main" ]
then
	echo Error: Current branch is $BRANCH.
	echo
	echo To fix this, trigger this release from the main branch.
	exit 1
else
	echo √ Release is being done from main
fi

GITDIFF=`git diff --stat`

if [ ! -z "$GITDIFF" ]
then
	echo
	echo Error: Git working tree is dirty.
	echo
	echo To fix this, commit all changes, then trigger the release again.
	exit 1
else
	echo √ All files have been committed
fi

if [ -z "$1" ]
then
	echo
	echo Error: version argument is missing
	echo
	echo Provide the version to be published as the first argument to this script.
	echo
	echo The current version in version.properties is $(cat version.properties).
	exit 1
fi

OUTPUT=`git rev-parse $1 2>&1`

if [ $? = "0" ]
then
	echo
	echo Error: This tag already exists, most likely because a release with this version has already been done.
	echo
	echo To fix this, increment the version in version.properties, commit and push the change, and try again.
	exit 1
else
	echo √ Tag $1 does not yet exist
fi

set -e

if [[ "$1" == *-* ]]
then
	echo
	echo Error: The version number must be of the form major.minor.patch. It must contain no hyphens.
	echo
	exit 1
fi

ACTUAL_VERSION=`cat version.properties | sed s/version=//g`

if [ "$1" != $ACTUAL_VERSION ]
then
	echo
	echo Error: The specified version $1 does not match the version declared in version.properties which is $ACTUAL_VERSION.
	echo
	echo This check is intended to make sure that you are intentionally releasing the right version.
	echo
	echo The version that you are passing to this script must match that in version.properties.
	exit 1
else
	echo √ Specified version matches ./version.properties
fi

if [ ! -d ../specmatic-documentation ]
then
	echo
	echo Error: cannot find the specmatic-documentation checkout. Make sure that git@github.com:znsio/specmatic-documentation.git is checked out at ../specmatic-documentation
	exit 1
fi

if [ ! -f ../specmatic-documentation/_config.yml ]
then
	echo
	echo Error: cannot find specmatic-documentation/_config.yml, need to check this file to ensure that it is in sync with the latest version of Specmatic.
	exit 1
fi

DOCUMENTATION_VERSION=$(cat ../specmatic-documentation/_config.yml | grep latest_release | cut -d ' ' -f 2)

if [ "$DOCUMENTATION_VERSION" != $ACTUAL_VERSION ]
then
  echo
  echo Error: The version in specmatic-documentation/_config.yml is $DOCUMENTATION_VERSION, but the version in version.properties is $ACTUAL_VERSION.
  echo
  echo To fix this, update the version in specmatic-documentation/_config.yml to $ACTUAL_VERSION, commit and push the change, and try again.
  exit 1
else
  echo √ Version in specmatic-documentation/_config.yml matches version in version.properties
fi

echo


echo Building
./gradlew clean build

echo Pushing to remote
git push

echo Publishing
./gradlew publish

echo

echo Tagging release $1
git tag $1
git push origin $1

echo Release done

