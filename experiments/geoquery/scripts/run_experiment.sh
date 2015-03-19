#!/bin/bash -e

# Generate the CCG lexicon
./experiments/geoquery/scripts/generate_lexicon.py > experiments/geoquery/scripts/generated_lexicon.txt

# Train the model. The trained parser is serialized to "out.ser"
# in the root directory
./experiments/geoquery/scripts/train.sh

# Test the model
./experiments/geoquery/scripts/test.sh
