#!/bin/bash -e

BASE_DIR=~/data/cvsm_2013/semeval_task8/
REL_DICT=$BASE_DIR/rel_dict.txt
TEMPLATE_PATH=$BASE_DIR/templates/*.txt

DATA_DIR=$BASE_DIR/generated/
TRAIN_SUFF=_train.txt
VALIDATION_SUFF=_validation.txt
TEST_SUFF=_test.txt
VECTOR_SUFF=_vector.txt
JKLOL_RUN=./scripts/run.sh

RUN_ID=temp7_l2-0.0001
OUT_DIR=$BASE_DIR/output/$RUN_ID/
LOGNAME=_log.txt
TRAINNAME=_train_err.txt
VALIDATIONNAME=_validation_err.txt
TESTNAME=_test_err.txt
MODELNAME=_out.ser

ITERATIONS=100
L2REG=0.0001

mkdir -p $OUT_DIR

for f in $TEMPLATE_PATH
do
    BASE=$(basename "$f")
    FILENAME="${BASE%.*}"
    TRAIN_IN=$DATA_DIR$FILENAME$TRAIN_SUFF
    VALIDATION_IN=$DATA_DIR$FILENAME$VALIDATION_SUFF
    TEST_IN=$DATA_DIR$FILENAME$TEST_SUFF
    VECTOR_IN=$DATA_DIR$FILENAME$VECTOR_SUFF

    LOG_OUT=$OUT_DIR/$FILENAME$LOGNAME
    TRAIN_ERR_OUT=$OUT_DIR/$FILENAME$TRAINNAME
    VALIDATION_ERR_OUT=$OUT_DIR/$FILENAME$VALIDATIONNAME
    TEST_ERR_OUT=$OUT_DIR/$FILENAME$TESTNAME
    MODEL_OUT=$OUT_DIR/$FILENAME$MODELNAME

    echo "Running $FILENAME..."
    $JKLOL_RUN com.jayantkrish.jklol.cvsm.TrainCvsm --training $TRAIN_IN --output $MODEL_OUT --iterations $ITERATIONS --batchSize 1 --l2Regularization $L2REG --initialVectors $VECTOR_IN --regularizationFrequency 0.1 > $LOG_OUT
    $JKLOL_RUN com.jayantkrish.jklol.cvsm.TestCvsm  --model $MODEL_OUT --relationDictionary $REL_DICT --testFilename $TRAIN_IN > $TRAIN_ERR_OUT
    $JKLOL_RUN com.jayantkrish.jklol.cvsm.TestCvsm  --model $MODEL_OUT --relationDictionary $REL_DICT --testFilename $VALIDATION_IN > $VALIDATION_ERR_OUT
    # $JKLOL_RUN com.jayantkrish.jklol.cvsm.TestCvsm  --model $MODEL_OUT  --testFilename $TEST_IN > $TEST_ERR_OUT
done