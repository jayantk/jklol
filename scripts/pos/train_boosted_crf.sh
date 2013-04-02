#!/bin/bash -e

TRAINING_DATA=~/data/ptb_pos/pos_00-18_1000sent.txt
VALIDATION_DATA=~/data/ptb_pos/pos_19-21.txt
LOG=log5.txt
OUTPUT=out5.ser
TRAINING_ERROR=train_err5.txt
VALIDATION_ERROR=validation_err5.txt

# TODO: reduce RAM usage for whole data set.

./scripts/run.sh com.jayantkrish.jklol.pos.TrainBoostedPosCrf --training=$TRAINING_DATA --output=$OUTPUT --fgaInitialStepSize=1.0 --fgaIterations 100 --maxThreads 16 --rtreeMaxDepth 10 $@ > $LOG

./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$OUTPUT --testFilename=$TRAINING_DATA > $TRAINING_ERROR
./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$OUTPUT --testFilename=$VALIDATION_DATA > $VALIDATION_ERROR