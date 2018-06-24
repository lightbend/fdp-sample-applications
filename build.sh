#!/usr/bin/env bash

set -eux

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

echo ${HERE}

. "$HERE/version.sh"

# The only allowed argument is the optional version string
# Note that because VERSION is exported in version.sh, its value will be propagated
# to the subsequent build.sh script invocations.
[[ $# -gt 0 ]] && VERSION=$1
echo "$0: Using version $VERSION"

${HERE}/bigdl/build.sh

${HERE}/nwintrusion/build.sh

${HERE}/flink/build.sh

${HERE}/kstream/build.sh
