package com.jayantkrish.jklol.ccg.gi;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEval.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class ParametricGroundedParser implements ParametricFamily<GroundedParser> {
  private static final long serialVersionUID = 1L;
  
  private final ParametricCcgParser ccgFamily;
  private final ParametricIncEval evalFamily;
  
  public ParametricGroundedParser(ParametricCcgParser ccgFamily,
      ParametricIncEval evalFamily) {
    this.ccgFamily = Preconditions.checkNotNull(ccgFamily);
    this.evalFamily = Preconditions.checkNotNull(evalFamily);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return new ListSufficientStatistics(Arrays.asList("ccg", "eval"), 
        Arrays.asList(ccgFamily.getNewSufficientStatistics(), evalFamily.getNewSufficientStatistics()));
  }

  @Override
  public GroundedParser getModelFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    CcgParser parser = ccgFamily.getModelFromParameters(parameterList.get(0));
    IncEval eval = evalFamily.getModelFromParameters(parameterList.get(1));
    return new GroundedParser(parser, eval);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();

    StringBuilder sb = new StringBuilder();
    sb.append(ccgFamily.getParameterDescription(parameterList.get(0), numFeatures));
    sb.append(evalFamily.getParameterDescription(parameterList.get(1), numFeatures));
    return sb.toString();
  }
  
  public void incrementSufficientStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, AnnotatedSentence sentence,
      Object diagram, GroundedCcgParse parse, double count) {
    List<SufficientStatistics> gradientList = gradient.coerceToList().getStatistics();
    List<SufficientStatistics> parameterList = currentParameters.coerceToList().getStatistics();
    
    ccgFamily.incrementSufficientStatistics(gradientList.get(0), parameterList.get(0),
        sentence, parse, count);
    
    for (IncEvalState state : parse.getStates()) {
      evalFamily.incrementSufficientStatistics(gradientList.get(1), parameterList.get(1),
          parse.getLogicalForm(), state, count);
    }
  }
}
