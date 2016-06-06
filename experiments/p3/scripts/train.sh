#!/bin/bash -e

source experiments/p3/scripts/config.sh

PROC=()
for (( i=0; i<${NUM_FOLDS}; i++ ));
do
    TRAIN=${TRAIN_FILES[$i]}
    TEST=${TEST_FILES[$i]}
    NAME=${FOLD_NAMES[$i]}

    
    echo "Training parser $NAME ..."
    echo "  training data: $TRAIN"
    echo "  test data: $TEST"

    MODEL_DIR="$EXPERIMENT_DIR/$NAME"
    mkdir -p $MODEL_DIR
    
    PARSER="$MODEL_DIR/$PARSER_FILENAME"
    KB_MODEL="$MODEL_DIR/$KBMODEL_FILENAME"
    LOG="$MODEL_DIR/train_log.txt"

    # --worldFilename $WORLD_FILE_PATH
    CMD="./scripts/run.sh com.jayantkrish.jklol.experiments.p3.TrainP3 --lexicon $LEXICON --trainingData $TRAIN --exampleFilename $TRAINING_FILE_PATH --categoryFilename $CATEGORY_FILE_PATH --relationFilename $RELATION_FILE_PATH --defs $DEFS,$GENDEFS --categories $CATEGORIES --categoryFeatures $CATEGORY_FEATURE_NAMES --relations $RELATIONS --relationFeatures $RELATION_FEATURE_NAMES --parserOut $PARSER --kbModelOut $KB_MODEL --batchSize 1 --iterations 10 --clipGradients 1.0"

    echo $CMD
    $CMD > $LOG &
    pid=$!
    echo $pid
    PROC[$i]=$pid
done

for pid in ${PROC[@]};
do
    echo "waiting $pid ..."
    wait $pid
done
