#!/bin/bash

# Hack to set up the AWS env variables needed to read from S2. Assumes host
# has this file. This is a temp fix. 
# TODO: get rid of this when our ops infrastructure handles this issue.
# https://github.com/allenai/okcorpus/issues/35
source ~/aws-env

# Regular stuff below
JVM_ARGS="-Xmx4g"
CLASS_NAME="org.allenai.dictionary.DictionaryToolWebapp"
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
SHORT_NAME=webapp
. "${SCRIPT_DIR}/run-class.sh" "$CLASS_NAME" "$SHORT_NAME" "$@"
