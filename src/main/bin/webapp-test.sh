#!/bin/bash

# Look for a test password file, and source it if it exists.
CREDENTIALS=/opt/ops/var/s3/ops-keystore/database/ike/pgpass-test.sh
if [ -e "$CREDENTIALS" ]; then
  source "$CREDENTIALS"

# Regular stuff below
  JVM_ARGS="-Xmx60G -Xms60G"
  CLASS_NAME="org.allenai.ike.IkeToolWebapp"
  SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"
  SHORT_NAME=webapp
  . "${SCRIPT_DIR}/run-class.sh" "$CLASS_NAME" "$SHORT_NAME" "$@"
else
  echo "Warning: $CREDENTIALS not found; will fail to start in test."
fi
