#!/usr/bin/python

import sys

listfile = sys.argv[1]

with open(listfile, 'r') as f:
    for line in f:
        parts = line.strip().partition(":-")
        tokens = parts[0].strip()

        parts = parts[2].partition(' : ')

        syntax = parts[0].strip()
        lf = parts[2].strip()
        t = lf.split(":")[1]

        print '"%(tokens)s","N:e{0}","%(lf)s","0 entity:%(type)s","0 %(lf)s"' % {'tokens' : tokens, 'type' : t, 'lf' : lf}
