package com.jayantkrish.jklol.p3;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.lisp.inc.IncEval;
import com.jayantkrish.jklol.lisp.inc.IncEvalState;
import com.jayantkrish.jklol.lisp.inc.ParametricIncEval;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class ParametricP3Model implements ParametricFamily<P3Model> {
  private static final long serialVersionUID = 1L;
  
  private final ParametricCcgParser ccgFamily;
  private final ParametricIncEval evalFamily;
  
  public static final String CCG_PARAMETER_NAME="ccg";
  public static final String EVAL_PARAMETER_NAME="eval";
  
  public ParametricP3Model(ParametricCcgParser ccgFamily,
      ParametricIncEval evalFamily) {
    this.ccgFamily = Preconditions.checkNotNull(ccgFamily);
    this.evalFamily = Preconditions.checkNotNull(evalFamily);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return new ListSufficientStatistics(Arrays.asList(CCG_PARAMETER_NAME, EVAL_PARAMETER_NAME), 
        Arrays.asList(ccgFamily.getNewSufficientStatistics(), evalFamily.getNewSufficientStatistics()));
  }

  @Override
  public P3Model getModelFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    CcgParser parser = ccgFamily.getModelFromParameters(parameterList.get(0));
    IncEval eval = evalFamily.getModelFromParameters(parameterList.get(1));
    return new P3Model(parser, eval);
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
      Object diagram, P3Parse parse, double count) {
    List<SufficientStatistics> gradientList = gradient.coerceToList().getStatistics();
    List<SufficientStatistics> parameterList = currentParameters.coerceToList().getStatistics();
    
    ccgFamily.incrementSufficientStatistics(gradientList.get(0), parameterList.get(0),
        sentence, parse, count);
    
    for (IncEvalState state : parse.getStates()) {
      evalFamily.incrementSufficientStatistics(gradientList.get(1), parameterList.get(1),
          parse.getLogicalForm(), state, count);
    }
  }
  
  public void incrementParserStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, AnnotatedSentence sentence,
      CcgParse parse, double count) {
    List<SufficientStatistics> gradientList = gradient.coerceToList().getStatistics();
    List<SufficientStatistics> parameterList = currentParameters.coerceToList().getStatistics();
    
    ccgFamily.incrementSufficientStatistics(gradientList.get(0), parameterList.get(0),
        sentence, parse, count);
  }
  
  public void incrementEvalStatistics(SufficientStatistics gradient,
      SufficientStatistics currentParameters, Expression2 logicalForm,
      IncEvalState state, double count) {
    List<SufficientStatistics> gradientList = gradient.coerceToList().getStatistics();
    List<SufficientStatistics> parameterList = currentParameters.coerceToList().getStatistics();

    evalFamily.incrementSufficientStatistics(gradientList.get(1), parameterList.get(1),
        logicalForm, state, count);
  }
}
