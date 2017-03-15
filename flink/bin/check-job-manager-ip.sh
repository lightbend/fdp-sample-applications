#!/usr/bin/env bash
set -e

# Checks that the job manager IP address routing is set correctly in the master node's
# /etc/hosts.

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"


# Used by show_help
HELP_MESSAGE="Checks that the job manager IP address routing is set correctly in the /etc/hosts
  file for a master node."
HELP_EXAMPLE_OPTIONS="ip-10-10-1-115"

ARGS="job_manager-address"
OPTIONAL_ARGS="[fdp-ssh_options]"

# The ')' must be on the line AFTER the EOF!
HELP_OPTIONS=$(cat <<EOF
  job_manager_address   is either the value shown in the Flink DC/OS web UI for
                        the property "jobmanager.rpc.address" or the corresponding
                        IP address, as shown in the examples (see the README for
                        this application for details on how to access this UI).
                        This argument must be first.
  fdp-ssh_options       Any option supported by \$FDP_INSTALLER_HOME/bin/fdp-ssh.sh.

  If you have more than one cluster or more than one master, you will be prompted
  to choose.
EOF
)

while [ $# -gt 0 ]
do
  case $1 in
    -h|--help)
      show_help
      exit 0
      ;;
    -n|--no-exec)
      NOEXEC="echo running: "
      ;;
    -*)
      ssh_ops[${#ssh_ops[@]}]=$1
      ;;
    *)
      if [ -z "$address" ]
      then
        address="$1"
      else
        # If we already have an address, then assume this is an ssh option without a leading "-"
        ssh_ops[${#ssh_ops[@]}]=$1
      fi
      ;;
  esac
  shift
done

[ -z "$FDP_INSTALLER_HOME" ] && error "Must define FDP_INSTALLER_HOME to point to the directory where the 'fdp-installer' is located."

$NOEXEC $FDP_INSTALLER_HOME/bin/fdp-ssh.sh "${ssh_ops[@]}" --master --command "grep $address /etc/hosts"

if [ $? -ne 0 ]
then
  warn "The /etc/hosts doesn't contain the required entry for $address" \
       "Run the following command to edit the file as discussed in the README:" \
       "  $FDP_INSTALLER_HOME/bin/fdp-ssh.sh --master"
else
  echo "Successful! The /etc/hosts file has the required entry for $address."
fi



