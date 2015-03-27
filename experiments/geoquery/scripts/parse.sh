#!/bin/bash -e

MODEL=out.ser

./scripts/run.sh com.jayantkrish.jklol.ccg.cli.ParseCcg --model $MODEL --numParses 1 --printLf $@ 


