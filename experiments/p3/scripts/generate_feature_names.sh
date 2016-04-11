#!/bin/bash -e

CATEGORY_PATTERN=experiments/p3/scene/scene/00**/osm_kb.domain.entities
RELATION_PATTERN=experiments/p3/scene/scene/00**/osm_kb.domain.relations

CATEGORY_FEATURE_NAMES=experiments/p3/scene/data/category_feature_names.txt
RELATION_FEATURE_NAMES=experiments/p3/scene/data/relation_feature_names.txt

cat $CATEGORY_PATTERN | cut -f3 -d, | sort -g | uniq > $CATEGORY_FEATURE_NAMES
cat $RELATION_PATTERN | cut -f4 -d, | sort -g | uniq > $RELATION_FEATURE_NAMES
