#!/bin/bash -e

MODEL=out.ser
ENVIRONMENT=experiments/language_programming/data/environment.lisp

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.RunSemanticParser --model $MODEL --environment $ENVIRONMENT $@