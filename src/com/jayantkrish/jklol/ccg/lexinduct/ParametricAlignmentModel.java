package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.SparseCptTableFactor;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.models.dynamic.VariableNumPattern;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;

public class ParametricAlignmentModel implements ParametricFamily<AlignmentModel> {
  private static final long serialVersionUID = 1L;

  private final ParametricFactorGraph pfg;
  private final VariableNumMap wordPlateVar;
  private final VariableNumMap expressionPlateVar;
  private final VariableNumMap booleanPlateVar;
  
  private final boolean useTreeConstraint;
  
  private static final String PLATE_NAME="expressions";
  private static final String WORD_VAR_NAME="word";
  private static final String WORD_VAR_PATTERN="expressions/?(0)/word";
  private static final String EXPRESSION_VAR_NAME="expression";
  private static final String EXPRESSION_VAR_PATTERN="expressions/?(0)/expression";

  private static final String BOOLEAN_PLATE_NAME="booleans";
  private static final String BOOLEAN_VAR_NAME="boolean";
  private static final String BOOLEAN_VAR_PATTERN="booleans/?(0)/boolean";
  
  public static final String NULL_WORD = "**null**"; 

  public ParametricAlignmentModel(ParametricFactorGraph pfg, VariableNumMap wordPlateVar,
      VariableNumMap expressionPlateVar, VariableNumMap booleanPlateVar, boolean useTreeConstraint) {
    this.pfg = Preconditions.checkNotNull(pfg);
    this.wordPlateVar = wordPlateVar;
    this.expressionPlateVar = expressionPlateVar;
    this.booleanPlateVar = booleanPlateVar;
    
    this.useTreeConstraint = useTreeConstraint;
  }

  public static ParametricAlignmentModel buildAlignmentModel(
      Collection<AlignmentExample> examples, boolean useTreeConstraint) {
    Set<Expression> allExpressions = Sets.newHashSet();
    Set<String> words = Sets.newHashSet();
    words.add(NULL_WORD);
    for (AlignmentExample example : examples) {
      example.getTree().getAllExpressions(allExpressions);
      words.addAll(example.getWords());
    }

    DiscreteVariable trueFalseVar = new DiscreteVariable("true-false", Arrays.asList("F", "T"));
    DiscreteVariable expressionVar = new DiscreteVariable("expressions", allExpressions);
    DiscreteVariable wordVar = new DiscreteVariable("words", Sets.newHashSet(words));
    
    System.out.println("alignment model: " + allExpressions.size() + " expressions, " + words.size() + " words.");

    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
    // Create another plate that allows us to build the tree of binary variables.
    builder.addPlate(BOOLEAN_PLATE_NAME, new VariableNumMap(Ints.asList(0), 
        Arrays.asList(BOOLEAN_VAR_NAME), Arrays.asList(trueFalseVar)), 10000);
    VariableNumMap booleanPlateVar = VariableNumMap.singleton(0, BOOLEAN_VAR_PATTERN, trueFalseVar);
    
    // Create a plate for each word / logical form pair.
    builder.addPlate(PLATE_NAME, new VariableNumMap(Ints.asList(1, 0), 
        Arrays.asList(WORD_VAR_NAME, EXPRESSION_VAR_NAME), Arrays.asList(wordVar, expressionVar)), 10000);
    VariableNumMap pattern = new VariableNumMap(Ints.asList(1, 0), 
        Arrays.asList(WORD_VAR_PATTERN, EXPRESSION_VAR_PATTERN), Arrays.asList(wordVar, expressionVar));
    VariableNumMap wordVarPattern = pattern.getVariablesByName(WORD_VAR_PATTERN);
    VariableNumMap expressionVarPattern = pattern.getVariablesByName(EXPRESSION_VAR_PATTERN);
    
    TableFactorBuilder sparsityBuilder = new TableFactorBuilder(wordVarPattern.union(expressionVarPattern), 
        SparseTensorBuilder.getFactory());
    Set<Expression> expressions = Sets.newHashSet();
    for (AlignmentExample example : examples) {
      expressions.clear();
      example.getTree().getAllExpressions(expressions);
      for (Expression expression : expressions) {
        for (String word : example.getWords()) {
          sparsityBuilder.setWeight(1.0, expression, word);
        }
      }
    }

    TableFactor sparsityFactor = sparsityBuilder.build();
    DiscreteFactor constantFactor = TableFactor.unity(expressionVarPattern)
        .outerProduct(TableFactor.pointDistribution(wordVarPattern,
            wordVarPattern.outcomeArrayToAssignment(NULL_WORD)))
            .product(1.0 / allExpressions.size());
    
    SparseCptTableFactor factor = new SparseCptTableFactor(wordVarPattern,
        expressionVarPattern, sparsityFactor, constantFactor);
    builder.addFactor("word-expression-factor", factor, VariableNumPattern
        .fromTemplateVariables(pattern, VariableNumMap.EMPTY, builder.getDynamicVariableSet()));

    ParametricFactorGraph pfg = builder.build();

    return new ParametricAlignmentModel(pfg, wordVarPattern, expressionVarPattern, booleanPlateVar,
        useTreeConstraint);
  }

  /**
   * Returns a copy of this model with the given value for useTreeConstraint.
   * 
   * @param newUseTreeConstraint
   * @return
   */
  public ParametricAlignmentModel updateUseTreeConstraint(boolean newUseTreeConstraint) {
    return new ParametricAlignmentModel(pfg, wordPlateVar, expressionPlateVar,
        booleanPlateVar, newUseTreeConstraint);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return pfg.getNewSufficientStatistics();
  }

  @Override
  public AlignmentModel getModelFromParameters(SufficientStatistics parameters) {
    DynamicFactorGraph fg = pfg.getModelFromParameters(parameters);
    return new AlignmentModel(fg, PLATE_NAME, wordPlateVar, expressionPlateVar, 
        BOOLEAN_PLATE_NAME, booleanPlateVar, useTreeConstraint);
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
