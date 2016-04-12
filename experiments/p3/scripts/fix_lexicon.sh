#!/bin/bash -e

./scripts/run.sh com.jayantkrish.jklol.experiments.p3.FixLexicon --lexicon $INITIAL_LEXICON --noPrintOptions > $LEXICON

cat $LEXICON | grep -o '[^ "(]*:<e,t>' | sort | uniq > $CATEGORIES
cat $LEXICON | grep -o '[^ "(]*:<e,<e,t>>' | sort | uniq > $RELATIONS

cat $CATEGORIES | sed 's/\(.*\)/(define \1 (make-category \"\1\"))/' > $GENDEFS
cat $RELATIONS | sed 's/\(.*\)/(define \1 (make-relation \"\1\"))/' >> $GENDEFS

