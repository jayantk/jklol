package com.jayantkrish.jklol.ccg;

import com.jayantkrish.jklol.ccg.lexicon.ParametricCcgLexicon;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;

/**
 * Interface for determining the featurization of a CCG parser. CCG
 * parsers have a set of events which can be parameterized, such as
 * the lexicon entries used for a word, or the expected syntactic
 * category given a part-of-speech tag. Typically, these events are
 * parameterized using indicator features; however, other
 * parameterizations are also possible, e.g., to capture commonalities
 * between a single word's syntactic behavior across different
 * semantic realizations. This interface enables the realization of
 * these other parameterizations.
 * <p>
 * To obtain the default indicator parameterization, see
 * {@link DefaultCcgFeatureFactory}.
 * 
 * @author jayantk
 */
public interface CcgFeatureFactory {

  /**
   * Gets a parametric factor defining the features of CCG dependency
   * structures. Given parameters, the returned parametric factor
   * produces a distribution over the six argument variables. This
   * distribution is also a distribution over CCG dependency
   * structures.
   * 
   * @param dependencyHeadVar
   * @param headSyntaxVar
   * @param dependencyArgNumVar
   * @param dependencyArgVar
   * @param dependencyHeadPosVar
   * @param dependencyArgPosVar
   * @return
   */
  ParametricFactor getDependencyFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar, VariableNumMap dependencyArgVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap dependencyArgPosVar);

  /**
   * Gets a parametric factor defining features over the distance
   * between a dependency and its arguments. Distance is measured as
   * the number of intervening words.
   * 
   * @param dependencyHeadVar
   * @param headSyntaxVar
   * @param dependencyArgNumVar
   * @param dependencyHeadPosVar
   * @param wordDistanceVar
   * @return
   */
  ParametricFactor getDependencyWordDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap wordDistanceVar);

  /**
   * Gets a parametric factor defining features over the distance
   * between a dependency and its arguments. Distance is measured as
   * the number of intervening punctuation marks.
   * 
   * @param dependencyHeadVar
   * @param headSyntaxVar
   * @param dependencyArgNumVar
   * @param dependencyHeadPosVar
   * @param puncDistanceVar
   * @return
   */
  ParametricFactor getDependencyPuncDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap puncDistanceVar);

  /**
   * Gets a parametric factor defining features over the distance
   * between a dependency and its arguments. Distance is measured as
   * the number of intervening verbs (detected using part-of-speech
   * tags).
   * 
   * @param dependencyHeadVar
   * @param headSyntaxVar
   * @param dependencyArgNumVar
   * @param dependencyHeadPosVar
   * @param verbDistanceVar
   * @return
   */
  ParametricFactor getDependencyVerbDistanceFeatures(VariableNumMap dependencyHeadVar,
      VariableNumMap headSyntaxVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyHeadPosVar, VariableNumMap verbDistanceVar);

  /**
   * Gets a parameterization for the lexicon of a CCG parser. This
   * parameterization controls the weights assigned to CCG categories
   * for terminals in the parse tree, given a sentence. The
   * parameterization may include features on e.g., the word itself and
   * its part-of-speech tag.
   * 
   * @param terminalWordVar
   * @param ccgCategoryVar
   * @param terminalPosVar
   * @param terminalSyntaxVar
   * @param lexiconIndicatorFactor an indicator distribution where
   * each outcome with value 1.0 represents a entry in the lexicon.
   * @return
   */
  ParametricCcgLexicon getLexiconFeatures(VariableNumMap terminalWordVar,
      VariableNumMap ccgCategoryVar, VariableNumMap terminalPosVar,
      VariableNumMap terminalSyntaxVar, DiscreteFactor lexiconIndicatorFactor);

  /**
   * Gets features over CCG binary rules. {@code binaryRuleDistribution} is the
   * set of binary rules included in the parser.
   * 
   * @param leftSyntaxVar
   * @param rightSyntaxVar
   * @param parentSyntaxVar
   * @param binaryRuleDistribution
   * @return
   */
  ParametricFactor getBinaryRuleFeatures(VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar, DiscreteFactor binaryRuleDistribution);

  /**
   * Gets features over CCG unary rules. {@code unaryRuleDistribution} is the
   * set of unary rules included in the parser.
   * 
   * @param unaryRuleSyntaxVar
   * @param unaryRuleVar
   * @param unaryRuleDistribution
   * @return
   */
  ParametricFactor getUnaryRuleFeatures(VariableNumMap unaryRuleSyntaxVar,
      VariableNumMap unaryRuleVar, DiscreteFactor unaryRuleDistribution);

  /**
   * Gets a parametric factor defining features over binary
   * combinators along with their semantic heads and parts-of-speech.
   * This distribution should not include features which depend only
   * on the syntactic variables -- such features should be created by 
   * {@link #getBinaryRuleFeatures}.
   * 
   * @param leftSyntaxVar
   * @param rightSyntaxVar
   * @param parentSyntaxVar
   * @param headedBinaryRulePredicateVar
   * @param headedBinaryRulePosVar
   * @return
   */
  ParametricFactor getHeadedBinaryRuleFeatures(VariableNumMap leftSyntaxVar,
      VariableNumMap rightSyntaxVar, VariableNumMap parentSyntaxVar,
      VariableNumMap headedBinaryRulePredicateVar, VariableNumMap headedBinaryRulePosVar);

  /**
   * Gets a parametric factor defining features over the root
   * syntactic category of the parse tree along with its semantic head
   * and part-of-speech tag. This factor should not include features
   * which depend solely on the syntactic category -- such features
   * are included in the parser by default.
   * 
   * @param rootSyntaxVar
   * @param rootPredicateVar
   * @param rootPosVar
   * @return
   */
  ParametricFactor getHeadedRootFeatures(VariableNumMap rootSyntaxVar,
      VariableNumMap rootPredicateVar, VariableNumMap rootPosVar);
}