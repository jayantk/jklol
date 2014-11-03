#!/bin/bash -e

#L2_ARRAY=(0.000000001 0.00000001 0.0000001 0.000001 0.000005 0.00001 0.00005 0.0001 0.0005);
#L2_ARRAY=(0.000000001 0.00000001 0.0000001 0.000001 0.000005 0.00001 0.00005 0.0001 0.0005, 0.001 0.005 0.01);
L2_ARRAY=(0.0005 0.001 0.005 0.01);
RUN_BASENAMES=("var_vec_l2-")
FLAGS=(" ")

#L2_ARRAY=(0.0001);
#RUN_BASENAMES=("var_vec_l2-" "fixed_vec_l2-")
#FLAGS=(" " "--fixInitializedVectors")

# L2_ARRAY=(0.00001);
# RUN_BASENAMES=("var_vec_l2-")
# FLAGS=(" ")

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
