dictionary-builder
==================

## Run Locally

1. Run `sbt`
2. Enter the `reStart` command
3. Open http://localhost:8080 in a browser

The webapp will download a large file from the datastore upon first request.

## Deploy 
1. Run `sbt "deploy prod -Ddeploy.host=$HOST -Ddeploy.user.ssh_keyfile=$KEYPAIR"` where `$HOST` is your EC2 instance hostname and`$KEYPAIR` is the path to your AWS keypair.
2. Open http://$HOST:8080 in a browser
