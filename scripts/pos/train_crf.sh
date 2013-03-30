#!/bin/bash -e

TRAINING_DATA=/data/penn_treebank3/LDC/LDC1/LDC99T42/TREEBANK_3/PARSED/MRG/WSJ/pos_00-18_1000sent.txt
OUTPUT=out2.ser

./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$OUTPUT --initialStepSize=1.0 --iterations 50 --l2Regularization 0.00 --batchSize 10 $@

echo "Printing top parameters:"
./scripts/run.sh com.jayantkrish.jklol.cli.PrintParameters --model=$OUTPUT --numFeatures 10