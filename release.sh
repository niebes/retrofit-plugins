#!/bin/sh -ex

: ${1?"Usage: $0 <[pre]major|[pre]minor|[pre]patch|prerelease>"}

#./mvnw scm:check-local-modification

current=1.17.0
release=1.17.1
next=1.18.0

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
