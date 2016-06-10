# Geoquery Semantic Parsing Experiment

This package contains code for an example experiment that learns a CCG
lexicon and trains a CCG semantic parser on the Geoquery data set.

Additional data and scripts for running 

## Running the Experiment

From the root jklol directory, run:

    ./experiments/geoquery/scripts/induce_lexicon_cv.sh
	
This script will induce a lexicon and train a parser on a single fold
of Geoquery. It takes a few minutes to run and outputs logging
information to `experiments/geoquery/output`.

## Data

The original Geoquery data set is available <a
href="http://www.cs.utexas.edu/users/ml/nldata/geoquery.html">here</a>. This
experiment uses a version of this data set from the <a
href="https://bitbucket.org/yoavartzi/spf">University of Washington
Semantic Parsing Framework</a>.

== Directory structure ==
data - The GeoQuery data set reformatted into a jklol-readable format.

grammar - Example CCG lexicon and grammar rules, and a list of known
entity names for GeoQuery.

scripts - scripts for lexicon generation, training and testing a
semantic parser.


== Running the Experiment ==
The run_experiments.sh script will generate a lexicon, train a
semantic parser, and evaluate its training error. It should be run
from the root jklol directory, as follows:

./experiments/geoquery/scripts/run_experiment.sh

This script trains and evaluates a CCG semantic parser on the entire
GeoQuery data set using a (very naively) automatically generated
lexicon. The parser is trained as a linear model using the structured
perceptron algorithm.

Running the experiment will also save a semantic parsing model to
"out.ser" in the base directory. This model can be used to parse new
text using the parse.sh script. For example:

./experiments/geoquery/scripts/parse.sh city in Texas

