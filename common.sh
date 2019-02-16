# Common functions used by other shell scripts in this project.
# Scripts that source this file should define a help function (used by error).
set -eu
: ${NOOP:=}

PROJECT_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$PROJECT_ROOT/version.sh"

# The help function that works for all the individual app build.sh scripts. The top-level
# build.sh overrides it.
function help {
  cat <<EOF
  $0: Build this sample app.
  usage: $0 [-h|--help] [-p|--push-docker-images] [-v|--version VERSION]
  where:
  -h | --help                 Show this help and exit.
  -p | --push-docker-images   Do the regular build, including Docker images, then push the images
                              to Docker Hub. Ignored if --print-docker-images is specified.
                              (default: build, but don't push the images). Ignored if --no-build specified.
  -v | --version VERSION      Use VERSION. (default: value set in version.sh)

  Run this script with "NOOP=info2 build.sh ..." to have it echo commands, but not run them.
EOF
}
function error {
  echo "ERROR: $0: $@" 1>&2
  help 1>&2
  exit 1
}
function warn {
  echo "WARN: $0: $@" 1>&2
}
# Called info2, because info is a *nix command
function info2 {
  echo "INFO: $0: $@"
}

function get_version {
  [[ $# -eq 0 ]] || [[ -z $1 ]] && error "No value specified for the version!"
  echo $1
}
