package com.jayantkrish.jklol.models;

import java.util.*;

/**
 * A sparse conditional probability table (for a BN). Stores a conditional probability over a set of
 * children conditioned on a set of parents. The CPT is sparse because some outcomes are guaranteed to
 * have 0 probability.
 *
 * Also stores sufficient statistics for estimating this same CPT.
 */ 
public class SparseCpt {

    // Child / parent variables define possible outcomes.
    private List<Variable> childVars;
    private List<Variable> parentVars;
    private List<Integer> parentNums;
    private List<Integer> childNums;

    private List<Integer> allNums;
    private List<Variable> allVars;

    // TODO: Maybe these should be dense? It's unclear...
    private SparseOutcomeTable<Double> childStatistics;
    private SparseOutcomeTable<Double> parentStatistics;



    public Iterator<Assignment> assignmentIterator() {
	return childStatistics.assignmentIterator();
    }


}