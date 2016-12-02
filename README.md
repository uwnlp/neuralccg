# Global Neural CCG Parsing with Optimality Guarantees

This repository contains the code for replicating results from [Global Neural CCG Parsing with Optimality Guarantees](http://homes.cs.washington.edu/~kentonl/pub/llz-emnlp.2016.pdf) at EMNLP ([Lee et al., 2016](http://homes.cs.washington.edu/~kentonl/pub/llz-emnlp.2016.bib)).

## Dependencies
* Java 8
 * `sudo add-apt-repository ppa:webupd8team/java`
 * `sudo apt-get update`
 * `sudo apt-get install oracle-java8-installer`
* Maven
 * `sudo apt-get install maven`
* Bazel 0.1.4 or above (https://github.com/bazelbuild/bazel/releases)
 * `wget https://github.com/bazelbuild/bazel/releases/download/0.4.0/bazel-0.4.0-installer-linux-x86_64.sh`
 * `chmod +x bazel-0.4.0-installer-linux-x86_64.sh`
 * `./bazel-0.4.0-installer-linux-x86_64.sh --user`
 * `export PATH=$PATH:~/bin`
 
## Setting Up
* Make user the `JAVA_HOME` environment variable is set correctly, e.g.
 * `export JAVA_HOME=/usr/lib/jvm/java-8-oracle`
* Run `./setup.sh` to download data and compile JNI binaries.
* Download and extract CCGBank data from https://catalog.ldc.upenn.edu/LDC2005T13.
* Move the `ccgbank_1_1` directory to the `data` directory.

## Running Experiments
* The `experiments` directory contains `.conf` files that specify experiments and stages of the experiment.
* Use `./run.sh <config> <goal> <port>` to run an experiment that launches the specified goal stage and its dependent goal stages.
* An experiment summary (e.g. progress, logs, and intermediate results) is hosted locally, and is accessible via a web browser at the given port, e.g. `localhost:8080`.

### Learning
* Training: `./run.sh experiments/train.conf train 8080`
* Dev evaluation: `./run.sh experiments/eval.conf eval-checkpoints 8081`

### Demo
* Demo with the released model: `./run.sh experiments/demo.conf demo 8080`
* Go the experiment summary page to access the demo. 
