#!/bin/bash -e

TRAINING_DATA=experiments/geoquery/data/sample.ccg

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.AlignmentLexiconInduction --trainingData $TRAINING_DATA --maxThreads 1 --emIterations 10 --sparseCpt --smoothing 0.1