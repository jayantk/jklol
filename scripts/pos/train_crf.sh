#!/bin/bash -e

TRAINING_DATA=~/data/ptb_pos/pos_00-18.txt
OUTPUT=out17.ser

./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$OUTPUT --initialStepSize=0.1 --iterations 10 --l2Regularization 0.00001 --maxThreads 16 --batchSize 100 --maxMargin $@

echo "Printing top parameters:"
./scripts/run.sh com.jayantkrish.jklol.cli.PrintParameters --model=$OUTPUT --numFeatures 10