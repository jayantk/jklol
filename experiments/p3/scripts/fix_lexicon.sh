#!/bin/bash -e

LEXICON=experiments/p3/scene/scene/lexicon.filtered.txt
OUT=experiments/p3/scene/data/lexicon.p3.txt

./scripts/run.sh com.jayantkrish.jklol.experiments.p3.FixLexicon --lexicon $LEXICON --noPrintOptions > $OUT

