IKE AI2-internal Details
========================

[Planning Document](https://docs.google.com/a/allenai.org/document/d/1DXx43Nrk-05ynk3KQm6_S6s3bQG15lf9dBEbTcKr24Y/edit#)


## Deploy
1. Get the IKE deployment key. It's in the `ai2-secure` bucket in S3, called `OkCorpusDeploymentKey.zip`.
2. Set the `AWS_PEM_FILE` variable to point at the private key file.
3. All deploy configs and scripts are in the AI2 private `deploy` repo, under the `ike` directory. In order to use them, clone the `deploy` project locally.
4. Run the script [`https://github.com/allenai/deploy/blob/master/ike/setup.sh`] from the deploy repo, which will set up symlinks to the paths expected by the deploy script. 
5. If you want to create a new machine, run the script in [`set_up_instance.sh`](https://github.com/allenai/deploy/blob/master/ike/scripts/set_up_instance.sh). It will create a new instance in EC2 and set it up for deployment. If you want to deploy to the existing machine, skip this step.
6. Run `sbt "deploy prod"` / `sbt "deploy test"`.

## Logging

IKEs logs to standard out and to a rotated logs file in `/local/deploy/ike/logs`, just like all other AI2 services. In addition to that, it logs to Papertrail at https://papertrailapp.com/groups/1690753. Papertrail is configured to write archives to a bucket in S3 named `ai2-papertrail-backup`. All archives go there, not only IKE, so to get the IKE logs you have to filter them out.

### Usage logging

IKE logs usage information, such as who is using the tool, how much are they using it, and which features are most popular. All that information goes into the logs together with all other logging information, but it uses the special logger named "Usage". There is a preconfigured search in Papertrail that shows this information at https://papertrailapp.com/groups/1690753/events?q=Usage%3A.

The key thing about the usage logger is that the first token in the log message is always the thing being used, i.e., `groupedSearch` or `similarPhrases` or some such. The rest of the message is extra information that's specific to the thing being used.

So far we have no tools to analyze this information further.
