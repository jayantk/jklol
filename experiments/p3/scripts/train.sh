#!/bin/bash -e

LEXICON=experiments/p3/scene/data/lexicon.p3.txt
DEFS=experiments/p3/scene/data/defs.lisp
GENDEFS=experiments/p3/scene/data/gendefs.lisp
TRAINING_DATA=experiments/p3/scene/scene/000000

CATEGORY_FEATURE_NAMES=experiments/p3/scene/data/category_feature_names.txt
RELATION_FEATURE_NAMES=experiments/p3/scene/data/relation_feature_names.txt

PARSER_OUT=experiments/p3/scene/data/parser.ser
KB_MODEL_OUT=experiments/p3/scene/data/kbmodel.ser

./scripts/run.sh com.jayantkrish.jklol.experiments.p3.TrainP3 --lexicon $LEXICON --trainingData $TRAINING_DATA --defs $DEFS --gendefs $GENDEFS --categoryFeatures $CATEGORY_FEATURE_NAMES --relationFeatures $RELATION_FEATURE_NAMES --parserOut $PARSER_OUT --kbModelOut $KB_MODEL_OUT --batchSize 1 --iterations 1 

