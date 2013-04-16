#!/bin/bash -e

TRAIN_DATA=~/data/cvsm_2013/eat/tensor_train.txt
TEST_DATA=~/data/cvsm_2013/eat/tensor_test.txt
VECTORS=~/data/cvsm_2013/eat/initial_vectors.txt
OUT_DIR=~/data/cvsm_2013/output/eat/
RUN_ID=_all1
LOGNAME=log
TRAINNAME=train
TESTNAME=test
TXT=.txt
LOG=$OUT_DIR$LOGNAME$RUN_ID$TXT
TRAIN=$OUT_DIR$TRAINNAME$RUN_ID$TXT
TEST=$OUT_DIR$TESTNAME$RUN_ID$TXT

./scripts/run.sh com.jayantkrish.jklol.cvsm.TrainCvsm --training $TRAIN_DATA --output out.ser --iterations 1000 --batchSize 1 --l2Regularization 0.001 > $LOG
./scripts/run.sh com.jayantkrish.jklol.cvsm.TestCvsm  --model out.ser  --testFilename $TRAIN_DATA > $TRAIN
./scripts/run.sh com.jayantkrish.jklol.cvsm.TestCvsm  --model out.ser  --testFilename $TEST_DATA > $TEST