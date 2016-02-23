package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lexicon.ConstantParametricLexiconScorer;
import com.jayantkrish.jklol.ccg.lexicon.ParametricCcgLexicon;
import com.jayantkrish.jklol.ccg.lexicon.ParametricFeaturizedLexiconScorer;
import com.jayantkrish.jklol.ccg.lexicon.ParametricLexiconScorer;
import com.jayantkrish.jklol.ccg.lexicon.ParametricSyntaxLexiconScorer;
import com.jayantkrish.jklol.ccg.lexicon.ParametricTableLexicon;
import com.jayantkrish.jklol.ccg.lexicon.ParametricUnknownWordLexicon;
import com.jayantkrish.jklol.ccg.supertag.SupertagLexiconScorer;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.parametric.CombiningParametricFactor;
import com.jayantkrish.jklol.models.parametric.ConstantParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;

/**
 * Default collection of features for a CCG parser. This collection
 * includes indicator features for lexicon entries, dependencies, 
 * dependency distances and combinators. Optionally can be configured
 * to include indicator features for POS tags and arbitrary feature
 * vectors for lexicon entries. 
 * 
 * @author jayantk
 */
public class DefaultCcgFeatureFactory implements CcgFeatureFactory {

  private final String lexiconFeatureAnnotationName;
  private final DiscreteVariable lexiconFeatureVariable;
  private final boolean usePosFeatures;
  private final boolean allowWordSkipping;
  private final String supertagAnnotationName;
  
  /**
   * Creates a feature factory that instantiates the indicator features
   * described above. If {@code usePosFeatures} is true, also includes
   * POS tags (and various backoff combinations) within all of the feature
   * sets.
   *      
   * @param usePosFeatures
   * @param allowWordSkipping
   */
  public DefaultCcgFeatureFactory(boolean usePosFeatures, boolean allowWordSkipping) {
    this.lexiconFeatureAnnotationName = null;
    this.lexiconFeatureVariable = null;
    // Both must be null or non-null
    Preconditions.checkArgument(!(lexiconFeatureVariable == null ^ lexiconFeatureAnnotationName == null));

    this.usePosFeatures = usePosFeatures;
    this.allowWordSkipping = allowWordSkipping;
    this.supertagAnnotationName = null;
  }

  /**
   * Creates a feature factory that uses additional lexicon entry features
   * (given by {@code lexiconFeatureVariable}) and optionally includes a 
   * supertag-based filter on possible lexicon entries.
   * 
   * @param lexiconFeatureAnnotationName
   * @param lexiconFeatureVariable
   * @param usePosFeatures
   * @param allowWordSkipping
   * @param supertagAnnotationName
   */
  public DefaultCcgFeatureFactory(String lexiconFeatureAnnotationName,
      DiscreteVariable lexiconFeatureVariable, boolean usePosFeatures,
      boolean allowWordSkipping, String supertagAnnotationName) {
    this.lexiconFeatureAnnotationName = lexiconFeatureAnnotationName;
    this.lexiconFeatureVariable = lexiconFeatureVariable;
    // Both must be null or non-null
    Preconditions.checkArgument(!(lexiconFeatureVariable == null ^ lexiconFeatureAnnotationName == null));
    
    this.usePosFeatures = usePosFeatures;
    this.allowWordSkipping = allowWordSkipping;
    this.supertagAnnotationName = supertagAnnotationName;
  }

  @Override
  public DiscreteVariable getSemanticPredicateVar(List<String> semanticPredicates) {
    return new DiscreteVariable("semanticPredicates", semanticPredicates);
  }

