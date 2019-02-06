#!/usr/bin/env bash

set -eu
: ${NOOP:=}

HERE="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$HERE/version.sh"

function help {
  cat <<EOF
  $0: Build all sample apps.
  usage: $0 [-h|--help] [-p|--push-docker-images] [--print|--print-docker-images] [-v|--version VERSION] [app1 ...]
  where:
  -h | --help                 Show this help and exit.
  -p | --push-docker-images   Do the regular build, including Docker images, then push the images
                              to Docker Hub. Ignored if --print-docker-images is specified.
                              (default: build, but don't push the images)
  --print | --print-docker-images
                              Only print the names of the Docker images that would be built.
  -v | --version VERSION      Use VERSION. (default: value set in version.sh)
  app1 ...                    Process just these apps. The names have to match the directories under
                              "apps" directory, e.g., "killrweather". (default: build all of them)

  Run this script with "NOOP=echo build.sh ..." to have it echo commands, but not run them.
EOF
}

function error {
  echo "ERROR: $0: $@" 1>&2
  help 1>&2
  exit 1
}
function info {
  echo "INFO: $0: $@"
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
      [[ $# -eq 0 ]] || [[ -z $1 ]] && error "No value specified for the version!"
      VERSION=$1
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

info "Using version $VERSION"

for d in ${root_dirs[@]}
do
  info "Running: VERSION=$VERSION $d/build.sh $push_docker_images"
  NOOP=$NOOP VERSION=$VERSION $d/build.sh $push_docker_images
done
