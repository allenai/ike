OkCorpus
========

## Resources
* [Live Demo](http://okcorpus.dev.ai2/)
* [Planning Document](https://docs.google.com/a/allenai.org/document/d/1DXx43Nrk-05ynk3KQm6_S6s3bQG15lf9dBEbTcKr24Y/edit#)

## Run Locally
1. Run `sbt`
2. Enter the `reStart` command
3. Open http://localhost:8080 in a browser

The webapp will download several large files from the datastore upon first request.

## Deploy
1. Get the OkCorpus deployment key. It's in the `ai2-secure` bucket in S3.
2. Set the `AWS_PEM_FILE` variable to point at the private key file.
3. If you want to create a new machine, run the script in [`set_up_instance.sh`](scripts/set_up_instance.sh). It will create a new instance in EC2 and set it up for deployment. If you want to deploy to the existing machine, skip this step.
4. Run `sbt "deploy prod"`.

## Creating an index
To create an index, you need the source text either as a directory of text files, or as one file with one document per line. Once you have that, run this in `sbt`:
```
runMain org.allenai.dictionary.index.CreateIndex --help
```
At the time of writing, this prints
```
Usage: CreateIndex [options]
 -d <value> | --destination <value>
       Directory to create the index in
 -b <value> | --batchSize <value>
       Batch size
 -c <value> | --clusters <value>
       URL of a file to load clusters from
 -t <value> | --textSource <value>
       URL of a file or directory to load the text from
 --help
```
The URLs for both clusters and corpora can be either file URLs or datastore URLs. A datastore URL looks like this: `datastore://{public|private}/<group>/<name>-v<version>.<ext>` for files, and `datastore://{public|private}/<group>/<name>-d<version>` for directories.

For clusters, I recommend using clusters from the private datastore, in any of the `org.allenai.brownclusters.*` groups. The most common one is `datastore://private/org.allenai.brownclusters.acl/c500-v1`.

When you have created the index, you can use it by modifying [`application.conf`](src/main/resources/application.conf) and restarting.
