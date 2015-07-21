#!/usr/bin/python

'''
Naive lexicon generation algorithm based on the entity and predicate
names in the database.
'''

import re

entity_types='experiments/geoquery/uwspf_data/entity_types.txt'
predicate_types='experiments/geoquery/uwspf_data/predicate_types.txt'

entity_format = '"%(name)s","N:e{0}","%(entity)s","0 entity:%(type)s"'

predicate_patterns = [('<[a-z]*,t>', '"%(name)s","N:s{0}","(lambda e (%(predicate)s e))","0 %(predicate)s"'),
                      ('<[a-z]*,[^t]>', '"%(name)s","(N:e{1}/N:e{1}){0}","(lambda e (%(predicate)s e))","0 %(predicate)s","%(predicate)s 1 1"'),
                      ('<[a-z]*,<[a-z]*,t>>', '"%(name)s","((N:s{1}\N:s{1})/N:e{2}){0}","(lambda x f e (and:<t*,t> (f e) (%(predicate)s e x)))","0 %(predicate)s","%(predicate)s 1 1","%(predicate)s 2 2"')]

predicate_synonyms = {'city' : ['cities'],
                      'state' : ['states'],
                      'next_to' : ['border', 'borders', 'bordering']}

with open(entity_types, 'r') as f:
    for line in f:
        parts = line.split(":")
        entity = parts[0].strip()
        type = parts[1].strip()

        if type == "n":
            continue

        # Determine the entity's name based on its type.
        entity_names = [entity.replace("_", " ")]
        if type == "c":
            # cities have states appended, and rivers have
            # "river" appended
            parts = entity.split("_")
            entity_names = [" ".join(parts[:-1])]
        
        entity = line.strip()
        for entity_name in entity_names:    
            print entity_format % {'name' : entity_name, 'entity' : entity, 'type' : type}

'''
with open(predicate_types, 'r') as f:
    for line in f:
        parts = line.split(":")
        predicate = parts[0].strip()
        type = parts[1].strip()
        name = predicate.replace("_", " ")

        names = [name]
        if predicate_synonyms.has_key(predicate):
            names.extend(predicate_synonyms[predicate])

        predicate = line.strip()
        for pattern in predicate_patterns:
            if re.match(pattern[0], type):
                for name in names:
                    print pattern[1] % {'name' : name, 'predicate' : predicate}

'''
