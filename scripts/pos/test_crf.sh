#!/bin/bash

MODEL=out15.ser
TEST_DATA=~/data/ptb_pos/pos_19-21.txt

./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$MODEL --testFilename=$TEST_DATA $@