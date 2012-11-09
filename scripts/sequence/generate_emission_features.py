 #!/usr/local/lib/python2.6

import re
import sys

filename = sys.argv[1]

def generate_string_features(word, label):
    dict = {}
    patterns = ['\d$', '\d\d$', '\d\d\d+$', '\d?\d?:\d\d$', 
                '[0-9:]+$', '[A-Z]', '[A-Z]$', '[A-Z][A-Z]$', 
                '[A-Z]+$', '[^0-9A-Za-z]+$', '[^0-9]+$', '[A-Za-z]+$',
                '[a-z]+$']

    for pattern in patterns:
        if re.match(pattern, word):
            dict['regex=' + pattern + '_label=' + label] = 1

    dict['bias_label=' + label] = 1
    dict['word=' + word.lower() + '_label=' + label] = 1

    return dict

words = set()
labels = set()
with open(filename, 'r') as f:
    for line in f:
        chunks = line.strip().split(" ")
        for chunk in chunks:
            parts = chunk.split("/")
            words.add(parts[0].strip())
            labels.add(parts[1].strip())

for word in words:
    for label in labels:
        features = generate_string_features(word, label)
        for feature in features.keys():
            print "%s,%s,%s,%d" % (word, label, feature, features[feature])
