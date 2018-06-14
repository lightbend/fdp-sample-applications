#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
ROOT_DIR="${DIR}/.."

. $ROOT_DIR/version.sh

function usage {
  cat<< EOF
  fdp-killrweather:
  This script currently builds the software, including docker images (but doesn't push them).
  It also creates an archive of the code.
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
  echo "$0: ERROR: The version argument is required."
  usage
  exit 1
fi

echo "$0: Processing templates for config files:"

$ROOT_DIR/process-templates.sh $VERSION

OUTPUT_FILE_ROOT=fdp-killrweather-${VERSION}
OUTPUT_FILE=${OUTPUT_FILE_ROOT}.zip

echo "$0: Building the zip file of sources: $OUTPUT_FILE"

staging=$DIR/staging
rm -rf $staging
mkdir -p $staging

# Copy all files to the source zip; we'll filter some now and remove some of them next.
# Note that we copy project, which would pick up a lot of object stuff, but on a clean
# checkout and build this detritus won't exist.
mkdir -p $staging/$OUTPUT_FILE_ROOT
cd  ${ROOT_DIR}
for f in *
do
  case $f in
    target|release|Jenkins*|*Lightbend*.md)
      # skipping
      ;;
    *)
      cp -r $f $staging/$OUTPUT_FILE_ROOT/
      ;;
  esac
done

# Remove files and directories that shouldn't be in the distribution.
# Some of these should have been filtered in the previous do loop.
cd $staging
rm -f ${OUTPUT_FILE_ROOT}/README-Lightbend.md
# TODO: remove the next line after v1.2!
rm -f ${OUTPUT_FILE_ROOT}/README-Kubernetes.md
find ${OUTPUT_FILE_ROOT} \( -name whitesource.sbt -o -name WhitesourceLicensePlugin.scala \) -exec rm {} \;
find ${OUTPUT_FILE_ROOT} -type d | egrep 'project/(project|target)$' | while read d; do rm -rf "$d"; done
find ${OUTPUT_FILE_ROOT} -type d | egrep 'target$' | while read d; do rm -rf "$d"; done

echo running: zip -r ${OUTPUT_FILE} ${OUTPUT_FILE_ROOT}
zip -r ${OUTPUT_FILE} ${OUTPUT_FILE_ROOT}

rm -rf ${OUTPUT_FILE_ROOT}

echo "$0: Building the sample apps and docker images: $ROOT_DIR/build.sh"

$ROOT_DIR/build.sh $VERSION

echo "$0: NOTE: Use the fdp-release project to PUBLISH the Docker images!"
