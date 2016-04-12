#!/bin/bash -e

source experiments/p3/scripts/config.sh

num_folds=${#FOLD_NAMES[@]}
echo $num_folds
for (( i=0; i<${num_folds}; i++ ));
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

    ./scripts/run.sh com.jayantkrish.jklol.experiments.p3.TestP3 --testData $TEST --defs $DEFS,$GENDEFS --categoryFeatures $CATEGORY_FEATURE_NAMES --relationFeatures $RELATION_FEATURE_NAMES --parser $PARSER --kbModel $KB_MODEL > $TEST_ERR

    ./scripts/run.sh com.jayantkrish.jklol.experiments.p3.TestP3 --testData $TRAIN --defs $DEFS,$GENDEFS --categoryFeatures $CATEGORY_FEATURE_NAMES --relationFeatures $RELATION_FEATURE_NAMES --parser $PARSER --kbModel $KB_MODEL > $TRAIN_ERR
done
