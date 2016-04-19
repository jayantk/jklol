#!/bin/bash -e

source experiments/p3/scripts/config.sh

grep 'Recall:' $EXPERIMENT_DIR/**/train_err.txt | grep -o '([^)]*)' | sed 's/[(\/)]//g' | awk '{SUM += $1; TOT += $2} END {print "Training recall: " (SUM / TOT) " (" SUM " / " TOT ")"}'
grep 'Recall:' $EXPERIMENT_DIR/**/test_err.txt | grep -o '([^)]*)' | sed 's/[(\/)]//g' | awk '{SUM += $1; TOT += $2} END {print "Test recall: " (SUM / TOT) " (" SUM " / " TOT ")"}'
