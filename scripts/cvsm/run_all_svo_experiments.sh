#!/bin/bash -e

BASE_DIR=~/data/cvsm_2013/eat/
TEMPLATE_PATH=$BASE_DIR/templates/*.txt

DATA_DIR=$BASE_DIR/generated/
TRAIN_SUFF=_train.txt
TEST_SUFF=_test.txt
VECTOR_SUFF=_vector.txt
JKLOL_RUN=./scripts/run.sh

RUN_ID=speed
OUT_DIR=$BASE_DIR/output/$RUN_ID/
LOGNAME=_log.txt
TRAINNAME=_train_err.txt
TESTNAME=_test_err.txt
MODELNAME=_out.ser

ITERATIONS=5

mkdir -p $OUT_DIR

for f in $TEMPLATE_PATH
do
    BASE=$(basename "$f")
    FILENAME="${BASE%.*}"
    TRAIN_IN=$DATA_DIR$FILENAME$TRAIN_SUFF
    TEST_IN=$DATA_DIR$FILENAME$TEST_SUFF
    VECTOR_IN=$DATA_DIR$FILENAME$VECTOR_SUFF

    LOG_OUT=$OUT_DIR/$FILENAME$LOGNAME
    TRAIN_ERR_OUT=$OUT_DIR/$FILENAME$TRAINNAME
    TEST_ERR_OUT=$OUT_DIR/$FILENAME$TESTNAME
    MODEL_OUT=$OUT_DIR/$FILENAME$MODELNAME

    echo "Running $FILENAME..."
    $JKLOL_RUN com.jayantkrish.jklol.cvsm.TrainCvsm --training $TRAIN_IN --output $MODEL_OUT --iterations $ITERATIONS --batchSize 1 --l2Regularization 0.01 --initialVectors $VECTOR_IN > $LOG_OUT
    $JKLOL_RUN com.jayantkrish.jklol.cvsm.TestCvsm  --model $MODEL_OUT  --testFilename $TRAIN_IN > $TRAIN_ERR_OUT
    $JKLOL_RUN com.jayantkrish.jklol.cvsm.TestCvsm  --model $MODEL_OUT  --testFilename $TEST_IN > $TEST_ERR_OUT

done