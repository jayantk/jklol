#!/bin/bash -e

DATA_DIR=experiments/wikitables/WikiTableQuestions/
ENVIRONMENT=experiments/wikitables/resources/environment.lisp,experiments/wikitables/resources/tables.lisp

./scripts/run.sh com.jayantkrish.jklol.experiments.wikitables.WikiTableEval --tablesDir $DATA_DIR/csv/ --environment $ENVIRONMENT --noPrintOptions $@