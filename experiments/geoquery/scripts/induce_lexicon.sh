#!/bin/bash -e

TRAINING_DATA=experiments/geoquery/data/fold0.ccg

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.AlignmentLexiconInduction --trainingData $TRAINING_DATA