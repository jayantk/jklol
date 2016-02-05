package com.jayantkrish.jklol.ccg.gi;

import com.jayantkrish.jklol.ccg.gi.IncrementalEval.IncrementalEvalState;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public interface ParametricIncrementalEval extends ParametricFamily<IncrementalEval> {

  public void incrementSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Expression2 lf, IncrementalEvalState state,
      double count);
}
