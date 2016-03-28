#!/bin/bash -e

DATA_DIR=experiments/geoquery/data/
OUT_DIR=experiments/geoquery/output/

ENTITY_LEXICON=$OUT_DIR/entity_lexicon.txt

mkdir -p $OUT_DIR

PROC=()
for i in `seq 0 9`;
do
    CMD="./scripts/run.sh com.jayantkrish.jklol.experiments.geoquery.GeoqueryTrainParser --trainingDataFolds $DATA_DIR --outputDir $OUT_DIR --parserIterations 50 --beamSize 100 --l2Regularization 0.0 --additionalLexicon $ENTITY_LEXICON --foldName fold$i.ccg --maxBatchesPerThread 1 --maxThreads 4"

    echo $CMD
    $CMD > $OUT_DIR/parser_log_fold$i.txt &
    pid=$!
    echo $pid
    PROC[$i]=$pid
done

for pid in ${PROC[@]};
do
    echo "waiting $pid ..."
    wait $pid
done

cat $OUT_DIR/training_error.fold*.json | jq '.correct' | awk '{SUM += $1; TOTAL += 1}; END {print "TRAINING RECALL"; print SUM / TOTAL}'
cat $OUT_DIR/training_error.fold*.json | jq '.correct_lf_possible' | awk '{SUM += $1; TOTAL += 1}; END {print "TRAINING LF RECALL"; print SUM / TOTAL}'
cat $OUT_DIR/test_error.fold*.json | jq '.correct' | awk '{SUM += $1; TOTAL += 1}; END {print "TEST RECALL"; print SUM / TOTAL}'
cat $OUT_DIR/test_error.fold*.json | jq '.correct_lf_possible' | awk '{SUM += $1; TOTAL += 1}; END {print "TEST LF RECALL"; print SUM / TOTAL}'
