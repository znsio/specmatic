#!/bin/sh

BRANCH=`git rev-parse --abbrev-ref HEAD`

if [ $BRANCH != "main" ]
then
	echo Error: Current branch is $BRANCH.
	echo
	echo You should only release from main.
	exit 1
else
	echo √ Release is being done from main
fi

GITDIFF=`git diff --stat`

if [ ! -z "$GITDIFF" ]
then
	echo
	echo Error: Git working tree is dirty, please commit all changes before releasing an new version.
	exit 1
else
	echo √ Working tree is clean
fi

if [ -z "$1" ]
then
	echo
	echo Error: Provide the version to be published as the first argument.
	echo Currently `cat version.properties`
	exit 1
fi

OUTPUT=`git rev-parse $1 2>&1`

if [ $? = "0" ]
then
	echo
	echo Error: This tag already exists, most likely because a release with this version has already been done.
	exit 1
else
	echo √ Tag $1 does not yet exist
fi

set -e

ACTUAL_VERSION=`cat version.properties | sed s/version=//g`

if [ "$1" != $ACTUAL_VERSION ]
then
	echo
	echo Error: The specified version $1 does not match the version declared in version.properties which is $ACTUAL_VERSION.
	exit 1
else
	echo √ Specified version matches ./version.properties
fi

echo

echo Building and publishing
./gradlew clean build publish

echo Tagging release $1
git tag $1
git push origin $1

echo Release done

