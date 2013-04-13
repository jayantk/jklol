#!/bin/bash

DATA=data/cvsm/synthetic/logic/tensor.txt
INITIAL_VECTORS=data/cvsm/synthetic/logic_vectors.txt

./scripts/run.sh com.jayantkrish.jklol.cvsm.TrainCvsm --training $DATA --output out.ser --iterations 10000 --batchSize 1 --l2Regularization 0.001 --brief --initialVectors=$INITIAL_VECTORS
./scripts/run.sh com.jayantkrish.jklol.cvsm.TestCvsm  --model out.ser  --testFilename $DATA