[![Build Status](https://semaphoreci.com/api/v1/allenai/ike/branches/master/shields_badge.svg)](https://semaphoreci.com/allenai/ike)
IKE (Interactive Knowledge Extraction)
======================================

## Usage Guide
[First time users, please refer the IKE Getting Started Guide](USAGE-GUIDE.md)

[Installation instructions (thanks to Ben Yang, Univ. Texas at Austin)](installing_ike_contributed_by_UTAustin.pdf)

## Run Locally
1. Install PostgreSQL locally and create a database for use by IKE. IKE needs this to store the tables you create.
2. Modify the `Tablestore` key value setting in the [IKE config] (https://github.com/allenai/ike/blob/master/src/main/resources/application.conf) with appropriate database JDBC URL and credentials.
3. Run `sbt`.
4. Enter the `reStart` command.
5. Open http://localhost:8080 in a browser.

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
ike/runMain org.allenai.ike.index.CreateIndex --help
```
At the time of writing, this prints
```
Usage: CreateIndex [options]
 -d <value> | --destination <value>
       Directory to create the index in
 -b <value> | --batchSize <value>
       Batch size
 -t <value> | --textSource <value>
       URL of a file or directory to load the text from
 --help
```
The URL for the corpus can be either a file URL or a datastore URL. A datastore URL looks like this: `datastore://{public|private}/<group>/<name>-v<version>.<ext>` for files, and `datastore://{public|private}/<group>/<name>-d<version>` for directories.

NOTE: The private datastore resources are for AI2 users only.

You can also point to a corpus directory in your file system by using a `file://` URL, for e.g., `file://path/to/my/corpus/directory`.

When you have created the index, you can use it by modifying [`application.conf`](src/main/resources/application.conf) and restarting.

### Index Size Limits

A Blacklab index size will typically be 12-13x the size of the input corpus being indexed.
Our testing on an Amazon [`r3.2xlarge`](https://aws.amazon.com/ec2/instance-types/) instance indicated that an index size of upto 2 GB could be processed with reasonable speed when loaded into IKE. If you have a large corpus, one way to get around the size limits is to shard the corpus and create multiple indexes, each within the size limit to load into IKE.


## Supported Platforms

IKE has been built, tested and verified to work on Linux and Mac systems. However, if you are interested in developing / running on Windows, please see the instructions from [diniluca1789] (https://github.com/diniluca1789), an external IKE user who got it successfully building and running on Windows, as described in [this thread](https://github.com/allenai/ike/issues/225).


## AI2 Internal Information
AI2 internal users, please go to [this link](README-AI2.md).
