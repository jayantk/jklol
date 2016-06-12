#!/bin/bash -e

MODEL=experiments/geoquery/output/parser.fold0.ccg.ser
ENV=experiments/geoquery/eval/environment.lisp
GEOBASE=experiments/geoquery/eval/geobase.lisp

./scripts/run.sh com.jayantkrish.jklol.experiments.geoquery.GeoqueryRunParser --noPrintOptions --environment $ENV,$GEOBASE --model $MODEL $@

