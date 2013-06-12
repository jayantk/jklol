#!/usr/local/bin/python2.6

import sys
import re

template_file=sys.argv[1]
example_file=sys.argv[2]

template = None
with open(template_file, 'r') as f:
    lines = f.readlines()
    template = " ".join([x.strip() for x in lines])
    
    print '# Data generated using template: '
    for line in lines:
        print '# %s' % line,
    print

with open(example_file, 'r') as f:
    for line in f:
        if (len(line.strip()) == 0):
            continue

        parts = line.split('#')
        
        args = parts[0].strip().split(",")
        value = template
        for i in xrange(len(args)):
            value = re.sub('<arg%d>' % i, args[i], value)
        
        print '"%s","%s"' % (value, parts[1].strip())
