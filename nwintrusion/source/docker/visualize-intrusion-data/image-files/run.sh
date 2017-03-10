#!/usr/bin/env bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd -P )"
LIGHTNING_DIR=/usr/src/app
LIGHTNING_PORT=3000

cd $LIGHTNING_DIR
PORT=$LIGHTNING_PORT npm start &
cd $DIR/jupyter

jupyter notebook --no-browser --NotebookApp.ip=0.0.0.0 --NotebookApp.notebook_dir=$DIR/jupyter
