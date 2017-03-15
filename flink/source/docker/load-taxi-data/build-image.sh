#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
DOCKER_TAG="latest"
DOCKER_REPO="fdp-nyc-taxiride-load-data"

function build_image {
  docker build --no-cache=true $DIR -t $DOCKER_USERNAME/$DOCKER_REPO:$DOCKER_TAG
}

function push_image {
	docker login -u $DOCKER_USERNAME -p $DOCKER_PASSWORD
	docker push $DOCKER_USERNAME/$DOCKER_REPO:$DOCKER_TAG
}

function parse_arguments {
  while :; do
    case "$1" in
      --docker-username)
      if [ -n "$2" ]; then
        DOCKER_USERNAME=$2
        shift 2
        continue
      else
        printf '"--docker-username" requires a non-empty option argument.\n'
        show_help
        exit 1
      fi
      ;;
      --docker-tag)
      if [ -n "$2" ]; then
        DOCKER_TAG=$2
        shift 2
        continue
      else
        printf '"--docker-tag" requires a non-empty option argument.\n'
        show_help
        exit 1
      fi
      ;;
      --docker-repo)
      if [ -n "$2" ]; then
        DOCKER_REPO=$2
        shift 2
        continue
      else
        printf '"--docker-repo" requires a non-empty option argument.\n'
        show_help
        exit 1
      fi
      ;;
      --docker-password)
      if [ -n "$2" ]; then
        DOCKER_PASSWORD=$2
        shift 2
        continue
      else
        printf '"--docker-password" requires a non-empty option argument.\n'
        show_help
        exit 1
      fi
      ;;
      --build)
        BUILD="TRUE"
        shift 1
        continue
      ;;
      --push)
        PUSH="TRUE"
        shift 1
        continue
      ;;
      -h|--help)   # Call a "show_help" function to display a synopsis, then exit.
      show_help
      exit
      ;;
      --)          # End of all options.
      shift
      break
      ;;
      '')          # End of all options.
      break
      ;;
      *)
      printf 'The option is not valid...: %s\n' "$1" >&2
      show_help
      exit 1
      ;;
    esac
    shift
  done
}

function show_help {
  cat<< EOF
  Builds the image for the data input part of this app.
  Usage: $SCRIPT  [OPTIONS]

  eg: ./$SCRIPT --docker-username lightbend --docker-password secret

  Options:
  --docker-tag                Tag for the image. Default: latest.
  --docker-repo               The name of the image. Default: fdp-nw-intrusion-load-data.
  --docker-username           Username on dockerhub. Part of the repo format.
                              <hub-user>/<repo-name>:<tag>
  --docker-password           Password for the account on dockerhub.
  --build                     Image will be built.
  --push                      Image will be pushed.
                              Will use the same repo as the one used for the build.
  -h, --help                  prints this message.
EOF
}

function main {
  parse_arguments "$@"

  if [ -z "$DOCKER_USERNAME" ]; then
    echo "A docker account must be provided..."
    show_help
    exit 1
  fi

  if [ -z "$BUILD" ] && [ -z "$PUSH" ]; then
    BUILD="TRUE"
    PUSH="TRUE"
  fi

  if [ -z "$DOCKER_PASSWORD" ] && [ -n "$PUSH" ]; then
    echo "A docker password must be provided..."
    show_help
    exit 1
  fi

  if [ -n "$BUILD" ]; then
    build_image
  fi

  if [ -n "$PUSH" ]; then
    push_image
  fi
}

main "$@"
exit 0

