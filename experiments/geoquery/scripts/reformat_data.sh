#!/bin/bash -e

IN_DIR=experiments/geoquery/uwspf_data/
OUT_DIR=experiments/geoquery/data/

for i in `ls $IN_DIR/*.ccg`
do
    filename="${i##*/}"
    out_file=$OUT_DIR/$filename
    cat $i | sed 's/\($[0-9]\):[a-z]*/\1/g' > $out_file
done
