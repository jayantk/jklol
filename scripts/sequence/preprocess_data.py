#!/usr/local/lib/python2.6

import re
import sys

filename = sys.argv[1]

with open(filename, 'r') as f:
    for line in f:
        chunks = line.strip().split(" ")
        for chunk in chunks:
            chunk = re.sub(',', '', chunk)
            if '/' in chunk:
                print chunk,
            else:
                print '%s/%s' % (chunk, "none"),
        print
