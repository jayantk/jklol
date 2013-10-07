package com.jayantkrish.jklol.ccg;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;

/**
 * Creates an indicator feature parameterization for a CCG parser.
 * 
 * @author jayantk
 */
public class DefaultCcgFeatureFactory implements CcgFeatureFactory {
  
  public DefaultCcgFeatureFactory() {}

  @Override
  public ParametricFactor getDependencyFeatures(VariableNumMap semanticHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap semanticArgNumVar, VariableNumMap semanticArgVar) {
    VariableNumMap vars = VariableNumMap.unionAll(semanticHeadVar, headSyntaxVar, semanticArgNumVar, semanticArgVar);
    return new DenseIndicatorLogLinearFactor(vars);
  }

  @Override
  public ParametricFactor getDependencyWordDistanceFeatures(VariableNumMap semanticHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap semanticArgNumVar, VariableNumMap wordDistanceVar) {
    return new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
        semanticHeadVar, headSyntaxVar, semanticArgNumVar, wordDistanceVar));
  }

  @Override
  public ParametricFactor getDependencyPuncDistanceFeatures(VariableNumMap semanticHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap semanticArgNumVar, VariableNumMap puncDistanceVar) {
    return new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
        semanticHeadVar, headSyntaxVar, semanticArgNumVar, puncDistanceVar));
  }

  @Override
  public ParametricFactor getDependencyVerbDistanceFeatures(VariableNumMap semanticHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap semanticArgNumVar, VariableNumMap verbDistanceVar) {
    return new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
        semanticHeadVar, headSyntaxVar, semanticArgNumVar, verbDistanceVar));
  }

  @Override
  public ParametricFactor getLexiconFeatures(VariableNumMap terminalWordVar,
      VariableNumMap ccgCategoryVar, DiscreteFactor lexiconIndicatorFactor) {
    return new IndicatorLogLinearFactor(terminalWordVar.union(ccgCategoryVar),
        lexiconIndicatorFactor);
  }

  @Override
  public ParametricFactor getLexiconSyntaxFeatures(VariableNumMap terminalWordVar,
      VariableNumMap terminalSyntaxVar, DiscreteFactor lexiconSyntaxIndicatorFactor) {
    return new IndicatorLogLinearFactor(terminalWordVar.union(terminalSyntaxVar),
        lexiconSyntaxIndicatorFactor);
  }
}
