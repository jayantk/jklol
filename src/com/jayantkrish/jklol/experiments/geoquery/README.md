# Geoquery Semantic Parsing Experiment

This package contains code for an example experiment that learns a CCG
lexicon and trains a CCG semantic parser on the Geoquery data
set. Scripts, data, and additional resources for this experiment are
in the `experiments/geoquery` directory.

## Running the Experiment

First, <a href="https://github.com/jayantk/jklol">follow the
installation and build instructions</a>. Next, from the root jklol
directory, run:

    ./experiments/geoquery/scripts/run_singlefold_experiment.sh
	
This script will learn a lexicon and train a parser on a single fold
of Geoquery. It takes a few minutes to run and outputs trained models,
results and logging information to `experiments/geoquery/output/`. You
can watch training progress by running:

    less -S experiments/geoquery/output/log_fold0.txt 
    <enter "F" to have less update with file changes>

When training finishes, the script will print training and
test accuracy (the accuracy computation requires <a
href="https://stedolan.github.io/jq/">jq</a> to be installed.):

    > Training Accuracy
    > 0.946296
    > Test Accuracy
    > 0.816667

Training also populates the `experiments/geoquery/output` folder with
several files:

* `entity_lexicon.txt` -- lexicon of entity names provided with Geoquery
* `lexicon.fold0.ccg.txt` -- learned lexicon (on folds 1-9), including the entity names from above
* `log_fold0.txt` -- training log file
* `parser.fold0.ccg.ser` -- serialized CCG parsing model
* `training_error.fold0.ccg.json` -- the parser's predictions on the training examples (folds 1-9)
* `test_error.fold0.ccg.json` -- the parser's predictions on the test examples (fold 0)

At this point, you can inspect the model's predictions via the json
files. You can also run the parser on new questions:

    ./experiments/geoquery/scripts/parse.sh what is the biggest city in texas
    > logical form: (argmax:<<e,t>,<<e,i>,e>> (lambda ($0) (and:<t*,t> (city:<c,t> $0) (loc:<lo,<lo,t>> $0 texas:s))) (lambda ($0) (size:<lo,i> $0)))
    > answer: houston_tx

## Data

The original Geoquery data set is available <a
href="http://www.cs.utexas.edu/users/ml/nldata/geoquery.html">here</a>. This
experiment uses a version of this data set from the <a
href="https://bitbucket.org/yoavartzi/spf">University of Washington
Semantic Parsing Framework</a>. These examples are in
`experiments/geoquery/data` in a simple text format.

## Exploring the Code 

The Java code for this experiment is in `GeoqueryInduceLexicon.java`
and has extensive comments.
