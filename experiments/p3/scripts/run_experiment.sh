#!/bin/bash -e

./experiments/p3/scripts/fix_lexicon.sh
./experiments/p3/scripts/train.sh
./experiments/p3/scripts/test.sh
./experiments/p3/scripts/eval.sh
