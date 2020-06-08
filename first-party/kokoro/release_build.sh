#!/bin/sh

# Fail on any error.
set -o errexit
# Display commands to stderr.
set -o xtrace

cd github/jib-extensions/first-party

git describe --tags  # non-zero exit code (no tag found) will fail the script
readonly EXTENSION_NAME=$( git describe --tags | sed -e 's/^v[0-9.]\+-//')

./gradlew ":${EXTENSION_NAME}:prepareRelease"
