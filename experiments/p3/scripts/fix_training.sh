#!/bin/bash -e

DATA_DIR="experiments/p3/scene"

TRAINING_FILE_PATH="training.annotated.txt"
TRAINING_OUT_PATH="training.txt"
WORLD_OUT_PATH="world.txt"

for i in $DATA_DIR/scene/0*; do

    echo $i
    
    cat $i/$TRAINING_FILE_PATH | grep '^\*' > $i/$WORLD_OUT_PATH
    cat $i/$TRAINING_FILE_PATH | grep -v '^\*' > $i/$TRAINING_OUT_PATH

done
