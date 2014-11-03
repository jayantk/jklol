#!/bin/bash -e

TRAIN_DATA=~/data/cvsm_2013/eat/tensor_train.txt
TEST_DATA=~/data/cvsm_2013/eat/tensor_test.txt
VECTORS=~/data/cvsm_2013/eat/initial_vectors.txt
OUT_DIR=~/data/cvsm_2013/output/eat/
RUN_ID=_all3
LOGNAME=log
TRAINNAME=train
TESTNAME=test
MODELNAME=out
TXT=.txt
SER=.ser
LOG=$OUT_DIR$LOGNAME$RUN_ID$TXT
TRAIN=$OUT_DIR$TRAINNAME$RUN_ID$TXT
TEST=$OUT_DIR$TESTNAME$RUN_ID$TXT
MODEL=$OUT_DIR$MODELNAME$RUN_ID$SER

./scripts/run.sh com.jayantkrish.jklol.cvsm.TrainCvsm --training $TRAIN_DATA --output $MODEL --iterations 20 --batchSize 1 --l2Regularization 0.01 --initialVectors $VECTORS > $LOG
./scripts/run.sh com.jayantkrish.jklol.cvsm.TestCvsm  --model $MODEL  --testFilename $TRAIN_DATA > $TRAIN
./scripts/run.sh com.jayantkrish.jklol.cvsm.TestCvsm  --model $MODEL  --testFilename $TEST_DATA > $TEST