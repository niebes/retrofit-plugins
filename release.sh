#!/bin/sh -ex

current=1.18.2
release=1.18.3
next=1.19.0

git checkout -b release/${release}

./mvnw versions:set -D newVersion=${release}
git commit -am "Release ${release}"
./mvnw clean deploy scm:tag -D tag=${release} -D pushChanges=false -D skipTests -D dependency-check.skip

./mvnw versions:set -D newVersion=${next}-SNAPSHOT
git commit -am "Development ${next}-SNAPSHOT"

git push origin release/${release}
git push origin --tags

git checkout develop
git branch -D release/${release}
