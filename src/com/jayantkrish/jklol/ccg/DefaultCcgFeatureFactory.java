package com.jayantkrish.jklol.ccg;

import java.util.Arrays;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.CombiningParametricFactor;
import com.jayantkrish.jklol.models.parametric.ConstantParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;

/**
 * Creates an indicator feature parameterization for a CCG parser.
 * 
 * @author jayantk
 */
public class DefaultCcgFeatureFactory implements CcgFeatureFactory {
  
  public DefaultCcgFeatureFactory() {}

  @Override
  public ParametricFactor getDependencyFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap dependencyArgPosVar) {
    
    ParametricFactor wordWordFactor = new DenseIndicatorLogLinearFactor(
        VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar, dependencyArgNumVar, dependencyArgVar));
    ParametricFactor wordPosFactor = new DenseIndicatorLogLinearFactor(
        VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar, dependencyArgNumVar, dependencyArgPosVar));
    ParametricFactor posWordFactor = new DenseIndicatorLogLinearFactor(
        VariableNumMap.unionAll(headSyntaxVar, dependencyArgNumVar, dependencyArgVar, dependencyHeadPosVar));
    ParametricFactor posPosFactor = new DenseIndicatorLogLinearFactor(
        VariableNumMap.unionAll(headSyntaxVar, dependencyArgNumVar, dependencyHeadPosVar, dependencyArgPosVar));
    
    VariableNumMap allVars = VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar,
        dependencyArgNumVar, dependencyArgVar, dependencyHeadPosVar, dependencyArgPosVar);
    return new CombiningParametricFactor(allVars, Arrays.asList("word-word", "word-pos", "pos-word", "pos-pos"),
        Arrays.asList(wordWordFactor, wordPosFactor, posWordFactor, posPosFactor), true);
  }
  
  private ParametricFactor getDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap distanceVar) {

    ParametricFactor wordDistanceFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
        dependencyHeadVar, headSyntaxVar, dependencyArgNumVar, distanceVar));
    ParametricFactor posDistanceFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
        headSyntaxVar, dependencyArgNumVar, dependencyHeadPosVar, distanceVar));

    VariableNumMap allVars = VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar,
        dependencyArgNumVar, dependencyHeadPosVar, distanceVar);
    return new CombiningParametricFactor(allVars, Arrays.asList("distance", "pos-backoff-distance"),
        Arrays.asList(wordDistanceFactor, posDistanceFactor), true);
  }

  @Override
  public ParametricFactor getDependencyWordDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap wordDistanceVar) {
    return getDistanceFeatures(dependencyHeadVar, headSyntaxVar, dependencyArgNumVar,
        dependencyHeadPosVar, wordDistanceVar);
  }

  @Override
  public ParametricFactor getDependencyPuncDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap puncDistanceVar) {
    return getDistanceFeatures(dependencyHeadVar, headSyntaxVar, dependencyArgNumVar,
        dependencyHeadPosVar, puncDistanceVar);
  }

  @Override
  public ParametricFactor getDependencyVerbDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap verbDistanceVar) {
    return getDistanceFeatures(dependencyHeadVar, headSyntaxVar, dependencyArgNumVar,
        dependencyHeadPosVar, verbDistanceVar);
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
    VariableNumMap vars = terminalWordVar.union(terminalSyntaxVar);
    return new ConstantParametricFactor(vars, TableFactor.logUnity(vars));
  }
  
  @Override
  public ParametricFactor getHeadedBinaryRuleFeatures(VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar,
      VariableNumMap headedBinaryRulePredicateVar, VariableNumMap headedBinaryRulePosVar) {
    ParametricFactor wordFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
        leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, headedBinaryRulePredicateVar));
    ParametricFactor posFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
        leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, headedBinaryRulePosVar));

    VariableNumMap allVars = VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, 
        headedBinaryRulePredicateVar, headedBinaryRulePosVar);

    return new CombiningParametricFactor(allVars, Arrays.asList("word-binary-rule",
        "pos-binary-rule"), Arrays.asList(wordFactor, posFactor), true);
  }
}