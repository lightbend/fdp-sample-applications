#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
ROOT_DIR="${DIR}/.."

CONTENT_FILE=${DIR}/content.txt
CONTENT=$(cat $CONTENT_FILE)

function usage {
  cat<< EOF
  This script packages the sample applications into fdp-sample-apps-<version>.tar.gz
  in the project root directory.
  Usage: $SCRIPT VERSION [-h | --help]

  VERSION       E.g., 0.3.0. Required
  -h | --help   This message.
EOF
}

while [ $# -ne 0 ]
do
  case $1 in
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      echo "$0: ERROR: Unrecognized argument $1"
      usage
      exit 1
      ;;
    *)
      VERSION=$1
      ;;
  esac
  shift
done

if [[ -z "$VERSION" ]]
then
  echo "$0: ERROR: The version argument is required."
  usage
  exit 1
fi

OUTPUT_FILE=fdp-sample-apps-${VERSION}.tar.gz

staging=$DIR/staging
rm -rf $staging
mkdir -p $staging
for f in ${CONTENT}; do cp -r $f $staging/$f; done
cd $staging
echo running: tar -czf ${OUTPUT_FILE} ${CONTENT}
tar -czf ${OUTPUT_FILE} ${CONTENT}

for f in ${CONTENT}; do rm -rf $f; done

