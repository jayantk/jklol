#!/bin/bash -e 

DATA=data/cvsm/synthetic/sizes/tensor.txt
VECTORS=data/cvsm/synthetic/sizes/size_vectors.txt

./scripts/run.sh com.jayantkrish.jklol.cvsm.TrainCvsm --training $DATA --output out.ser --iterations 10000 --batchSize 1 --l2Regularization 0.001 --brief --initialVectors $VECTORS
./scripts/run.sh com.jayantkrish.jklol.cvsm.TestCvsm  --model out.ser  --testFilename $DATA
