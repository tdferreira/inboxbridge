#!/bin/sh
# The caller must pass smoke checks and log in before invoking this script.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
context=${1:?Usage: publish-images.sh CONTEXT}
: "${BACKEND_REPOSITORY:?Set BACKEND_REPOSITORY}"
: "${FRONTEND_REPOSITORY:?Set FRONTEND_REPOSITORY}"
: "${SOURCE_URL:?Set SOURCE_URL}"
: "${REVISION:?Set REVISION}"
: "${VERSION:?Set VERSION}"
# No latest or major/minor aliases: rerunning an older release cannot move them.
python3 "$root/scripts/release/image-metadata.py" --repository "${SOURCE_URL#https://github.com/}" \
  --tag "$VERSION" --revision "$REVISION" >/dev/null
for component in backend frontend; do
  if [ "$component" = backend ]; then image=$BACKEND_REPOSITORY; else image=$FRONTEND_REPOSITORY; fi
  docker buildx build --platform linux/amd64,linux/arm64 \
    --file "$root/docker/release/Dockerfile.$component" \
    --build-arg SOURCE_URL="$SOURCE_URL" --build-arg REVISION="$REVISION" \
    --build-arg VERSION="$VERSION" --tag "$image:$VERSION" \
    --tag "$image:sha-$REVISION" --provenance=true --sbom=true --push "$context"
done
