package com.jayantkrish.jklol.experiments.geoquery;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.base.Functions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgFeatureFactory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lexicon.ParametricCcgLexicon;
import com.jayantkrish.jklol.ccg.lexicon.ParametricFeaturizedLexiconScorer;
import com.jayantkrish.jklol.ccg.lexicon.ParametricLexiconScorer;
import com.jayantkrish.jklol.ccg.lexicon.ParametricTableLexicon;
import com.jayantkrish.jklol.ccg.lexicon.ParametricUnknownWordLexicon;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.parametric.CombiningParametricFactor;
import com.jayantkrish.jklol.models.parametric.ConstantParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class GeoqueryFeatureFactory implements CcgFeatureFactory {

  private final boolean useDependencyFeatures;
  private final boolean useSyntacticFeatures;
  private final String lexiconFeatureAnnotationName;
  private final DiscreteVariable lexiconFeatureDictionary;
  
  private final List<LexiconEntry> entityNames;
  
  public GeoqueryFeatureFactory(boolean useDependencyFeatures, boolean useSyntacticFeatures,
      String lexiconFeatureAnnotationName, DiscreteVariable lexiconFeatureDictionary,
      List<LexiconEntry> entityNames) {
    this.useDependencyFeatures = useDependencyFeatures;
    this.useSyntacticFeatures = useSyntacticFeatures;
    this.lexiconFeatureAnnotationName = lexiconFeatureAnnotationName;
    this.lexiconFeatureDictionary = lexiconFeatureDictionary;
    this.entityNames = entityNames;
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
      ParametricFactor syntaxDistanceFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
          headSyntaxVar, dependencyArgNumVar, distanceVar), true);
      
      return new CombiningParametricFactor(allVars, Arrays.asList("distance", "syntaxDistance", "allVars"),
          Arrays.asList(wordDistanceFactor, syntaxDistanceFactor, onesFactor), true);
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
  public List<ParametricCcgLexicon> getLexiconFeatures(VariableNumMap terminalWordVar,
      VariableNumMap ccgCategoryVar, VariableNumMap terminalPosVar, VariableNumMap terminalSyntaxVar,
      DiscreteFactor lexiconIndicatorFactor, Collection<LexiconEntry> lexiconEntries,
      DiscreteFactor unknownLexiconIndicatorFactor, Collection<LexiconEntry> unknownLexiconEntries) {
    // Features for mapping words to ccg categories (which include both 
    // syntax and semantics). 
    ParametricFactor terminalIndicatorFactor = new IndicatorLogLinearFactor(
        terminalWordVar.union(ccgCategoryVar), lexiconIndicatorFactor);
    
    // Additional features that generalize across lexicon entries.
    VariableNumMap terminalVars = terminalWordVar.union(ccgCategoryVar);
    int varNum = Ints.max(VariableNumMap.unionAll(terminalWordVar, ccgCategoryVar).getVariableNumsArray()) + 1;
    DiscreteVariable featureNames = new DiscreteVariable("featureVar", Arrays.asList("entity-match", "entity-name-match"));
    VariableNumMap featureVar = VariableNumMap.singleton(varNum, "lexiconFeatures", featureNames);
    TableFactorBuilder featureBuilder = new TableFactorBuilder(terminalVars.union(featureVar),
        SparseTensorBuilder.getFactory());

    for (LexiconEntry entry : entityNames) {
      CcgCategory category = entry.getCategory();
      Expression2 lf = category.getLogicalForm();

      if (lf.isConstant()) {
        String[] constantNameParts = lf.getConstant().split(":");
        String type = "";
        if (constantNameParts.length >= 2) {
          type = constantNameParts[1];
        }
        
        if (!type.equals("n")) {
          Assignment assignment = terminalVars.outcomeArrayToAssignment(entry.getWords(), entry.getCategory())
              .union(featureVar.outcomeArrayToAssignment("entity-match"));
          featureBuilder.setWeight(assignment, entry.getWords().size());
        } else {
          Assignment assignment = terminalVars.outcomeArrayToAssignment(entry.getWords(), entry.getCategory())
              .union(featureVar.outcomeArrayToAssignment("entity-name-match"));
          featureBuilder.setWeight(assignment, entry.getWords().size());
        }
      }
    }

    DiscreteLogLinearFactor additionalFeatures = new DiscreteLogLinearFactor(terminalVars, featureVar,
        featureBuilder.build(), lexiconIndicatorFactor);

    ParametricFactor terminalParametricFactor = new CombiningParametricFactor(terminalVars,
        Arrays.asList("indicators", "features"), Arrays.asList(terminalIndicatorFactor, additionalFeatures), false);

    List<ParametricCcgLexicon> lexicons = Lists.newArrayList();
    lexicons.add(new ParametricTableLexicon(
        terminalWordVar, ccgCategoryVar, terminalParametricFactor));
    
    if (unknownLexiconEntries.size() > 0) {
      ParametricFactor unknownTerminalFamily = new IndicatorLogLinearFactor(
          terminalPosVar.union(ccgCategoryVar), unknownLexiconIndicatorFactor);
      ParametricCcgLexicon unknownLexicon = new ParametricUnknownWordLexicon(terminalWordVar,
          terminalPosVar, ccgCategoryVar, unknownTerminalFamily);
      
      lexicons.add(unknownLexicon);
    }
    return lexicons;
  }
  
  @Override
  public List<ParametricLexiconScorer> getLexiconScorers(VariableNumMap terminalWordVar,
      VariableNumMap ccgCategoryVar, VariableNumMap terminalPosVar,
      VariableNumMap terminalSyntaxVar) {
    if (lexiconFeatureAnnotationName != null) {
      VariableNumMap featureVar = VariableNumMap.singleton(ccgCategoryVar.getOnlyVariableNum() - 1,
          lexiconFeatureAnnotationName, lexiconFeatureDictionary);
      ParametricLinearClassifierFactor classifierFamily = new ParametricLinearClassifierFactor(
          featureVar, ccgCategoryVar, VariableNumMap.EMPTY,
          lexiconFeatureDictionary, null, false);

      ParametricLexiconScorer scorer = new ParametricFeaturizedLexiconScorer("features",
          ccgCategoryVar, featureVar, classifierFamily, Functions.<CcgCategory>identity());

      return Lists.newArrayList(scorer);
    } else {
      return Collections.emptyList();
    }
  }
  
  @Override
  public ParametricFactor getWordSkipFactor(VariableNumMap terminalWordVar) {
    return new DenseIndicatorLogLinearFactor(terminalWordVar, false);
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
