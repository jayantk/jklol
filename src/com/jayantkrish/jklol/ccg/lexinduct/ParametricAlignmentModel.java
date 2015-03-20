package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.models.DiscreteVariable;
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
  
  private static final String PLATE_NAME="expressions";
  private static final String WORD_VAR_NAME="word";
  private static final String WORD_VAR_PATTERN="expressions/?(0)/word";
  private static final String EXPRESSION_VAR_NAME="expression";
  private static final String EXPRESSION_VAR_PATTERN="expressions/?(0)/expression";

  public ParametricAlignmentModel(ParametricFactorGraph pfg,
      VariableNumMap wordPlateVar, VariableNumMap expressionPlateVar) {
    this.pfg = Preconditions.checkNotNull(pfg);
    this.wordPlateVar = wordPlateVar;
    this.expressionPlateVar = expressionPlateVar;
  }
  
  public static ParametricAlignmentModel buildAlignmentModel(
      Collection<AlignmentExample> examples) {
    Set<Expression> allExpressions = Sets.newHashSet();
    Set<String> words = Sets.newHashSet();
    for (AlignmentExample example : examples) {
      example.getTree().getAllExpressions(allExpressions);
      words.addAll(example.getWords());
    }

    DiscreteVariable trueFalseVar = new DiscreteVariable("true-false", Arrays.asList("F", "T"));
    DiscreteVariable expressionVar = new DiscreteVariable("expressions", allExpressions);
    DiscreteVariable wordVar = new DiscreteVariable("words", Sets.newHashSet(words));
    
    System.out.println("alignment model: " + allExpressions.size() + " expressions, " + words.size() + " words.");

    ParametricFactorGraphBuilder builder = new ParametricFactorGraphBuilder();
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
      for (String word : example.getWords()) {
        for (Expression expression : expressions) {
          sparsityBuilder.setWeight(1.0, expression, word);
        }
      }
    }
    
    SparseCptTableFactor factor = new SparseCptTableFactor(wordVarPattern,
        expressionVarPattern, sparsityBuilder.build());
    builder.addFactor("word-expression-factor", factor, VariableNumPattern
        .fromTemplateVariables(pattern, VariableNumMap.EMPTY, builder.getDynamicVariableSet()));

    ParametricFactorGraph pfg = builder.build();

    return new ParametricAlignmentModel(pfg, wordVarPattern, expressionVarPattern);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return pfg.getNewSufficientStatistics();
  }

  @Override
  public AlignmentModel getModelFromParameters(SufficientStatistics parameters) {
    DynamicFactorGraph fg = pfg.getModelFromParameters(parameters);
    return new AlignmentModel(fg, PLATE_NAME, wordPlateVar, expressionPlateVar);
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
