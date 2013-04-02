#!/bin/bash -e

TRAINING_DATA=~/data/ptb_pos/pos_00-18.txt
VALIDATION_DATA=~/data/ptb_pos/pos_19-21.txt
LOG=log6.txt
OUTPUT=out6.ser
TRAINING_ERROR=train_err6.txt
VALIDATION_ERROR=validation_err6.txt

# TODO: reduce RAM usage for whole data set.

./scripts/run.sh com.jayantkrish.jklol.pos.TrainBoostedPosCrf --training=$TRAINING_DATA --output=$OUTPUT --fgaInitialStepSize=1.0 --fgaIterations 50 --maxThreads 16 --rtreeMaxDepth 10 $@ > $LOG

./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$OUTPUT --testFilename=$TRAINING_DATA > $TRAINING_ERROR
./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$OUTPUT --testFilename=$VALIDATION_DATA > $VALIDATION_ERROR