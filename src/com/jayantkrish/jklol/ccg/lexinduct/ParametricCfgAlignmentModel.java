package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTree.ExpressionNode;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.SparseCptTableFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;

public class ParametricCfgAlignmentModel implements ParametricFamily<CfgAlignmentModel> {
  private static final long serialVersionUID = 1L;

  private final ParametricFactor terminalFactor;
  private final VariableNumMap terminalVar;
  private final VariableNumMap leftVar;
  private final VariableNumMap rightVar;
  private final VariableNumMap parentVar;
  private final VariableNumMap ruleVar;
  
  private final int nGramLength;
    
  public static final String TERMINAL = "terminal";
  public static final String FORWARD_APPLICATION = "fa";
  public static final String BACKWARD_APPLICATION = "ba";
  public static final String SKIP_RULE = "skip";
  public static final ExpressionNode SKIP_EXPRESSION = new ExpressionNode(Expression2.constant("**skip**"), 0);

  public ParametricCfgAlignmentModel(ParametricFactor terminalFactor, VariableNumMap terminalVar,
      VariableNumMap leftVar, VariableNumMap rightVar, VariableNumMap parentVar,
      VariableNumMap ruleVar, int nGramLength) {
    this.terminalFactor = Preconditions.checkNotNull(terminalFactor);

    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.leftVar = Preconditions.checkNotNull(leftVar);
    this.rightVar = Preconditions.checkNotNull(rightVar);
    this.parentVar = Preconditions.checkNotNull(parentVar);
    this.ruleVar = Preconditions.checkNotNull(ruleVar);
    
    this.nGramLength = nGramLength;
  }
  
  public static ParametricCfgAlignmentModel buildAlignmentModelWithNGrams(Collection<AlignmentExample> examples,
      FeatureVectorGenerator<Expression2> featureVectorGenerator, int nGramLength) {
    Set<List<String>> terminalVarValues = Sets.newHashSet();
    for (AlignmentExample example : examples) {
      terminalVarValues.addAll(example.getNGrams(nGramLength));
    }
    return buildAlignmentModel(examples, featureVectorGenerator, terminalVarValues);
  }
  
  public static ParametricCfgAlignmentModel buildAlignmentModel(Collection<AlignmentExample> examples,
      FeatureVectorGenerator<Expression2> featureVectorGenerator, Set<List<String>> terminalVarValues) {
    Set<ExpressionNode> expressions = Sets.newHashSet();

    System.out.println("num terminals: " + terminalVarValues.size());
    System.out.println(terminalVarValues);

    int nGramLength = 0;
    for (List<String> terminalVarValue : terminalVarValues) {
      nGramLength = Math.max(nGramLength, terminalVarValue.size());
    }

    for (AlignmentExample example : examples) {
      example.getTree().getAllExpressionNodes(expressions);
    }
    expressions.add(SKIP_EXPRESSION);

    DiscreteVariable expressionVarType = new DiscreteVariable("expressions", expressions);
    DiscreteVariable terminalVarType = new DiscreteVariable("words", terminalVarValues);
    DiscreteVariable ruleVarType = new DiscreteVariable("rule", Arrays.asList(TERMINAL,
        FORWARD_APPLICATION, BACKWARD_APPLICATION, SKIP_RULE));

    VariableNumMap terminalVar = VariableNumMap.singleton(0, "terminal", terminalVarType);
    VariableNumMap leftVar = VariableNumMap.singleton(1, "left", expressionVarType);
    VariableNumMap rightVar = VariableNumMap.singleton(2, "right", expressionVarType);
    VariableNumMap parentVar = VariableNumMap.singleton(3, "parent", expressionVarType);
    VariableNumMap ruleVar = VariableNumMap.singleton(4, "rule", ruleVarType);

    DiscreteFactor sparsityFactor = TableFactor.unity(parentVar.union(terminalVar))
        .outerProduct(TableFactor.pointDistribution(ruleVar, ruleVar.outcomeArrayToAssignment(TERMINAL)));
    DiscreteFactor constantFactor = TableFactor.zero(VariableNumMap.unionAll(terminalVar, parentVar, ruleVar));
    SparseCptTableFactor terminalFactor = new SparseCptTableFactor(parentVar.union(ruleVar), terminalVar,
        sparsityFactor, constantFactor);

    /*
    VariableNumMap vars = VariableNumMap.unionAll(parentVar, ruleVar, terminalVar);
    int featureVarNum = Ints.max(vars.getVariableNumsArray()) + 1;
    VariableNumMap featureVar = VariableNumMap.singleton(featureVarNum, "features",
        featureVectorGenerator.getFeatureDictionary());
    DiscreteLogLinearFactor terminalFactor = new DiscreteLogLinearFactor(vars, featureVar,
        featureValues);
        */

    return new ParametricCfgAlignmentModel(terminalFactor, terminalVar, leftVar, rightVar,
        parentVar, ruleVar, nGramLength);
  }
  
  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return terminalFactor.getNewSufficientStatistics();
  }

  @Override
  public CfgAlignmentModel getModelFromParameters(SufficientStatistics parameters) {
    DiscreteFactor f = terminalFactor.getModelFromParameters(parameters).coerceToDiscrete();
    return new CfgAlignmentModel(f, terminalVar, leftVar, rightVar, parentVar, ruleVar, nGramLength);
  }

  public void incrementSufficientStatistics(SufficientStatistics statistics,
      SufficientStatistics currentParameters, CfgParseChart chart, double count) {
    DiscreteFactor terminalExpectations = chart.getTerminalRuleExpectations().coerceToDiscrete();

    Iterator<Outcome> iter = terminalExpectations.outcomeIterator();
    double partitionFunction = chart.getPartitionFunction();
    
    while (iter.hasNext()) {
      Outcome o = iter.next();
      terminalFactor.incrementSufficientStatisticsFromAssignment(statistics, currentParameters,
          o.getAssignment(), o.getProbability() / partitionFunction);

    }
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return terminalFactor.getParameterDescription(parameters);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    return terminalFactor.getParameterDescription(parameters, numFeatures);
  }
}
