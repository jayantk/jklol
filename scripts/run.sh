#!/bin/bash

# Runs a java program with the correct classpath.

java -cp jklol.jar:lib/* -Xmx8000M -Xss8m $@ 
