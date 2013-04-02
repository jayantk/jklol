#!/bin/bash -e

TRAINING_DATA=~/data/ptb_pos/pos_00-18.txt
OUTPUT=out4.ser

./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$OUTPUT --initialStepSize=1.0 --iterations 10 --l2Regularization 0.0001 --batchSize 100 --maxThreads 16 --maxMargin --noTransitions $@

echo "Printing top parameters:"
./scripts/run.sh com.jayantkrish.jklol.cli.PrintParameters --model=$OUTPUT --numFeatures 10

