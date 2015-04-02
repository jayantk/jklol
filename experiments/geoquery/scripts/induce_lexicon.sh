#!/bin/bash -e

TRAINING_DATA=experiments/geoquery/data/sample.ccg
TEST_DATA=experiments/geoquery/data/sample.ccg

RULES=experiments/geoquery/grammar/rules.txt

LEXICON=lexicon.txt
ALIGNMENT_MODEL=alignment_out.ser
MODEL=out.ser

# ./scripts/run.sh com.jayantkrish.jklol.ccg.cli.AlignmentLexiconInduction --trainingData $TRAINING_DATA --lexiconOutput $LEXICON --modelOutput $ALIGNMENT_MODEL --maxThreads 1 --emIterations 3 --smoothing 0.01 --useCfg

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.TrainSemanticParser --trainingData $TRAINING_DATA --lexicon $LEXICON --rules $RULES --skipWords  --batchSize 1 --iterations 20 --output $MODEL --logInterval 10 --beamSize 500 --returnAveragedParameters --alignmentModel $ALIGNMENT_MODEL

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.TestSemanticParser --testData $TEST_DATA --model $MODEL