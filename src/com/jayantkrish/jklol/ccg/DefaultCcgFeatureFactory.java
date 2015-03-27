package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.ccg.lexicon.LexiconFeatureGenerator;
import com.jayantkrish.jklol.ccg.lexicon.ParametricCcgLexicon;
import com.jayantkrish.jklol.ccg.lexicon.ParametricFeaturizedLexicon;
import com.jayantkrish.jklol.ccg.lexicon.ParametricTableLexicon;
import com.jayantkrish.jklol.ccg.supertag.TrainSupertagger;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.ConditionalLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.CombiningParametricFactor;
import com.jayantkrish.jklol.models.parametric.ConstantParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.preprocessing.DictionaryFeatureVectorGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.sequence.ListLocalContext;
import com.jayantkrish.jklol.sequence.LocalContext;

/**
 * Creates an indicator feature parameterization for a CCG parser.
 * 
 * @author jayantk
 */
public class DefaultCcgFeatureFactory implements CcgFeatureFactory {

  private final FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator;
  private final boolean usePosFeatures;
  
  /**
   * 
   * @param featureGenerator generates the features to use in
   * predicting the CCG category for terminal spans in the parse. If
   * {@code null}, use word / CCG category and POS / syntactic
   * category features.
   */
  public DefaultCcgFeatureFactory(FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator,
      boolean usePosFeatures) {
    this.featureGenerator = featureGenerator;
    this.usePosFeatures = usePosFeatures;
  }

  public static FeatureVectorGenerator<LocalContext<WordAndPos>> getDefaultFeatureGenerator(
      Collection<CcgExample> examples) {
    List<LocalContext<WordAndPos>> contexts = getContextsFromExamples(examples);
    return TrainSupertagger.buildFeatureVectorGenerator(contexts, null, Integer.MAX_VALUE, 250, 100, false);
  }

  public static FeatureVectorGenerator<LocalContext<WordAndPos>> getPosFeatureGenerator(
      Collection<CcgExample> examples) {
    List<LocalContext<WordAndPos>> contexts = getContextsFromExamples(examples);

    FeatureGenerator<LocalContext<WordAndPos>, String> posGenerator = new LexiconFeatureGenerator();
    FeatureVectorGenerator<LocalContext<WordAndPos>> featureGenerator = DictionaryFeatureVectorGenerator
        .createFromData(contexts, posGenerator, true);
    return featureGenerator;
  }

  private static List<LocalContext<WordAndPos>> getContextsFromExamples(
      Collection<CcgExample> examples) {
    List<LocalContext<WordAndPos>> contexts = Lists.newArrayList();
    for (CcgExample example : examples) {
      List<WordAndPos> wordAndPos = example.getSentence().getWordsAndPosTags();
      for (int i = 0; i < wordAndPos.size(); i++) {
        contexts.add(new ListLocalContext<WordAndPos>(wordAndPos, i));
      }
    }
    return contexts;
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
  public ParametricCcgLexicon getLexiconFeatures(VariableNumMap terminalWordVar,
      VariableNumMap ccgCategoryVar, VariableNumMap terminalPosVar, VariableNumMap terminalSyntaxVar,
      DiscreteFactor lexiconIndicatorFactor) {
    if (featureGenerator == null) {
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
      ParametricFactor terminalPosParametricFactor;
      if (usePosFeatures) {
        terminalPosParametricFactor = new DenseIndicatorLogLinearFactor(terminalPosVars, true);
      } else {
        terminalPosParametricFactor = new ConstantParametricFactor(terminalPosVars, TableFactor.logUnity(terminalPosVars));
      }

      return new ParametricTableLexicon(terminalWordVar, ccgCategoryVar, terminalParametricFactor,
          terminalPosVar, terminalSyntaxVar, terminalPosParametricFactor, terminalSyntaxFactor);
    } else {
      ParametricFactor terminalFamily = new IndicatorLogLinearFactor(terminalWordVar.union(ccgCategoryVar),
          lexiconIndicatorFactor);

      VariableNumMap featureVar = VariableNumMap.singleton(terminalSyntaxVar.getOnlyVariableNum() - 1,
          "ccgLexiconFeatures", featureGenerator.getFeatureDictionary());
      ConditionalLogLinearFactor featureFamily = new ConditionalLogLinearFactor(featureVar, terminalSyntaxVar,
          VariableNumMap.EMPTY, featureGenerator.getFeatureDictionary());

      return new ParametricFeaturizedLexicon(terminalWordVar, ccgCategoryVar, terminalFamily,
          featureGenerator, terminalSyntaxVar, featureVar, featureFamily);
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