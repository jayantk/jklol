package com.jayantkrish.jklol.inference;

import java.util.Collection;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A marginal distribution composed of (1) a marginal distribution and (2) an
 * assignment. Both components are on disjoint sets of variables.
 * 
 * @author jayantk
 */
public class ProductMarginalSet implements MarginalSet {

  private final Assignment assignment;
  private final MarginalSet baseMarginal;

  public ProductMarginalSet(Assignment assignment, MarginalSet baseMarginal) {
    // The variables in assignment and baseMarginal must be disjoint.
    for (Integer varNum : assignment.getVarNumsSorted()) {
      Preconditions.checkArgument(!baseMarginal.getVarNums().contains(varNum));
    }

    this.assignment = assignment;
    this.baseMarginal = baseMarginal;
  }
  
  @Override
  public Set<Integer> getVarNums() {
    Set<Integer> variables = Sets.newHashSet(baseMarginal.getVarNums());
    variables.addAll(assignment.getVarNumsSorted());
    return variables;
  }

  @Override
  public Factor getMarginal(Collection<Integer> varNums) {    
    Set<Integer> baseVars = Sets.newHashSet(baseMarginal.getVarNums());
    baseVars.retainAll(varNums);
    Factor base = baseMarginal.getMarginal(baseVars);
        
    // TODO: For generality (e.g., with continuous variables), this
    // should 
            Assignment subAssignment = assignment.subAssignment(varNums);

  }

  @Override
  public double getPartitionFunction() {
    return baseMarginal.getPartitionFunction();
  }
}
