#!/bin/bash -e

TRAINING_DATA=/data/penn_treebank3/LDC/LDC1/LDC99T42/TREEBANK_3/PARSED/MRG/WSJ/pos_00-18_1sent.txt
OUTPUT=out.ser

./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$OUTPUT --initialStepSize=1.0 --iterations 10000 --l2Regularization 0.01 $@

echo "Printing top parameters:"
./scripts/run.sh com.jayantkrish.jklol.cli.PrintParameters --model=$OUTPUT --numFeatures 10