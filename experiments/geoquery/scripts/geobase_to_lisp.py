#!/usr/bin/python

import sys

geobase_file = sys.argv[1]
lisp_file = sys.argv[2]

# Category predicates
categories = [
    ('state:<s,t>', 's', 'state', 0),
    ('city:<c,t>', 'c', 'city', 2),
    ('capital:<c,t>', 'c', 'state', 2),
    ('lake:<l,t>', 'l', 'lake', 0),
    ('river:<r,t>', 'r', 'river', 0),
    ('mountain:<m,t>', 'm', 'mountain', 2),
]

# Relations
relations = [
    ('loc:<lo,<lo,t>>', 'city', (2, 1), (0, 1), False),
    ('loc:<lo,<lo,t>>', 'river', (0, 1), (2, 1000), False),
    ('loc:<lo,<lo,t>>', 'mountain', (2, 1), (0, 1), False),
    ('loc:<lo,<lo,t>>', 'lake', (0, 1), (2, 1000), False),
    ('next_to:<lo,<lo,t>>', 'border', (0, 1), (2, 1000), False),
    ('capital2:<s,<c,t>>', 'state', (0, 1), (2, 1), False),
    ('population:<lo,<i,t>>', 'state', (0, 1), (3, 1), False),
    ('capital:<s,c>', 'state', (0, 1), (2, 1), True),

    ('area:<lo,i>', 'state', (0,1), (4,1), True),
    ('area:<lo,i>', 'lake', (0,1), (2,1), True),
    ('population:<lo,i>', 'state', (0,1), (3,1), True),
    ('population:<lo,i>', 'city', (0,1), (3,1), True),
    ('elevation:<lo,i>', 'mountain', (2,1), (3,1), True),
    ('elevation:<lo,<i,t>>', 'mountain', (2,1), (3,1), False),
    ('len:<r,i>', 'river', (0,1), (1,1), True),
]

def quoted_list(l):
    return " ".join([quote_not_number(x) for x in l])

def quoted_relation_list(l):
    return " ".join(['(cons ' + quote_not_number(x[0]) + ' ' + quote_not_number(x[1]) + ' )' for x in l])

def entity_to_id(x, type):
    return x.replace(" ", "_") + ":" + type

def is_number(x):
    try:
        float(x)
        return True
    except ValueError:
        return False

def quote_not_number(x):
    if not is_number(x):
        return '"' + x + '"'
    else:
        return x

category_values = {}
for cat in categories:
    category_values[cat[0]] = []

relation_values = {}
for rel in relations:
    relation_values[rel[0]] = []

with open(geobase_file, 'r') as f:
    for line in f:
        parts = line.strip().split('(')
        pred = parts[0]
        args = parts[1]

        arglist = [x.strip("'.)][") for x in args.split(",")]

        # print >> sys.stderr, pred, arglist

        for category in categories:
            if pred == category[2]:
                category_values[category[0]].append(arglist[category[3]])

        for relation in relations:
            if pred == relation[1]:
                arg1_start = relation[2][0]
                arg1_vals = min(relation[2][1], len(arglist) - relation[2][0])
                arg2_start = relation[3][0]
                arg2_vals = min(relation[3][1], len(arglist) - relation[3][0])
                for i in xrange(arg1_vals):
                    for j in xrange(arg2_vals):
                        arg1 = arglist[arg1_start + i]
                        arg2 = arglist[arg2_start + j]

                        relation_values[relation[0]].append((arg1, arg2))

        '''
        if pred == 'state':
            states.append(arglist[0])
            capitals.append(arglist[2])
        elif pred == 'city':
            cities.append(arglist[2])
            loc.append((arglist[2], arglist[0]))
        '''

        # print pred, arglist


with open(lisp_file, 'w') as f:

    # Generate the list of all entity names.
    entities = set()
    for category in categories:
        entities.update(category_values[category[0]])
    print >> f, '(define entities (list', quoted_list(entities), '))'

    for category in categories:
        # Define the entity ids of each category.
        cat_name = category[0]
        values = category_values[cat_name]
        for val in values:
            print >> f, '(define', entity_to_id(val, category[1]), ('"' + val + '")')

        print >> f, '(define %s-dictionary (make-dictionary %s ))' % (cat_name, quoted_list(values))
        print >> f, '(define %s (x) (dictionary-contains x %s-dictionary))' % (cat_name, cat_name)

    for relation in relations:
        rel_name = relation[0]
        values = relation_values[rel_name]

        if relation[4]:
            # functional relation
            arg1s = [x[0] for x in values]
            arg2s = [x[1] for x in values]

            print >> f, '(define %s-dictionary (make-dictionary %s ))' % (rel_name, quoted_list(arg1s))
            print >> f, '(define %s-array (array %s))' % (rel_name, quoted_list(arg2s))
            print >> f, '(define %s (x) (array-get-ith-element %s-array (dictionary-lookup x %s-dictionary)))' % (rel_name, rel_name, rel_name)
        else:
            print >> f, '(define %s-dictionary (make-dictionary %s ))' % (rel_name, quoted_relation_list(values))
            print >> f, '(define %s (x y) (dictionary-contains (cons x y) %s-dictionary))' % (rel_name, rel_name)
