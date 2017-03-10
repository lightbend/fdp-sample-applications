#!/usr/bin/env bash
set -e

# Copies the Flink app to the master node.

SCRIPT=`basename ${BASH_SOURCE[0]}`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"

. "$DIR/../../bin/common.sh"
. "$HOME/.ssh/aws.sh"

# Used by show_help
function usage {
  cat >&2 << EOF

  Copy the Flink sample app to the master node. Requires your EC2 key-pair file
  name and file to be defined as environment variables in your \$HOME/.ssh/aws.sh,
  as used by "fdp-installer".

  Usage: $SCRIPT [options] [fdp-ssh_options]

  Where:

  -h | --help           Show help.
  -n | --no-exec        Just show the commands, but do not actually run them.
  fdp-ssh_options       Any option supported by \$FDP_INSTALLER_HOME/bin/fdp-ssh.sh.

  If you have more than one cluster or more than one master, you will be prompted
  to choose.
EOF
}

while [ $# -gt 0 ]
do
  case $1 in
    -h|--help)
      usage
      exit 0
      ;;
    -n|--no-exec)
      NOEXEC="echo running: "
      ;;
    *)
      ssh_ops[${#ssh_ops[@]}]=$1
      ;;
  esac
  shift
done

[ -z "$FDP_INSTALLER_HOME" ] && error "Must define FDP_INSTALLER_HOME to point to the directory where the 'fdp-installer' is located."

[ -z "$EC2_KEYPAIR_FILE" ] && error "EC2_KEYPAIR_FILE is required, but wasn't defined in ~/.ssh/aws.sh."

target_dir="source/core/target/scala-2.11"
jar=$(ls $target_dir/fdp*.jar)
if [ $? -ne 0 ]
then
  error "No app jar found in $target_dir. Please build the app first. (See the README)."
else
  echo "Using file $jar"
fi

# copy to a temporary directory so we can recursively copy it and have flink-app/...jar
# on the target.

appdir=tmp/flink-app
mkdir -p $appdir
cp $jar $appdir

jar2=$(basename $jar)

echo "$FDP_INSTALLER_HOME/bin/fdp-print-addresses.sh --quiet --master"
# For some weird reason, it exits silently unless I use the the while loop:
address=$($FDP_INSTALLER_HOME/bin/fdp-print-addresses.sh --quiet --master | while read a; do echo "$a"; done)

echo "Using address: $address"
$NOEXEC scp -i "$EC2_KEYPAIR_FILE" "${ssh_ops[@]}" -r $appdir "ubuntu@$address:"

rm -rf $appdir

if [ $? -ne 0 ]
then
  warn "Failed to scp $jar to ubuntu@$address:$jar."
else
  echo "Successful!"
fi



