#!/bin/bash -e

TRAINING_DATA=experiments/geoquery/data/sample.ccg
TEST_DATA=experiments/geoquery/data/sample.ccg

RULES=experiments/geoquery/grammar/rules.txt

LEXICON=lexicon.txt
MODEL=out.ser

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.AlignmentLexiconInduction --trainingData $TRAINING_DATA --lexiconOutput $LEXICON --maxThreads 1 --emIterations 10 --smoothing 0.1

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.TrainSemanticParser --trainingData $TRAINING_DATA --lexicon $LEXICON --rules $RULES --skipWords --batchSize 1 --iterations 20 --output $MODEL --logInterval 10 --beamSize 500 --returnAveragedParameters

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.TestSemanticParser --testData $TEST_DATA --model $MODEL