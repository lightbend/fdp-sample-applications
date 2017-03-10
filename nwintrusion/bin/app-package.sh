#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

files="$(ls $DIR/*.template)"
# all scripts besides current
binaries="$(ls $DIR/*.sh | grep -v $SCRIPT)"
TAR_FILE="nwin-app.tar"

function main {
  type tar >/dev/null 2>&1 || {
    echo >&2 "tar is required but it's not installed.  Please install tar to continue...";
    exit 1
  }

  # archive files
  file_list="$(printf '%s\n' "${files[@]}")"
  TARGET_FOLDER="$DIR/nwin-app"
  TARGET_FILE="$TARGET_FOLDER.tar"
  mkdir -p "$TARGET_FOLDER"
  cp $file_list "$TARGET_FOLDER"
  cd "$TARGET_FOLDER"
  tar -cvf "$TARGET_FILE" -C "$DIR" "${TARGET_FOLDER##*/}" > /dev/null
  cd $DIR
  rm -rf "$TARGET_FOLDER"
  echo "Created $TARGET_FILE"
}

main "$@"
exit 0
