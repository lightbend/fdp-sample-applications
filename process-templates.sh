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
      echo "===  $t"
      file=${t%.template}
      cat "$t" | sed -e "s/FDP_VERSION/$VERSION/g" > "$file"
    fi
  done
}

echo "Processing templates:"
for d in *
do
  case $d in
    release|target|build-plugin) ;;  # skip
    *)
      find "$d" -name 'values.yaml.template' | while read f
      do
        process_templates "$f"
      done
      ;;
  esac
done

