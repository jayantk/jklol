#!/bin/bash -e

DATA_DIR=experiments/geoquery/data/
OUT_DIR=experiments/geoquery/output/

ENTITY_LEXICON=$OUT_DIR/entity_lexicon.txt

FOLD_NUM=0

mkdir -p $OUT_DIR

./experiments/geoquery/scripts/convert_np_list.py experiments/geoquery/grammar/np-list.lex > $ENTITY_LEXICON

./scripts/run.sh com.jayantkrish.jklol.experiments.geoquery.GeoqueryInduceLexicon --trainingDataFolds $DATA_DIR --outputDir $OUT_DIR --emIterations 10 --smoothing 0.1 --parserIterations 10 --beamSize 100 --l2Regularization 0.0 --additionalLexicon $ENTITY_LEXICON --foldName fold$FOLD_NUM.ccg --unknownWordThreshold 0 --maxBatchesPerThread 1 --maxThreads 4 > $OUT_DIR/log_fold$FOLD_NUM.txt

cat $OUT_DIR/training_error.fold$FOLD_NUM.ccg.json | jq '.correct' | awk '{SUM += $1; TOTAL += 1}; END {print "Training Accuracy"; print SUM / TOTAL}'
cat $OUT_DIR/test_error.fold$FOLD_NUM.ccg.json | jq '.correct' | awk '{SUM += $1; TOTAL += 1}; END {print "Test Accuracy"; print SUM / TOTAL}'
