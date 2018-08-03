#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
ROOT_DIR="${DIR}/.."

. $ROOT_DIR/version.sh

function usage {
  cat<< EOF
  fdp-sample-apps:
  This script does the following:
  1. builds the software
  2. creates an archive of the code
  3. builds the Docker images
  4. pushes the Docker images to Docker Hub

  It also accepts options to skip building (just create an archive of the sources) and just
  print the names of the Docker images (for fdp-release use).

  Usage: $SCRIPT [VERSION] [options]

  VERSION                E.g., 0.4.0. If not provided, the value is read from ./version.sh

  -h | --help            This message.
  --print-docker-images  Just print the image name with tag (if any) and exit. (Implies --skip-build)
  --skip-build           Skip the actual build step (for testing the rest of the process)
EOF
}

print_docker_image_names=false
skip_build=false
while [ $# -ne 0 ]
do
  case $1 in
    -h|--help)
      usage
      exit 0
      ;;
    --skip*)
      skip_build=true
      ;;
    --print-docker-image*)
      print_docker_image_names=true
      skip_build=true
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

if $print_docker_image_names
then
  $ROOT_DIR/build.sh $VERSION --print-docker-image
  exit 0
fi

OUTPUT_FILE_ROOT=fdp-sample-apps-${VERSION}
OUTPUT_FILE=${OUTPUT_FILE_ROOT}.zip

echo "$0: Building the zip file of sources: $OUTPUT_FILE"

staging=$DIR/staging
rm -rf $staging
mkdir -p $staging

mkdir -p $staging/$OUTPUT_FILE_ROOT
echo "Copying files to $staging/$OUTPUT_FILE_ROOT:"
cd ${ROOT_DIR}
for f in *
do
  case $f in
    release|target|build-plugin) ;;  # skip
    *)
      echo "=== $f..."
      cp -r $f $staging/$OUTPUT_FILE_ROOT/$f
      ;;
  esac
done
cd $staging

# Remove files and directories that shouldn't be in the distribution:
find ${OUTPUT_FILE_ROOT} -type d | egrep 'project/(project|target)$' | while read d; do rm -rf "$d"; done
find ${OUTPUT_FILE_ROOT} -type d | egrep 'target$' | while read d; do rm -rf "$d"; done

echo running: zip -r ${OUTPUT_FILE} ${OUTPUT_FILE_ROOT}
zip -r ${OUTPUT_FILE} ${OUTPUT_FILE_ROOT}

rm -rf ${OUTPUT_FILE_ROOT}

echo "$0: Process templates for config files to set the version string:"
$ROOT_DIR/process-templates.sh $VERSION

if $skip_build
then
  echo "$0: Skipping SBT build, including Docker images"
  exit 0
fi

echo "$0: Build the sample apps and docker images: $ROOT_DIR/build.sh"
$ROOT_DIR/build.sh $VERSION

echo "$PWD: $0: NOTE: The Docker images should have been published to DockerHub!"

