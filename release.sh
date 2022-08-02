#!/bin/sh

BRANCH=`git rev-parse --abbrev-ref HEAD`

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
	echo √ Working tree is clean
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

echo

echo Building and publishing
./gradlew clean build publish

echo Tagging release $1
git tag $1
git push origin $1

echo Release done

