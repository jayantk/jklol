package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;

import com.jayantkrish.jklol.ccg.lexicon.ParametricCcgLexicon;
import com.jayantkrish.jklol.ccg.lexicon.ParametricTableLexicon;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.CombiningParametricFactor;
import com.jayantkrish.jklol.models.parametric.ConstantParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;

public class SemanticParserFeatureFactory implements CcgFeatureFactory {

  private final boolean useDependencyFeatures;
  private final boolean useSyntacticFeatures;
  
  public SemanticParserFeatureFactory(boolean useDependencyFeatures, boolean useSyntacticFeatures) {
    this.useDependencyFeatures = useDependencyFeatures;
    this.useSyntacticFeatures = useSyntacticFeatures;
  }

  @Override
  public DiscreteVariable getSemanticPredicateVar(List<String> semanticPredicates) {
    return new DiscreteVariable("semanticPredicates", semanticPredicates);
  }

  @Override
  public ParametricFactor getDependencyFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap dependencyArgPosVar) {

    VariableNumMap allVars = VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar,
        dependencyArgNumVar, dependencyArgVar, dependencyHeadPosVar, dependencyArgPosVar);
    ParametricFactor onesFactor = new ConstantParametricFactor(allVars, TableFactor.logUnity(allVars));

    if (useDependencyFeatures) {
      ParametricFactor wordWordFactor = new DenseIndicatorLogLinearFactor(
          VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar, dependencyArgNumVar, dependencyArgVar), true);

      return new CombiningParametricFactor(allVars, Arrays.asList("word-word", "allVars"),
          Arrays.asList(wordWordFactor, onesFactor), true);
    } else {
      return onesFactor;
    }
  }

  private ParametricFactor getDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap distanceVar) {
    VariableNumMap allVars = VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar,
        dependencyArgNumVar, dependencyHeadPosVar, distanceVar);
    ParametricFactor onesFactor = new ConstantParametricFactor(allVars, TableFactor.logUnity(allVars));

    if (useDependencyFeatures) {
      ParametricFactor wordDistanceFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
          dependencyHeadVar, headSyntaxVar, dependencyArgNumVar, distanceVar), true);
      return new CombiningParametricFactor(allVars, Arrays.asList("distance", "allVars"),
          Arrays.asList(wordDistanceFactor, onesFactor), true);
    } else {
      return onesFactor;
    }
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
    // Can't compute the distance in terms of punctuation symbols
    // without POS tags to identify punctuation.
    VariableNumMap allVars = VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar,
        dependencyArgNumVar, dependencyHeadPosVar, puncDistanceVar);
    return new ConstantParametricFactor(allVars, TableFactor.logUnity(allVars));
  }

  @Override
  public ParametricFactor getDependencyVerbDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap verbDistanceVar) {
    // Can't compute the distance in terms of verbs without
    // POS tags to identify verbs.
    VariableNumMap allVars = VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar,
        dependencyArgNumVar, dependencyHeadPosVar, verbDistanceVar);
    return new ConstantParametricFactor(allVars, TableFactor.logUnity(allVars));
  }

  @Override
  public ParametricCcgLexicon getLexiconFeatures(VariableNumMap terminalWordVar,
      VariableNumMap ccgCategoryVar, VariableNumMap terminalPosVar, VariableNumMap terminalSyntaxVar,
      DiscreteFactor lexiconIndicatorFactor) {
    // Features for mapping words to ccg categories (which include both 
    // syntax and semantics). 
    ParametricFactor terminalParametricFactor = new IndicatorLogLinearFactor(
        terminalWordVar.union(ccgCategoryVar), lexiconIndicatorFactor);

    // Backoff features mapping words to syntactic categories (ignoring 
    // semantics). The CCGbank lexicon does not contain multiple lexicon 
    // entries for a single word with the same syntactic category but different 
    // semantics, so these features are set to be ignored. 
    VariableNumMap vars = terminalWordVar.union(terminalSyntaxVar); 
    ParametricFactor terminalSyntaxFactor = new ConstantParametricFactor(vars,
        TableFactor.logUnity(vars));

    // Backoff distribution over parts-of-speech and syntactic 
    // categories.
    VariableNumMap terminalPosVars = VariableNumMap.unionAll(terminalPosVar, terminalSyntaxVar);
    ParametricFactor terminalPosParametricFactor = new ConstantParametricFactor(terminalPosVars,
        TableFactor.logUnity(terminalPosVars));

    return new ParametricTableLexicon(terminalWordVar, ccgCategoryVar, terminalParametricFactor,
        terminalPosVar, terminalSyntaxVar, terminalPosParametricFactor, terminalSyntaxFactor);
  }

  @Override
  public ParametricFactor getBinaryRuleFeatures(VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar, DiscreteFactor binaryRuleDistribution) {
    VariableNumMap allVars = VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar, parentSyntaxVar);
    if (useSyntacticFeatures) {
      return new DenseIndicatorLogLinearFactor(allVars, true);
    } else {
      return new ConstantParametricFactor(allVars, TableFactor.logUnity(allVars));
    }
  }

  @Override
  public ParametricFactor getUnaryRuleFeatures(VariableNumMap unaryRuleSyntaxVar,
      VariableNumMap unaryRuleVar, DiscreteFactor unaryRuleDistribution) {
    VariableNumMap allVars = VariableNumMap.unionAll(unaryRuleSyntaxVar, unaryRuleVar);
    if (useSyntacticFeatures) {
      return new IndicatorLogLinearFactor(allVars, unaryRuleDistribution);
    } else {
      return new ConstantParametricFactor(allVars, unaryRuleDistribution);
    }
  }

  @Override
  public ParametricFactor getHeadedBinaryRuleFeatures(VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar,
      VariableNumMap headedBinaryRulePredicateVar, VariableNumMap headedBinaryRulePosVar) {

    VariableNumMap allVars = VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar, parentSyntaxVar,
        headedBinaryRulePredicateVar, headedBinaryRulePosVar);
    ParametricFactor onesFactor = new ConstantParametricFactor(allVars, TableFactor.logUnity(allVars));
    
    if (useSyntacticFeatures) {
      ParametricFactor wordFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
          leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, headedBinaryRulePredicateVar), true);
    return new CombiningParametricFactor(allVars, Arrays.asList("word-binary-rule",
        "allVars"), Arrays.asList(wordFactor, onesFactor), true);      
    } else {
      return onesFactor;
    }
  }

  @Override
  public ParametricFactor getHeadedRootFeatures(VariableNumMap rootSyntaxVar, VariableNumMap rootPredicateVar,
      VariableNumMap rootPosVar) {
    VariableNumMap allVars = VariableNumMap.unionAll(rootSyntaxVar, rootPredicateVar, rootPosVar);
    ParametricFactor onesFactor = new ConstantParametricFactor(allVars, TableFactor.logUnity(allVars));
    
    if (useSyntacticFeatures) {
      ParametricFactor wordFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
          rootSyntaxVar, rootPredicateVar), true);
      return new CombiningParametricFactor(allVars, Arrays.asList("root-word",
          "allVars"), Arrays.asList(wordFactor, onesFactor), true);  
    } else {
      return onesFactor;
    }
  }
}
