#!/bin/bash -
# Usage: prepare_release.sh <extension project> <release version>
# Example: prepare_release.sh jib-layer-filter-extension-gradle 0.2.0

set -o errexit

# cd into first_party/ (parent of this script directory)
cd $( dirname "${BASH_SOURCE[0]}" )/..

EchoRed() {
  echo "$(tput setaf 1; tput bold)$1$(tput sgr0)"
}
EchoGreen() {
  echo "$(tput setaf 2; tput bold)$1$(tput sgr0)"
}

Die() {
  EchoRed "$1"
  exit 1
}

DieUsage() {
  EchoRed "Usage: prepare_release.sh <extension project> <release version> [<post-release-version>]"
  EchoRed
  EchoRed "Example: prepare_release.sh jib-layer-filter-extension-gradle 0.2.0"
  exit 1
}

# Usage: CheckVersion <version>
CheckVersion() {
  [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z]+)?$ ]] || Die "Version: $1 not in ###.###.###[-XXX] format."
}

[ $# -ne 2 ] && [ $# -ne 3 ] && DieUsage

EchoGreen '===== RELEASE SETUP SCRIPT ====='

PROJECT=$1
VERSION=$2
CheckVersion ${VERSION}
if [ -n "$3" ]; then
  POST_RELEASE_VERSION=$3
  CheckVersion ${POST_RELEASE_VERSION}
fi

if [[ $(git status -uno --porcelain) ]]; then
  Die 'There are uncommitted changes.'
fi

# Runs integration tests.
./gradlew :${PROJECT}:integrationTest --info --stacktrace

# Checks out a new branch for this version release (eg. 1.5.7).
BRANCH=${PROJECT}-release-v${VERSION}
git checkout -b ${BRANCH}

# Changes the version for release and creates the commits/tags.
echo | ./gradlew :${PROJECT}:release -Prelease.releaseVersion=${VERSION} ${POST_RELEASE_VERSION:+"-Prelease.newVersion=${POST_RELEASE_VERSION}"}

# Pushes the release branch and tag to Github.
git push origin ${BRANCH}
git push origin v${VERSION}-${PROJECT}

# File a PR on Github for the new branch. Have someone LGTM it, which gives you permission to continue.
EchoGreen 'File a PR for the new release branch:'
echo https://github.com/GoogleContainerTools/jib-extensions/pull/new/${BRANCH}

EchoGreen "Merge the PR after the extension is released."
