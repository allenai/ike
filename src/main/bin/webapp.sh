#!/bin/bash

# Hack to set up the AWS env variables needed to read from S2
source ~/aws-env

# Regular stuff below
JVM_ARGS="-Xmx4g"
CLASS_NAME="org.allenai.dictionary.DictionaryToolWebapp"
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
SHORT_NAME=webapp
. "${SCRIPT_DIR}/run-class.sh" "$CLASS_NAME" "$SHORT_NAME" "$@"
