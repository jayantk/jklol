#!/bin/bash -e

TRAINING_DATA=~/data/ptb_pos/pos_00-18.txt
VALIDATION_DATA=~/data/ptb_pos/pos_19-21.txt
OUTPUT_DIR=pos_output/speed_test3/

ITERATIONS=10
BATCH_SIZE=1
L2_REG=0.00001

# Where the trained models go.
LR_LOG=$OUTPUT_DIR/logistic_regression_log.txt
LR_OUT=$OUTPUT_DIR/logistic_regression_out.ser
LR_TRAIN=$OUTPUT_DIR/logistic_regression_train.txt
LR_VALIDATION=$OUTPUT_DIR/logistic_regression_validation.txt

CRF_LOG=$OUTPUT_DIR/loglinear_crf_log.txt
CRF_OUT=$OUTPUT_DIR/loglinear_crf_out.ser
CRF_TRAIN=$OUTPUT_DIR/loglinear_crf_train.txt
CRF_VALIDATION=$OUTPUT_DIR/loglinear_crf_validation.txt

SVM_LOG=$OUTPUT_DIR/svm_log.txt
SVM_OUT=$OUTPUT_DIR/svm_out.ser
SVM_TRAIN=$OUTPUT_DIR/svm_train.txt
SVM_VALIDATION=$OUTPUT_DIR/svm_validation.txt

M3N_LOG=$OUTPUT_DIR/m3n_log.txt
M3N_OUT=$OUTPUT_DIR/m3n_out.ser
M3N_TRAIN=$OUTPUT_DIR/m3n_train.txt
M3N_VALIDATION=$OUTPUT_DIR/m3n_validation.txt

mkdir -p $OUTPUT_DIR

echo "Training logistic regression..."
#./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$LR_OUT --initialStepSize=1.0 --iterations $ITERATIONS --l2Regularization $L2_REG --batchSize $BATCH_SIZE --maxThreads 16 --noTransitions --logInterval 1000 > $LR_LOG

echo "Training CRF..."
#./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$CRF_OUT --initialStepSize=1.0 --iterations $ITERATIONS --l2Regularization $L2_REG --batchSize $BATCH_SIZE --maxThreads 16 --logInterval 1000 > $CRF_LOG

echo "Training SVM..."
#./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$SVM_OUT --initialStepSize=1.0 --iterations $ITERATIONS --l2Regularization $L2_REG --batchSize $BATCH_SIZE --maxThreads 16 --noTransitions --maxMargin --logInterval 1000 > $SVM_LOG

echo "Training M3N..."
./scripts/run.sh com.jayantkrish.jklol.pos.TrainPosCrf --training=$TRAINING_DATA --output=$M3N_OUT --initialStepSize=1.0 --iterations $ITERATIONS --l2Regularization $L2_REG --batchSize $BATCH_SIZE --maxThreads 16 --maxMargin --logInterval 1000 > $M3N_LOG

# validate each model.
echo "Testing..."
./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$LR_OUT  --testFilename=$VALIDATION_DATA > $LR_VALIDATION
./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$LR_OUT  --testFilename=$TRAINING_DATA > $LR_TRAIN

./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$CRF_OUT --testFilename=$VALIDATION_DATA > $CRF_VALIDATION
./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$CRF_OUT --testFilename=$TRAINING_DATA > $CRF_TRAIN

./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$SVM_OUT --testFilename=$VALIDATION_DATA > $SVM_VALIDATION
./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$SVM_OUT --testFilename=$TRAINING_DATA > $SVM_TRAIN

./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$M3N_OUT --testFilename=$VALIDATION_DATA > $M3N_VALIDATION
./scripts/run.sh com.jayantkrish.jklol.pos.TestPosCrf --model=$M3N_OUT --testFilename=$TRAINING_DATA > $M3N_TRAIN
