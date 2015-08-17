#!/bin/bash -e

DATA_DIR=experiments/geoquery/data/
OUT_DIR=experiments/geoquery/output/

ENTITY_LEXICON=$OUT_DIR/entity_lexicon.txt

mkdir -p $OUT_DIR

./experiments/geoquery/scripts/generate_lexicon.py > $ENTITY_LEXICON

./scripts/run.sh com.jayantkrish.jklol.experiments.geoquery.LexiconInductionCrossValidation --trainingDataFolds $DATA_DIR --outputDir $OUT_DIR --emIterations 10 --smoothing 0.01 --parserIterations 100 --beamSize 100 --l2Regularization 0.01 --additionalLexicon $ENTITY_LEXICON --unknownWordThreshold 0

