#!/bin/bash -e

DATA_DIR=experiments/wikitables/WikiTableQuestions/
TRAINING_DATA=$DATA_DIR/data/training.tsv
ENVIRONMENT=experiments/wikitables/environment.lisp,experiments/wikitables/tables.lisp

./scripts/run.sh com.jayantkrish.jklol.experiments.wikitables.TrainSemanticParser --trainingData $TRAINING_DATA --tablesDir $DATA_DIR/csv/ --environment $ENVIRONMENT
