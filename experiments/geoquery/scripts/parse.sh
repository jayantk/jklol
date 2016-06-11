#!/bin/bash -e

MODEL=experiments/geoquery/output/parser.fold0.ccg.ser

./scripts/run.sh com.jayantkrish.jklol.experiments.geoquery.GeoqueryRunParser --noPrintOptions --model $MODEL $@

