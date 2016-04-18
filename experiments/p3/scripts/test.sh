#!/bin/bash -e

source experiments/p3/scripts/config.sh

PROC=()
for (( i=0; i<${NUM_FOLDS}; i++ ));
do
    TRAIN=${TRAIN_FILES[$i]}
    TEST=${TEST_FILES[$i]}
    NAME=${FOLD_NAMES[$i]}

    MODEL_DIR="$EXPERIMENT_DIR/$NAME"
    PARSER="$MODEL_DIR/$PARSER_FILENAME"
    KB_MODEL="$MODEL_DIR/$KBMODEL_FILENAME"
    TRAIN_ERR="$MODEL_DIR/train_err.txt"
    TEST_ERR="$MODEL_DIR/test_err.txt"

    echo "Testing $NAME..."

    CMD="./scripts/run.sh com.jayantkrish.jklol.experiments.p3.TestP3 --testData $TEST --exampleFilename $TRAINING_FILE_PATH --categoryFilename $CATEGORY_FILE_PATH --relationFilename $RELATION_FILE_PATH --defs $DEFS,$GENDEFS --categories $CATEGORIES --categoryFeatures $CATEGORY_FEATURE_NAMES --relations $RELATIONS --relationFeatures $RELATION_FEATURE_NAMES --parser $PARSER --kbModel $KB_MODEL"
    $CMD > $TEST_ERR &
    pid=$!
    PROC+=($pid)

    CMD="./scripts/run.sh com.jayantkrish.jklol.experiments.p3.TestP3 --testData $TRAIN --exampleFilename $TRAINING_FILE_PATH --categoryFilename $CATEGORY_FILE_PATH --relationFilename $RELATION_FILE_PATH --defs $DEFS,$GENDEFS --categories $CATEGORIES --categoryFeatures $CATEGORY_FEATURE_NAMES --relations $RELATIONS --relationFeatures $RELATION_FEATURE_NAMES --parser $PARSER --kbModel $KB_MODEL"
    $CMD > $TRAIN_ERR &
    pid=$!
    PROC+=($pid)
done

for pid in ${PROC[@]};
do
    echo "waiting $pid ..."
    wait $pid
done


grep 'Recall:' $EXPERIMENT_DIR/**/train_err.txt | grep -o '([^)]*)' | sed 's/[(\/)]//g' | awk '{SUM += $1; TOT += $2} END {print "Training recall: " (SUM / TOT) " (" SUM " / " TOT ")"}'
grep 'Recall:' $EXPERIMENT_DIR/**/test_err.txt | grep -o '([^)]*)' | sed 's/[(\/)]//g' | awk '{SUM += $1; TOT += $2} END {print "Test recall: " (SUM / TOT) " (" SUM " / " TOT ")"}'
