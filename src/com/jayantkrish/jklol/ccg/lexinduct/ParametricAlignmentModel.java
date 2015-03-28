package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.LogLinearCptFactor;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.VariableNumPattern;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.Lbfgs;
import com.jayantkrish.jklol.training.NullLogFunction;

/**
 * Model family for a word alignment-based lexicon induction 
 * algorithm. This class implements methods for 
 * creating {@code AlignmentModel} instances from parameter 
 * vectors. 
 * <p>
 * Each instance of an alignment model is a factor graph that
 * aligns words in a sentence with logical forms that are 
 * decompositions of a target logical form. A tree structured
 * constraint on the logical form decompositions selects a
 * subset of these logical forms as active, and enforces that
 * the selected logical forms can be composed to produce the 
 * target. Words are aligned to each of the selected logical
 * forms using a Naive Bayes classifier that generates the
 * logical form's feature vector. 
 *  
 * @author jayant
 *
 */
public class ParametricAlignmentModel implements ParametricFamily<AlignmentModel> {
  private static final long serialVersionUID = 1L;

  private final ParametricFactorGraph pfg;
  private final VariableNumMap expressionPlateVar;
  private final VariableNumMap featurePlateVar;
  private final VariableNumMap wordPlateVar;

  private final VariableNumMap booleanPlateVar;
  
  private final boolean useTreeConstraint;
  
  private static final String PLATE_NAME="expressions";
  private static final String EXPRESSION_VAR_NAME="expression";
  private static final String EXPRESSION_VAR_PATTERN="expressions/?(0)/expression";
  private static final String FEATURE_VAR_NAME="features";
  private static final String FEATURE_VAR_PATTERN="expressions/?(0)/features";
  private static final String WORD_VAR_NAME="word";
  private static final String WORD_VAR_PATTERN="expressions/?(0)/word";

  private static final String BOOLEAN_PLATE_NAME="booleans";
  private static final String BOOLEAN_VAR_NAME="boolean";
  private static final String BOOLEAN_VAR_PATTERN="booleans/?(0)/boolean";
  
  public static final String NULL_WORD = "**null**"; 

  public ParametricAlignmentModel(ParametricFactorGraph pfg, VariableNumMap expressionPlateVar, 
      VariableNumMap featurePlateVar, VariableNumMap wordPlateVar, VariableNumMap booleanPlateVar,
      boolean useTreeConstraint) {
    this.pfg = Preconditions.checkNotNull(pfg);
    this.expressionPlateVar = expressionPlateVar;
    this.featurePlateVar = featurePlateVar;
    this.wordPlateVar = wordPlateVar;
    
    this.booleanPlateVar = booleanPlateVar;
    
    this.useTreeConstraint = useTreeConstraint;
  }

