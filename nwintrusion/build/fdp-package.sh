#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
ROOT_DIR="${DIR}/.."

CONTENT_FILE=${DIR}/content.txt
VERSION_FILE=${DIR}/version.txt

CONTENT=$(cat $CONTENT_FILE)
VERSION=$(cat $VERSION_FILE)

OUTPUT_FILE=fdp-sample-nwintrusion-app-${VERSION}.tar.gz

function show_help {
  cat<< EOF
  This script packages the sample network intrusion app into $OUTPUT_FILE in the project root directory.
  Usage: $SCRIPT  [OPTIONS]
  (There are no command line options)
EOF
}

case $1 in
  -h|--help)
    show_help
    exit 0
   ;;
esac

tar -C ${ROOT_DIR} -cf ${ROOT_DIR}/${OUTPUT_FILE} ${CONTENT}

