#!/usr/bin/env bash

set -eu

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
cd $HERE

. ./version.sh

help() {
  cat <<EOF
Change the version string in all places where it's needed.

$0 [-h | --help] [VERSION]
where:
-h | --help   Show this help message
VERSION       If not provided, uses the value defined in ./version.sh
EOF
}

while [[ $# -gt 0 ]]
do
  case $1 in
    -h|--h*)
      help
      exit 0
      ;;
    -*)
      echo "$0: ERROR: Unrecognized argument $1"
      help
      exit 1
      ;;
    *)
      VERSION=$1
      ;;
  esac
  shift
done
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
      find "$d" -name '*.yaml.template' | while read f
      do
        process_templates "$f"
      done
      find "$d" -name 'training-pod.json.template' | while read f
      do
        process_templates "$f"
      done
      ;;
  esac
done

