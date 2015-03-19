#!/bin/bash -e

TEST_DATA=experiments/geoquery/data/all_folds.ccg
MODEL=out.ser

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.TestSemanticParser --testData $TEST_DATA --model $MODEL