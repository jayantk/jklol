#!/bin/bash -e

TRAINING_DATA=experiments/geoquery/data/all_folds.ccg
LEXICON=experiments/geoquery/grammar/generated_lexicon.txt
RULES=experiments/geoquery/grammar/rules.txt

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.TrainSemanticParser --trainingData $TRAINING_DATA --lexicon $LEXICON --rules $RULES --skipWords --batchSize 1 --iterations 10 --output out.ser --logInterval 100