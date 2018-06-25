#!/usr/bin/env bash

set -eu

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
cd $HERE

. ./version.sh

# The only allowed argument is the optional version string
[[ $# -gt 0 ]] && VERSION=$1
echo "$0: Using version $VERSION"

function process_templates {
  for t in "$@"
  do
    if [[ -f "$t" ]]
    then
      echo "  Processing template: $t"
      file=${t%.template}
      cat "$t" | sed -e "s/FDP_VERSION/$VERSION/g" > "$file"
    fi
  done
}

# Find the template files and change the version, generating the corresponding file.
process_templates *.template
# Ignore templates that end up in target directories:
find killr* -path '*/target' -prune -o -name '*.template' | while read f
do
  process_templates "$f"
done

