package com.jayantkrish.jklol.probdb;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.ArrayUtils;

/**
 * Existentially quantifies out one or more variables.
 *  
 * @author jayant
 */
public class ExistentialQuery extends AbstractQuery {

  private final Query subQuery;
  private final int[] varsToEliminate;
  
  public ExistentialQuery(Query subQuery, int[] varsToEliminate) {
    super(Arrays.asList(subQuery));
    this.subQuery = Preconditions.checkNotNull(subQuery);
    this.varsToEliminate = ArrayUtils.copyOf(varsToEliminate, varsToEliminate.length);
  }
  
  @Override
  public TableAssignment evaluate(DbAssignment db) {
    TableAssignment subAssignment = subQuery.evaluate(db);
    
    VariableNumMap resultVars = subAssignment.getVariables().removeAll(Ints.asList(varsToEliminate));
    Tensor resultIndicators = subAssignment.getIndicators().maxOutDimensions(varsToEliminate);
    return new TableAssignment(resultVars, resultIndicators);
  }  
}
