#!/bin/bash

TRAINING_DATA=/data/penn_treebank3/LDC/LDC1/LDC99T42/TREEBANK_3/PARSED/MRG/WSJ/pos_00-18.txt
OUTPUT=out.ser

./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$OUTPUT --initialStepSize=0.1 --iterations 1 --l2Regularization 0.01 $@

echo "Printing top parameters:"
./scripts/run.sh com.jayantkrish.jklol.cli.PrintParameters --model=$OUTPUT --numFeatures 10