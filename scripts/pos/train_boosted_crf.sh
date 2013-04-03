#!/bin/bash -e

TRAINING_DATA=~/data/ptb_pos/pos_00-18.txt
VALIDATION_DATA=~/data/ptb_pos/pos_19-21.txt

OUTPUT_DIR=pos_output/boosted/
LOG=$OUTPUT_DIR/log11.txt
OUTPUT=$OUTPUT_DIR/out11.ser
TRAINING_ERROR=$OUTPUT_DIR/train_err11.txt
VALIDATION_ERROR=$OUTPUT_DIR/validation_err11.txt

ITERATIONS=100
MAX_TREE_DEPTH=2

# TODO: reduce RAM usage for whole data set.

./scripts/run.sh com.jayantkrish.jklol.pos.TrainBoostedPosCrf --training=$TRAINING_DATA --output=$OUTPUT --fgaInitialStepSize=1.0 --fgaIterations $ITERATIONS --maxThreads 16 --rtreeMaxDepth $MAX_TREE_DEPTH $@ > $LOG

./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$OUTPUT --testFilename=$TRAINING_DATA > $TRAINING_ERROR
./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$OUTPUT --testFilename=$VALIDATION_DATA > $VALIDATION_ERROR