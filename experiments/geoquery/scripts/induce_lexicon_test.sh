
DATA_DIR=experiments/geoquery/data/
OUT_DIR=experiments/geoquery/output/

ENTITY_LEXICON=$OUT_DIR/entity_lexicon.txt

mkdir -p $OUT_DIR

# ./experiments/geoquery/scripts/convert_np_list.py experiments/geoquery/grammar/np-list.lex > $ENTITY_LEXICON

# ./scripts/run.sh com.jayantkrish.jklol.experiments.geoquery.LexiconInductionCrossValidation --trainingDataFolds $DATA_DIR --outputDir $OUT_DIR --emIterations 20 --smoothing 0.1 --parserIterations 50 --beamSize 100 --l2Regularization 0.0 --additionalLexicon $ENTITY_LEXICON --unknownWordThreshold 0 --maxBatchesPerThread 1 --maxThreads 4 --test --foldName test.ccg > $OUT_DIR/log.txt

./scripts/run.sh com.jayantkrish.jklol.experiments.geoquery.TrainGeoqueryParser --trainingData $DATA_DIR/all_folds.ccg --testData $DATA_DIR/test.ccg --outputDir $OUT_DIR --foldName test.ccg --lexicon $OUT_DIR/lexicon.test.ccg.txt --npLexicon $ENTITY_LEXICON --parserIterations 50 --beamSize 100 --l2Regularization 0.01 --maxBatchesPerThread 1 --maxThreads 4  > $OUT_DIR/train_log.txt

# ./scripts/run.sh com.jayantkrish.jklol.experiments.geoquery.LexiconInductionCrossValidation --trainingDataFolds $DATA_DIR --outputDir $OUT_DIR --emIterations 20 --smoothing 0.1 --parserIterations 50 --beamSize 100 --l2Regularization 0.0 --additionalLexicon $ENTITY_LEXICON --unknownWordThreshold 0 --maxBatchesPerThread 1 --maxThreads 4 --foldName fold0.ccg > $OUT_DIR/log.txt