  /**
   * Creates a new family of word alignment models. 
   *   
   * @param examples training data that will be used to
   * train this model. Used to define the space of possible 
   * logical forms and feature vectors.
   * @param useTreeConstraint if {@code true}, use the tree
   * structured logical form decomposition constraint. If
   * {@code false}, every possible decomposition of the target
   * logical form is aligned to a word.
   * @param useWordDistribution if {@code true}, the model includes
   * a factor for learning a distribution over which words are chosen
   * to be aligned.
   * @param featureVectorGenerator function mapping logical
   * forms to feature vectors that are used for the word-logical
   * form alignment.
   * @return
   */
  public static ParametricAlignmentModel buildAlignmentModel(
      Collection<AlignmentExample> examples, boolean useTreeConstraint,
      boolean useWordDistribution, FeatureVectorGenerator<Expression2> featureVectorGenerator) {
    Set<Expression2> allExpressions = Sets.newHashSet();
    Set<String> words = Sets.newHashSet();
    words.add(NULL_WORD);
    for (AlignmentExample example : examples) {
      example.getTree().getAllExpressions(allExpressions);
      words.addAll(example.getWords());
    }

    DiscreteVariable trueFalseVar = new DiscreteVariable("true-false", Arrays.asList("F", "T"));
    DiscreteVariable expressionVar = new DiscreteVariable("expressions", allExpressions);
    ObjectVariable expressionFeatureVar = new ObjectVariable(Tensor.class);
    DiscreteVariable wordVar = new DiscreteVariable("words", Sets.newHashSet(words));
    
    System.out.println("alignment model: " + allExpressions.size() + " expressions, " + words.size() + " words.");
    
    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    // Create another plate that allows us to build the tree of binary variables.
    builder.addPlate(BOOLEAN_PLATE_NAME, new VariableNumMap(Ints.asList(0), 
        Arrays.asList(BOOLEAN_VAR_NAME), Arrays.asList(trueFalseVar)), 100000);
    VariableNumMap booleanPlateVar = VariableNumMap.singleton(0, BOOLEAN_VAR_PATTERN, trueFalseVar);

    // Create a plate for each word / logical form pair.
    builder.addPlate(PLATE_NAME, new VariableNumMap(Ints.asList(0, 1, 2), 
        Arrays.asList(EXPRESSION_VAR_NAME, FEATURE_VAR_NAME, WORD_VAR_NAME),
        Arrays.<Variable>asList(expressionVar, expressionFeatureVar, wordVar)), 100000);
    VariableNumMap pattern = new VariableNumMap(Ints.asList(0, 1, 2), 
        Arrays.asList(EXPRESSION_VAR_PATTERN, FEATURE_VAR_PATTERN, WORD_VAR_PATTERN),
        Arrays.<Variable>asList(expressionVar, expressionFeatureVar, wordVar));
    VariableNumMap expressionVarPattern = pattern.getVariablesByName(EXPRESSION_VAR_PATTERN);
    VariableNumMap featureVarPattern = pattern.getVariablesByName(FEATURE_VAR_PATTERN);
    VariableNumMap wordVarPattern = pattern.getVariablesByName(WORD_VAR_PATTERN);

    // Words generate the feature vector of each expression using a multinomial
    // naive bayes model.  
    ParametricLinearClassifierFactor factor = new ParametricLinearClassifierFactor(featureVarPattern,
        wordVarPattern, VariableNumMap.EMPTY, featureVectorGenerator.getFeatureDictionary(),
        wordVarPattern.outcomeArrayToAssignment(NULL_WORD), true);
    builder.addFactor("word-expression-factor", factor,
        VariableNumPattern.fromTemplateVariables(wordVarPattern.union(featureVarPattern),
            VariableNumMap.EMPTY, builder.getDynamicVariableSet()));

    if (useWordDistribution) {
      // TODO: This distribution is learned as a global distribution
      // over words, but it should be a conditional distribution given
      // the sentence.
      DiscreteLogLinearFactor wordFactor = DiscreteLogLinearFactor.createIndicatorFactor(wordVarPattern);
      LogLinearCptFactor wrapperFactor = new LogLinearCptFactor(wordFactor,
          new Lbfgs(20, 20, 1.0, new NullLogFunction()));
      
      builder.addFactor("word-factor", wrapperFactor,
          VariableNumPattern.fromTemplateVariables(wordVarPattern,
          VariableNumMap.EMPTY, builder.getDynamicVariableSet()));
    }

    ParametricFactorGraph pfg = builder.build();

    return new ParametricAlignmentModel(pfg, expressionVarPattern, featureVarPattern,
        wordVarPattern,  booleanPlateVar, useTreeConstraint);
  }

  /**
   * Returns a copy of this model with the given value for useTreeConstraint.
   * 
   * @param newUseTreeConstraint
   * @return
   */
  public ParametricAlignmentModel updateUseTreeConstraint(boolean newUseTreeConstraint) {
    return new ParametricAlignmentModel(pfg, expressionPlateVar, featurePlateVar, wordPlateVar, 
        booleanPlateVar, newUseTreeConstraint);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return pfg.getNewSufficientStatistics();
  }

  @Override
  public AlignmentModel getModelFromParameters(SufficientStatistics parameters) {
    DynamicFactorGraph fg = pfg.getModelFromParameters(parameters);
    return new AlignmentModel(fg, PLATE_NAME, expressionPlateVar, featurePlateVar,
        wordPlateVar, BOOLEAN_PLATE_NAME, booleanPlateVar, useTreeConstraint);
  }

  public void incrementSufficientStatistics(SufficientStatistics statistics,
      SufficientStatistics currentParameters, MarginalSet marginals, double count) {
    pfg.incrementSufficientStatistics(statistics, currentParameters, marginals, count);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return pfg.getParameterDescription(parameters);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters,
      int numFeatures) {
    return pfg.getParameterDescription(parameters, numFeatures);
  }
}
