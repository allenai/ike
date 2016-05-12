#!/bin/bash

# Regular stuff below
JVM_ARGS="-Xmx60G -Xms60G"
CLASS_NAME="org.allenai.ike.IkeToolWebapp"
SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
SHORT_NAME=webapp
. "${SCRIPT_DIR}/run-class.sh" "$CLASS_NAME" "$SHORT_NAME" "$@"
