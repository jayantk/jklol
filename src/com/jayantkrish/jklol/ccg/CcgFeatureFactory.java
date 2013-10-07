package com.jayantkrish.jklol.ccg;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;

/**
 * Interface for determining the featurization of a CCG parser. CCG
 * parsers have a set of events which can be parameterized, such as the
 * lexicon entries used for a word, or the expected syntactic category
 * given a part-of-speech tag. Typically, these events are
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
   * Gets a parametric factor defining features over the distance between
   * a dependency and its arguments. Distance is measured as the number of
   * intervening words.
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
   * Gets a parametric factor defining features over the distance between
   * a dependency and its arguments. Distance is measured as the number of
   * intervening punctuation marks.
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
   * Gets a parametric factor defining features over the distance between
   * a dependency and its arguments. Distance is measured as the number of
   * intervening verbs (detected using part-of-speech tags).
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
   * Gets a parametric factor defining the features of lexicon entries
   * in a CCG parser. {@code lexiconIndicatorFactor} is an indicator
   * distribution where each outcome with value 1.0 represents a entry
   * in the lexicon.
   * 
   * @param terminalWordVard
   * @param ccgCategoryVar
   * @param lexiconIndicatorFactor
   * @return
   */
  ParametricFactor getLexiconFeatures(VariableNumMap terminalWordVard,
      VariableNumMap ccgCategoryVar, DiscreteFactor lexiconIndicatorFactor);

  /**
   * Gets a parametric factor defining features of words sequences and
   * their syntactic category. These features are backoff features for
   * the lexicon features (see {@link #getLexiconFeatures}) which
   * ignore the semantics of the lexicon entry.
   * 
   * @param terminalWordVar
   * @param terminalSyntaxVar
   * @param lexiconSyntaxIndicatorFactor
   * @return
   */
  ParametricFactor getLexiconSyntaxFeatures(VariableNumMap terminalWordVar,
      VariableNumMap terminalSyntaxVar, DiscreteFactor lexiconSyntaxIndicatorFactor);

}
