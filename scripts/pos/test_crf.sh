#!/bin/bash

MODEL=out2.ser
TEST_DATA=/data/penn_treebank3/LDC/LDC1/LDC99T42/TREEBANK_3/PARSED/MRG/WSJ/pos_00-18_10sent.txt

./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$MODEL --testFilename=$TEST_DATA $@
