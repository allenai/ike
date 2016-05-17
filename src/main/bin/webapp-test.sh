#!/bin/bash

SCRIPT_DIR="$( cd "$( dirname "$0" )" && pwd )"

# Look for a test password file, and source it if it exists.
CREDENTIALS=/opt/ops/var/s3/ops-keystore/database/ike/pgpass-test.sh
if [ -e "$CREDENTIALS" ]; then
  source "$CREDENTIALS"
  . "${SCRIPT_DIR}/webapp.sh"
else
  echo "Error: $CREDENTIALS not found; will fail to start in test."
  exit 1
fi
