#!/bin/bash -e

TRAINING_DATA=~/data/ptb_pos/pos_00-18.txt
VALIDATION_DATA=~/data/ptb_pos/pos_19-21.txt
OUTPUT_PREFIX=pos_output/lbfgs/crf_lbfgs_all3_

LOG_SUFFIX=log.txt
MODEL_SUFFIX=out.ser
TRAIN_SUFFIX=train.txt
VALIDATION_SUFFIX=validation.txt

LOG=$OUTPUT_PREFIX$LOG_SUFFIX
MODEL=$OUTPUT_PREFIX$MODEL_SUFFIX
TRAIN=$OUTPUT_PREFIX$TRAIN_SUFFIX
VALIDATION=$OUTPUT_PREFIX$VALIDATION_SUFFIX

#./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$MODEL --initialStepSize=1.0 --iterations 10 --l2Regularization 0.0001 --batchSize 16 --maxThreads 16 $@ > $LOG
./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$MODEL --lbfgs --lbfgsIterations 100 --lbfgsHessianRank 50 --lbfgsL2Regularization 0.00001 --maxThreads 16 $@ > $LOG

./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$MODEL --testFilename=$VALIDATION_DATA > $VALIDATION
./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$MODEL --testFilename=$TRAINING_DATA > $TRAIN

#echo "Printing top parameters:"
#./scripts/run.sh com.jayantkrish.jklol.cli.PrintParameters --model=$OUTPUT --numFeatures 10

