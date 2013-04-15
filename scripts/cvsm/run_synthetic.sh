#!/bin/bash

DATA=data/cvsm/synthetic/logic/rnn.txt

./scripts/run.sh com.jayantkrish.jklol.cvsm.TrainCvsm --training $DATA --output out.ser --iterations 10000 --batchSize 1 --l2Regularization 0.001 --brief
./scripts/run.sh com.jayantkrish.jklol.cvsm.TestCvsm --testFilename $DATA --model out.ser 