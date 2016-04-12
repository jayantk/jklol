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
    mkdir -p $MODEL_DIR
    
    PARSER="$MODEL_DIR/$PARSER_FILENAME"
    KB_MODEL="$MODEL_DIR/$KBMODEL_FILENAME"
    LOG="$MODEL_DIR/train_log.txt"

    echo "Training parser: $NAME"
    echo "  train: $TRAIN"

    ./scripts/run.sh com.jayantkrish.jklol.experiments.p3.TrainP3 --lexicon $LEXICON --trainingData $TRAIN --defs $DEFS,$GENDEFS --categories $CATEGORIES --categoryFeatures $CATEGORY_FEATURE_NAMES --relations $RELATIONS --relationFeatures $RELATION_FEATURE_NAMES --parserOut $PARSER --kbModelOut $KB_MODEL --batchSize 1 --iterations 1 > $LOG
done
