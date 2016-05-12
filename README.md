IKE (Interactive Knowledge Extraction)
======================================

## Resources
[Live Demo](http://ike.allenai.org/)

## Run Locally
1. Run `sbt`
2. Enter the `reStart` command
3. Open http://localhost:8080 in a browser

The webapp will download some large files from the [datastore](https://github.com/allenai/datastore) upon first request. This could take several minutes. You will see a series of messages that look like the following:

```
ike 2016-05-11 13:46:27,070 INFO  org.allenai.datastore.Datastore - Downloading org.allenai.dictionary.indexes/WaterlooFilteredV2Shard4-d1.zip from the public datastore. 1.23 GB bytes read.
ike 2016-05-11 13:46:28,260 INFO  org.allenai.datastore.Datastore - Downloading org.allenai.dictionary.indexes/WaterlooFilteredV2Shard4-d1.zip from the public datastore. 1.23 GB bytes read.
ike 2016-05-11 13:46:44,521 INFO  org.allenai.datastore.Datastore - Downloading org.allenai.dictionary.indexes/WaterlooFilteredV2Shard4-d1.zip from the public datastore. 1.23 GB bytes read.
```
On subsequent runs, the service will start up quickly as the downloaded indexes are cached locally.

## Creating and using an Index
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

You can also specify corpus and/or cluster files in your file system by using a `file://` URL, for e.g., `file://path/to/my/corpus/file`.

NOTE: The private datastore resources are for AI2 users only.

When you have created the index, you can use it by modifying [`application.conf`](src/main/resources/application.conf) and restarting.

## AI2 Internal Information
AI2 internal users, please go to [this link](README-AI2.md).
