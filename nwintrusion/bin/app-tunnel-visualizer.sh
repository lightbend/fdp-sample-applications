#!/usr/bin/env bash
set -e

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../version.sh"
. "$DIR/../../bin/common.sh"

# Used by show_help
HELP_MESSAGE="Uses ssh to connect to the node running the visualizer."
HELP_EXAMPLE_OPTIONS="-i ~/.ssh/foo.pem 55.10.200.1"
ARGS="public-address"

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  public_address        The public IP address or DNS name of the node running
                        the visualizer app (required - see documentation).
  -i ec2_key_pair_file  The AWS-generated pem file. By default, it will
                        look for the definition of EC2_KEYPAIR_FILE in
                        your environment or $HOME/.ssh/aws.sh.
EOF
)

address=
function parse_arguments {

  while [ $# -ne 0 ]
  do
    case "$1" in
      -i)
        shift
        EC2_KEYPAIR_FILE="$1"
      ;;
      -h|--help)   # Call a "show_help" function to display a synopsis, then exit.
      show_help
      exit 0
      ;;
      -n|--no-exec)   # Don't actually run the installation commands; just print (for debugging)
      NOEXEC="echo running: "
      ;;
      *)
      if [[ -z $address ]] ; then
        address="$1"
      else
        error "Extra option is not valid: $1"
      fi
      ;;
    esac
    shift
  done

  if [[ -z $address ]] ; then
    error "Must specify a public IP address or DNS name"
  fi

  if [[ -z $EC2_KEYPAIR_FILE ]] ; then
    [ -f "$HOME/.ssh/aws.sh" ] && source "$HOME/.ssh/aws.sh"
    if [[ -z $EC2_KEYPAIR_FILE ]] ; then
      error "EC2_KEYPAIR_FILE not defined, including attempt to source ~/.ssh/aws.sh."
    fi
  fi
}

parse_arguments "$@"

$NOEXEC ssh -L 8888:localhost:8888 -L 3000:localhost:3000 \
  -i "$EC2_KEYPAIR_FILE" "ubuntu@$address"
