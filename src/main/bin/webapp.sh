#!/bin/bash

JVM_ARGS="-Xmx60G -Xms60G"
CLASS_NAME="org.allenai.ike.IkeToolWebapp"
SHORT_NAME=webapp
#SCRIPT_DIR=$1
. "${SCRIPT_DIR}/run-class.sh" "$CLASS_NAME" "$SHORT_NAME" "$@"
