#!/usr/bin/env bash

set -eu
: ${NOOP:=}

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$HERE/common.sh"

# This definition overrides the one in common.sh
function help {
  cat <<EOF
  $0: Build all sample apps.
  usage: $0 [-h|--help] [-p|--push-docker-images] [--print|--print-docker-images] [-v|--version VERSION] [app1 ...]
  where:
  -h | --help                 Show this help and exit.
  -p | --push-docker-images   Do the regular build, including Docker images, then push the images
                              to Docker Hub. Ignored if --print-docker-images is specified.
                              (default: build, but don't push the images). Ignored if --no-build specified.
  --print | --print-docker-images
                              Only print the names of the Docker images that would be built. Implies --no-build.
  -v | --version VERSION      Use VERSION. (default: value set in version.sh)
  app1 ...                    Process just these apps. The names have to match the directories under
                              "apps" directory, e.g., "killrweather". (default: build all of them)

  Run this script with "NOOP=info2 build.sh ..." to have it echo commands, but not run them.
EOF
}

# The only allowed arguments are the optional version string (no flag) and the
# flag to just print the Docker image names.
# Note that because VERSION is exported in version.sh, its value will be propagated
# to the subsequent build.sh script invocations.
print_docker_image_names=false
push_docker_images=
apps=()
while [[ $# -gt 0 ]]
do
  case $1 in
    -h|--h*)
      help
      exit 0
      ;;
    --print|--print-docker-image*)
      print_docker_image_names=true
      ;;
    -p|--push*)
      push_docker_images=--push-docker-images
      ;;
    -v|--version*)
      shift
      VERSION=$(get_version $@)
      ;;
    -*)
      error "Unrecognized argument $1"
      ;;
    *)
      apps+=( $1 )
  esac
  shift
done

[[ -n $VERSION ]] || error "Version string can't be empty!"
info2 "Using version $VERSION"

function list_app_dirs {
  for app in ${HERE}/apps/*
  do
    name=$(basename $app)
    case $name in
      sbt-common-settings) continue ;;
      *) echo $app ;;
    esac
  done
}

function make_app_dirs {
  for a in $@
  do
    dir=${HERE}/apps/$a
    [[ -d $dir ]] || error "specified app $a doesn't exist in ${HERE}/apps"
    echo $dir
  done
}

if [[ ${#apps[@]} -eq 0 ]]
then
  root_dirs=( $(list_app_dirs) )
else
  root_dirs=( $(make_app_dirs ${apps[@]}) )
fi

if $print_docker_image_names
then
  for d in ${root_dirs[@]}
  do
    if [[ -z $NOOP ]]
    then
      ( cd "$d/source/core"; sbt -no-colors "set version in ThisBuild := \"$VERSION\"" "show docker::imageNames" ) |  grep -e 'lightbend/[^)]*' --only-matching
    else
      ( cd "$d/source/core"; $NOOP sbt -no-colors "set version in ThisBuild := \"$VERSION\"" "show docker::imageNames" )
    fi
  done
  exit $?
fi

info2 "Process templates for config files to set the version string to $VERSION:"
$NOOP $HERE/process-templates.sh $VERSION

for d in ${root_dirs[@]}
do
  info2 "Running: NOOP=$NOOP $d/build.sh --version $VERSION $push_docker_images"
  NOOP=$NOOP $d/build.sh --version $VERSION $push_docker_images || error "Failed building $d"
done
