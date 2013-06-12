#!/bin/bash -e

TRAINING_DATA=~/data/ptb_pos/pos_00-18.txt
VALIDATION_DATA=~/data/ptb_pos/pos_19-21.txt
INITIAL_MODEL=pos_output/lbfgs/crf_sgd16_out.ser

OUTPUT_DIR=pos_output/boosted/
LOG=$OUTPUT_DIR/log15.txt
OUTPUT=$OUTPUT_DIR/out15.ser
TRAINING_ERROR=$OUTPUT_DIR/train_err15.txt
VALIDATION_ERROR=$OUTPUT_DIR/validation_err15.txt

ITERATIONS=10
BATCH_SIZE=1000
MAX_TREE_DEPTH=5

# TODO: reduce RAM usage for whole data set.

./scripts/run.sh com.jayantkrish.jklol.pos.TrainBoostedPosCrf --training=$TRAINING_DATA --output=$OUTPUT --fgaInitialStepSize=1.0 --fgaIterations $ITERATIONS --fgaBatchSize=$BATCH_SIZE --initialModel=$INITIAL_MODEL --maxThreads 16 --rtreeMaxDepth $MAX_TREE_DEPTH $@ > $LOG

./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$OUTPUT --testFilename=$TRAINING_DATA > $TRAINING_ERROR
./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$OUTPUT --testFilename=$VALIDATION_DATA > $VALIDATION_ERROR