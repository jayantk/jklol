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

RUN_ID=$1
OUT_DIR=$BASE_DIR/output/svo7/no_subexpressions_100dim_tensor_regdelta_rand2/$RUN_ID/
OUT_TEMP_DIR=$BASE_DIR/output/svo7/no_subexpressions_100dim_tensor_regdelta_rand2/$RUN_ID/temp
LOGNAME=_log.txt
TRAINNAME=_train_err.txt
TRAIN_SEM=_train_sem.txt
VALIDATIONNAME=_validation_err.txt
VALIDATION_SEM=_validation_sem.txt
TESTNAME=_test_err.txt
TEST_SEM=_test_sem.txt
MODELNAME=_out.ser

SEMEVAL_SCORER=~/data/cvsm_2013/semeval_task8/SemEval2010_task8_all_data/SemEval2010_task8_scorer-v1.2/semeval2010_task8_scorer-v1.2.pl

ITERATIONS=3000
L2REG=$2

mkdir -p $OUT_DIR
mkdir -p $OUT_TEMP_DIR

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
    TRAIN_SEM_OUT=$OUT_DIR/$FILENAME$TRAIN_SEM
    VALIDATION_ERR_OUT=$OUT_DIR/$FILENAME$VALIDATIONNAME
    VALIDATION_SEM_OUT=$OUT_DIR/$FILENAME$VALIDATION_SEM
    TEST_ERR_OUT=$OUT_DIR/$FILENAME$TESTNAME
    TEST_SEM_OUT=$OUT_DIR/$FILENAME$TEST_SEM
    MODEL_OUT=$OUT_DIR/$FILENAME$MODELNAME

    echo "Running $FILENAME..."
    ### $JKLOL_RUN com.jayantkrish.jklol.cvsm.TrainCvsm --training $TRAIN_IN --output $MODEL_OUT --batchSize 1 --iterations $ITERATIONS --l2Regularization $L2REG --initialVectors $VECTOR_IN --regularizationFrequency 0.1 --initialStepSize 0.1 $@ > $LOG_OUT
    $JKLOL_RUN com.jayantkrish.jklol.cvsm.TrainCvsm --training $TRAIN_IN --output $MODEL_OUT --initializeTensorsToIdentity --lbfgsMinibatchSize 1000 --lbfgsMinibatchIterations 20 --lbfgsIterations $ITERATIONS --lbfgs --lbfgsL2Regularization $L2REG --initialVectors $VECTOR_IN --regularizeDeltas $@ > $LOG_OUT
    # --regularizeVectorDeltas

    $JKLOL_RUN com.jayantkrish.jklol.cvsm.TestCvsm  --model $MODEL_OUT --relationDictionary $REL_DICT --testFilename $TRAIN_IN > $TRAIN_ERR_OUT
    $JKLOL_RUN com.jayantkrish.jklol.cvsm.TestCvsm  --model $MODEL_OUT --relationDictionary $REL_DICT --testFilename $VALIDATION_IN > $VALIDATION_ERR_OUT
    $JKLOL_RUN com.jayantkrish.jklol.cvsm.TestCvsm  --model $MODEL_OUT --relationDictionary $REL_DICT --testFilename $TEST_IN > $TEST_ERR_OUT

    # Run official SemEval scorer.
    TEMP_TRUE=$OUT_TEMP_DIR/true.txt
    TEMP_PREDICTED=$OUT_TEMP_DIR/predicted.txt
    grep '^[01]' $TRAIN_ERR_OUT | cut -f2 -d' ' | awk '{i += 1; print i"\t"$1}' > $TEMP_TRUE
    grep '^[01]' $TRAIN_ERR_OUT | cut -f4 -d' ' | awk '{i += 1; print i"\t"$1}' > $TEMP_PREDICTED
    $SEMEVAL_SCORER $TEMP_PREDICTED $TEMP_TRUE > $TRAIN_SEM_OUT

    grep '^[01]' $VALIDATION_ERR_OUT | cut -f2 -d' ' | awk '{i += 1; print i"\t"$1}' > $TEMP_TRUE
    grep '^[01]' $VALIDATION_ERR_OUT | cut -f4 -d' ' | awk '{i += 1; print i"\t"$1}' > $TEMP_PREDICTED
    $SEMEVAL_SCORER $TEMP_PREDICTED $TEMP_TRUE > $VALIDATION_SEM_OUT

    grep '^[01]' $TEST_ERR_OUT | cut -f2 -d' ' | awk '{i += 1; print i"\t"$1}' > $TEMP_TRUE
    grep '^[01]' $TEST_ERR_OUT | cut -f4 -d' ' | awk '{i += 1; print i"\t"$1}' > $TEMP_PREDICTED
    $SEMEVAL_SCORER $TEMP_PREDICTED $TEMP_TRUE > $TEST_SEM_OUT
done