
export DATA_DIR="experiments/p3/scene"
export OUT_DIR="$DATA_DIR/output/"
export EXPERIMENT_NAME="080316_6"
export EXPERIMENT_DIR="$OUT_DIR/$EXPERIMENT_NAME/"

mkdir -p $OUT_DIR
mkdir -p $EXPERIMENT_DIR

# Common data files for all models
export CATEGORY_FILE_PATH="osm_kb.domain.entities"
export RELATION_FILE_PATH="osm_kb.domain.relations"
export TRAINING_FILE_PATH="training.txt"
export CATEGORY_FEATURE_NAMES="$DATA_DIR/data/category_feature_names.txt"
export RELATION_FEATURE_NAMES="$DATA_DIR/data/relation_feature_names.txt"
export WORLD_FILE_PATH="world.txt"
export DEFS="$DATA_DIR/data/defs.lisp"

# Configuration for lexicon preprocessing and various
# files generated from the lexicon that are used for
# training.
export INITIAL_LEXICON="$DATA_DIR/scene/lexicon.filtered.txt"
export LEXICON="$EXPERIMENT_DIR/lexicon.txt"
export CATEGORIES="$EXPERIMENT_DIR/categories.txt"
export RELATIONS="$EXPERIMENT_DIR/relations.txt"
export GENDEFS="$EXPERIMENT_DIR/gendefs.lisp"


# Training file configuration
function join { local IFS="$1"; shift; echo "$*"; }

TRAIN_FILES=()
TEST_FILES=()
FOLD_NAMES=()
for i in $DATA_DIR/scene/0*; do
    name=$(basename $i)
    TRAIN_FILES_ARR=()
    for j in $DATA_DIR/scene/0*; do
	if [ $j != $i ]
	then
	   TRAIN_FILES_ARR+=($j)
	fi
    done

    TRAIN_FILES_STR=$(join , ${TRAIN_FILES_ARR[@]})

    TRAIN_FILES+=($TRAIN_FILES_STR)
    TEST_FILES+=($i)
    FOLD_NAMES+=($name)
done

# NUM_FOLDS=${#FOLD_NAMES[@]}
NUM_FOLDS=1

export PARSER_FILENAME="parser.ser"
export KBMODEL_FILENAME="kbmodel.ser"
