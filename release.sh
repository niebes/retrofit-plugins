#!/bin/sh -ex

: ${1?"Usage: $0 <[pre]major|[pre]minor|[pre]patch|prerelease>"}

#./mvnw scm:check-local-modification

current=$(git describe --abbrev=0 || echo 0.0.0)
release=$(semver ${current} -i $1 --preid RC)
next=$(semver ${release} -i minor)

git checkout -b release/${release}

./mvnw versions:set -D newVersion=${release}
git commit -am "Release ${release}"
./mvnw clean deploy scm:tag -D tag=${release} -D pushChanges=false -D skipTests -D dependency-check.skip

./mvnw versions:set -D newVersion=${next}-SNAPSHOT
git commit -am "Development ${next}-SNAPSHOT"

git push origin
git push origin --tags

git checkout develop
git branch -D release/${release}
