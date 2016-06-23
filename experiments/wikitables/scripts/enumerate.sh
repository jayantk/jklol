#!/bin/bash -e

DATA_DIR=experiments/wikitables/WikiTableQuestions/
TRAINING_DATA=$DATA_DIR/data/training.tsv
ENVIRONMENT=experiments/wikitables/resources/environment.lisp,experiments/wikitables/resources/tables.lisp
TYPE_DECLARATION=experiments/wikitables/resources/types.txt

./scripts/run.sh com.jayantkrish.jklol.experiments.wikitables.EnumerateLogicalForms --trainingData $TRAINING_DATA --tablesDir $DATA_DIR/csv/ --environment $ENVIRONMENT --typeDeclaration=$TYPE_DECLARATION $@
