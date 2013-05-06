#!/bin/bash -e

L2_ARRAY=(0.1 0.01 0.001 0.0001 0.00001 0.000001);
RUN_BASENAMES=("var_vec_l2-" "fixed_vec_l2-")
FLAGS=(" " "--fixInitializedVectors")

for i in "${!FLAGS[@]}";
do
    RUN_BASENAME=${RUN_BASENAMES[$i]}
    # echo $RUN_BASENAME

    for L2 in ${L2_ARRAY[@]}
    do
	# echo "l2=$L2"
	RUN_NAME=$RUN_BASENAME$L2
	CMD="./scripts/cvsm/run_all_svo_experiments.sh $RUN_NAME $L2 ${FLAGS[$i]}"
	echo $CMD
	$CMD
    done
done
