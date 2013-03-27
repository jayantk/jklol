#!/usr/local/bin/python2.6

import sys
import re

def read_pos_tags(file):
    sents = []
    with open(file, 'r') as f:
        sent = []
        for line in f:
            if (line.startswith('(')):
                if (len(sent) > 0):
                    sents.append(sent)
                sent = []

            for match in re.finditer('\(([^() ]*)\s+([^() ]*)\)', line):
                sent.append((match.group(2), match.group(1)))

    if (len(sent) > 0):
        sents.append(sent)
    return sents

def format_pos_tags(sent):
    return ' '.join([' '.join(x) for x in sent if x[1] != '-NONE-'])

sents = read_pos_tags(sys.argv[1])

for sent in sents:
    print format_pos_tags(sent)