  @Override
  public ParametricFactor getDependencyFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap dependencyArgPosVar) {

    ParametricFactor wordWordFactor = new DenseIndicatorLogLinearFactor(
        VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar, dependencyArgNumVar, dependencyArgVar), true);
    VariableNumMap wordPosVars = VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar, dependencyArgNumVar, dependencyArgPosVar);
    VariableNumMap posWordVars = VariableNumMap.unionAll(headSyntaxVar, dependencyArgNumVar, dependencyArgVar, dependencyHeadPosVar);
    VariableNumMap posPosVars = VariableNumMap.unionAll(headSyntaxVar, dependencyArgNumVar, dependencyHeadPosVar, dependencyArgPosVar);
    ParametricFactor wordPosFactor, posWordFactor, posPosFactor;
    if (usePosFeatures) {
      wordPosFactor = new DenseIndicatorLogLinearFactor(wordPosVars, true);
      posWordFactor = new DenseIndicatorLogLinearFactor(posWordVars, true);
      posPosFactor = new DenseIndicatorLogLinearFactor(posPosVars, true);
    } else {
      wordPosFactor = new ConstantParametricFactor(wordPosVars, TableFactor.logUnity(wordPosVars));
      posWordFactor = new ConstantParametricFactor(posWordVars, TableFactor.logUnity(posWordVars));
      posPosFactor = new ConstantParametricFactor(posPosVars, TableFactor.logUnity(posPosVars));
    }

    VariableNumMap allVars = VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar,
        dependencyArgNumVar, dependencyArgVar, dependencyHeadPosVar, dependencyArgPosVar);
    return new CombiningParametricFactor(allVars, Arrays.asList("word-word", "word-pos", "pos-word", "pos-pos"),
        Arrays.asList(wordWordFactor, wordPosFactor, posWordFactor, posPosFactor), true);
  }

  private ParametricFactor getDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap distanceVar) {

    ParametricFactor wordDistanceFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
        dependencyHeadVar, headSyntaxVar, dependencyArgNumVar, distanceVar), true);
    VariableNumMap posDistanceVars = VariableNumMap.unionAll(
          headSyntaxVar, dependencyArgNumVar, dependencyHeadPosVar, distanceVar);
    ParametricFactor posDistanceFactor;
    if (usePosFeatures) {
      posDistanceFactor = new DenseIndicatorLogLinearFactor(posDistanceVars, true);
    } else {
      posDistanceFactor = new ConstantParametricFactor(posDistanceVars, TableFactor.logUnity(posDistanceVars));
    }

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
    if (usePosFeatures) {
      return getDistanceFeatures(dependencyHeadVar, headSyntaxVar, dependencyArgNumVar,
          dependencyHeadPosVar, puncDistanceVar);
    } else {
      // Can't compute the distance in terms of punctuation symbols
      // without POS tags to identify punctuation.
      VariableNumMap allVars = VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar,
        dependencyArgNumVar, dependencyHeadPosVar, puncDistanceVar);
      return new ConstantParametricFactor(allVars, TableFactor.logUnity(allVars));
    }
  }

  @Override
  public ParametricFactor getDependencyVerbDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap verbDistanceVar) {
    if (usePosFeatures) {
      return getDistanceFeatures(dependencyHeadVar, headSyntaxVar, dependencyArgNumVar,
          dependencyHeadPosVar, verbDistanceVar);
    } else {
      // Can't compute the distance in terms of verbs without
      // POS tags to identify verbs.
      VariableNumMap allVars = VariableNumMap.unionAll(dependencyHeadVar, headSyntaxVar,
          dependencyArgNumVar, dependencyHeadPosVar, verbDistanceVar);
      return new ConstantParametricFactor(allVars, TableFactor.logUnity(allVars));
    }
  }

  @Override
  public List<ParametricCcgLexicon> getLexiconFeatures(VariableNumMap terminalWordVar,
      VariableNumMap ccgCategoryVar, VariableNumMap terminalPosVar, VariableNumMap terminalSyntaxVar,
      DiscreteFactor lexiconIndicatorFactor, Collection<LexiconEntry> lexiconEntries,
      DiscreteFactor unknownLexiconIndicatorFactor, Collection<LexiconEntry> unknownLexiconEntries) {
    List<ParametricCcgLexicon> lexicons = Lists.newArrayList();

    // Lexicon mapping words to ccg categories (which include both 
    // syntax and semantics). 
    ParametricFactor terminalParametricFactor = new IndicatorLogLinearFactor(
        terminalWordVar.union(ccgCategoryVar), lexiconIndicatorFactor);
    lexicons.add(new ParametricTableLexicon(terminalWordVar, ccgCategoryVar, terminalParametricFactor));
    
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
    List<ParametricLexiconScorer> scorers = Lists.newArrayList();
    if (supertagAnnotationName != null) {
      scorers.add(new ConstantParametricLexiconScorer(
          new SupertagLexiconScorer(supertagAnnotationName)));
    }

    if (usePosFeatures) {
      // Backoff features mapping words to syntactic categories (ignoring 
      // semantics). These features aren't very useful for semantic parsing. 
      VariableNumMap vars = terminalWordVar.union(terminalSyntaxVar); 
      ParametricFactor terminalSyntaxFamily = new ConstantParametricFactor(vars,
          TableFactor.logUnity(vars));

      VariableNumMap terminalPosVars = VariableNumMap.unionAll(terminalPosVar, terminalSyntaxVar);
      ParametricFactor terminalPosFamily = new DenseIndicatorLogLinearFactor(terminalPosVars, true);

      scorers.add(new ParametricSyntaxLexiconScorer(terminalWordVar, terminalPosVar,
          terminalSyntaxVar, terminalPosFamily, terminalSyntaxFamily));
    }

    if (lexiconFeatureAnnotationName != null) {
      VariableNumMap featureVar = VariableNumMap.singleton(terminalSyntaxVar.getOnlyVariableNum() - 1,
          "ccgLexiconFeatures", lexiconFeatureVariable);
      ParametricLinearClassifierFactor featureFamily = new ParametricLinearClassifierFactor(
          featureVar, terminalSyntaxVar, VariableNumMap.EMPTY,
          lexiconFeatureVariable, null, false);

      scorers.add(new ParametricFeaturizedLexiconScorer(lexiconFeatureAnnotationName,
          terminalSyntaxVar, featureVar, featureFamily, new Function<CcgCategory, Object>() {
            @Override
            public Object apply(CcgCategory category) {
              return category.getSyntax();
            }
          }));
    }

    return scorers;
  }

  @Override
  public ParametricFactor getWordSkipFactor(VariableNumMap terminalWordVar) {
    if (allowWordSkipping) {
      return new DenseIndicatorLogLinearFactor(terminalWordVar, false);
    } else {
      return null;
    }
  }

  @Override
  public ParametricFactor getBinaryRuleFeatures(VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar, DiscreteFactor binaryRuleDistribution) {
    VariableNumMap allVars = VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar, parentSyntaxVar);
    return new DenseIndicatorLogLinearFactor(allVars, true);
  }

  @Override
  public ParametricFactor getUnaryRuleFeatures(VariableNumMap unaryRuleSyntaxVar,
      VariableNumMap unaryRuleVar, DiscreteFactor unaryRuleDistribution) {
    VariableNumMap allVars = VariableNumMap.unionAll(unaryRuleSyntaxVar, unaryRuleVar);
    return new IndicatorLogLinearFactor(allVars, unaryRuleDistribution);
  }

  @Override
  public ParametricFactor getHeadedBinaryRuleFeatures(VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar,
      VariableNumMap headedBinaryRulePredicateVar, VariableNumMap headedBinaryRulePosVar) {
    ParametricFactor wordFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
        leftSyntaxVar, rightSyntaxVar, parentSyntaxVar, headedBinaryRulePredicateVar), true);
    
    VariableNumMap posVars = VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar,
        parentSyntaxVar, headedBinaryRulePosVar);
    ParametricFactor posFactor;
    if (usePosFeatures) {
      posFactor = new DenseIndicatorLogLinearFactor(posVars, true);
    } else {
      posFactor = new ConstantParametricFactor(posVars, TableFactor.logUnity(posVars));
    }

    VariableNumMap allVars = VariableNumMap.unionAll(leftSyntaxVar, rightSyntaxVar, parentSyntaxVar,
        headedBinaryRulePredicateVar, headedBinaryRulePosVar);

    return new CombiningParametricFactor(allVars, Arrays.asList("word-binary-rule",
        "pos-binary-rule"), Arrays.asList(wordFactor, posFactor), true);
  }

  @Override
  public ParametricFactor getHeadedRootFeatures(VariableNumMap rootSyntaxVar, VariableNumMap rootPredicateVar,
      VariableNumMap rootPosVar) {
    ParametricFactor wordFactor = new DenseIndicatorLogLinearFactor(VariableNumMap.unionAll(
        rootSyntaxVar, rootPredicateVar), true);

    VariableNumMap posVars = VariableNumMap.unionAll(rootSyntaxVar, rootPosVar);
    ParametricFactor posFactor;
    if (usePosFeatures) {
      posFactor = new DenseIndicatorLogLinearFactor(posVars, true);
    } else {
      posFactor = new ConstantParametricFactor(posVars, TableFactor.logUnity(posVars));
    }

    VariableNumMap allVars = VariableNumMap.unionAll(rootSyntaxVar, rootPredicateVar, rootPosVar);
    return new CombiningParametricFactor(allVars, Arrays.asList("root-word",
        "root-pos"), Arrays.asList(wordFactor, posFactor), true);
  }
}