#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
ROOT_DIR="${DIR}/.."

. $ROOT_DIR/version.sh

CONTENT_FILE=${DIR}/content.txt
CONTENT=$(cat $CONTENT_FILE)

function usage {
  cat<< EOF
  fdp-sample-apps:
  This script currently DOES NOT BUILD this package. It just creates an archive of the code.
  Usage: $SCRIPT [VERSION] [-h | --help]

  VERSION       E.g., 0.3.0. Required, but defaults to the value in $ROOT_DIR/version.sh
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
  echo "$0: ERROR: VERSION is not defined. Pass the value as an argument or check the definition in version.sh"
  usage
  exit 1
fi

OUTPUT_FILE_ROOT=fdp-sample-apps-${VERSION}
OUTPUT_FILE=${OUTPUT_FILE_ROOT}.zip

echo "$0: Building the zip file of sources: $OUTPUT_FILE"

staging=$DIR/staging
rm -rf $staging
mkdir -p $staging

mkdir -p $staging/$OUTPUT_FILE_ROOT
for f in ${CONTENT}
do
  cp -r ${ROOT_DIR}/$f $staging/$OUTPUT_FILE_ROOT/$f
done
cd $staging

# Remove files and directories that shouldn't be in the distribution:
find ${OUTPUT_FILE_ROOT} -type d | egrep 'project/(project|target)$' | while read d; do rm -rf "$d"; done
find ${OUTPUT_FILE_ROOT} -type d | egrep 'target$' | while read d; do rm -rf "$d"; done

echo running: zip -r ${OUTPUT_FILE} ${OUTPUT_FILE_ROOT}
zip -r ${OUTPUT_FILE} ${OUTPUT_FILE_ROOT}

rm -rf ${OUTPUT_FILE_ROOT}

echo "$0: Building the sample apps and docker images: $ROOT_DIR/build.sh"

$ROOT_DIR/build.sh

echo "$0: NOTE: Use the fdp-release project to PUBLISH the Docker images!"

