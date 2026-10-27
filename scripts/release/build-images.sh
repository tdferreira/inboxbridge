#!/bin/sh
# Build exactly the tested runtime outputs. Never use the checkout as build context.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
context=${1:?Usage: build-images.sh CONTEXT}
: "${BACKEND_IMAGE:?Set BACKEND_IMAGE}"
: "${FRONTEND_IMAGE:?Set FRONTEND_IMAGE}"
: "${SOURCE_URL:?Set SOURCE_URL}"
: "${REVISION:?Set REVISION}"
: "${VERSION:?Set VERSION}"
for component in backend frontend; do
  if [ "$component" = backend ]; then image=$BACKEND_IMAGE; else image=$FRONTEND_IMAGE; fi
  docker build --file "$root/docker/release/Dockerfile.$component" \
    --build-arg SOURCE_URL="$SOURCE_URL" --build-arg REVISION="$REVISION" \
    --build-arg VERSION="$VERSION" --tag "$image" "$context"
done
