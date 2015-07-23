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
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.bayesnet.SparseCptTableFactor;
import com.jayantkrish.jklol.models.parametric.ConstantParametricFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricCfgAlignmentModel implements ParametricFamily<CfgAlignmentModel> {
  private static final long serialVersionUID = 1L;

  private final ParametricFactor ruleFactor;
  private final ParametricFactor nonterminalFactor;
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

  public ParametricCfgAlignmentModel(ParametricFactor ruleFactor, ParametricFactor nonterminalFactor,
      ParametricFactor terminalFactor, VariableNumMap terminalVar, VariableNumMap leftVar, VariableNumMap rightVar,
      VariableNumMap parentVar, VariableNumMap ruleVar, int nGramLength) {
    this.ruleFactor = Preconditions.checkNotNull(ruleFactor);
    this.nonterminalFactor = Preconditions.checkNotNull(nonterminalFactor);
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
    expressions.add(SKIP_EXPRESSION);

    System.out.println("num terminals: " + terminalVarValues.size());
    System.out.println(terminalVarValues);

    int nGramLength = 0;
    for (List<String> terminalVarValue : terminalVarValues) {
      nGramLength = Math.max(nGramLength, terminalVarValue.size());
    }

    for (AlignmentExample example : examples) {
      example.getTree().getAllExpressionNodes(expressions);
    }

    DiscreteVariable expressionVarType = new DiscreteVariable("expressions", expressions);
    DiscreteVariable terminalVarType = new DiscreteVariable("words", terminalVarValues);
    DiscreteVariable ruleVarType = new DiscreteVariable("rule", Arrays.asList(TERMINAL,
        FORWARD_APPLICATION, BACKWARD_APPLICATION, SKIP_RULE));

    VariableNumMap terminalVar = VariableNumMap.singleton(0, "terminal", terminalVarType);
    VariableNumMap leftVar = VariableNumMap.singleton(1, "left", expressionVarType);
    VariableNumMap rightVar = VariableNumMap.singleton(2, "right", expressionVarType);
    VariableNumMap parentVar = VariableNumMap.singleton(3, "parent", expressionVarType);
    VariableNumMap ruleVar = VariableNumMap.singleton(4, "rule", ruleVarType);
    
    // Probability distribution over the different CFG rule types
    // CptTableFactor ruleFactor = new CptTableFactor(parentVar, ruleVar);
    ParametricFactor ruleFactor = new ConstantParametricFactor(parentVar.union(ruleVar),
        TableFactor.unity(parentVar.union(ruleVar)));

    VariableNumMap nonterminalVars = VariableNumMap.unionAll(leftVar, rightVar, parentVar, ruleVar);
    TableFactor ones = TableFactor.logUnity(nonterminalVars);
    TableFactorBuilder nonterminalBuilder = new TableFactorBuilder(nonterminalVars,
        SparseTensorBuilder.getFactory());
    for (AlignmentExample example : examples) {
      example.getTree().populateBinaryRuleDistribution(nonterminalBuilder, ones);
    }

    DiscreteFactor nonterminalSparsityFactor = nonterminalBuilder.build();
    /*
    DiscreteFactor nonterminalConstantFactor = TableFactor.zero(nonterminalVars);
    SparseCptTableFactor nonterminalFactor = new SparseCptTableFactor(parentVar.union(ruleVar),
        leftVar.union(rightVar), nonterminalSparsityFactor, nonterminalConstantFactor);
        */
    ParametricFactor nonterminalFactor = new ConstantParametricFactor(nonterminalVars, nonterminalSparsityFactor);

    DiscreteFactor sparsityFactor = TableFactor.unity(parentVar.union(terminalVar))
        .outerProduct(TableFactor.pointDistribution(ruleVar, ruleVar.outcomeArrayToAssignment(TERMINAL)));
    DiscreteFactor constantFactor = TableFactor.zero(VariableNumMap.unionAll(terminalVar, parentVar, ruleVar));
    // TODO: There should probably be special handling for the SKIP symbol
    // Maximize P(logical form | word)
    /*
    SparseCptTableFactor terminalFactor = new SparseCptTableFactor(terminalVar.union(ruleVar),
        parentVar, sparsityFactor, constantFactor);
        */
    // Maximize P(word | logical form). This works better.
    SparseCptTableFactor terminalFactor = new SparseCptTableFactor(parentVar.union(ruleVar),
        terminalVar, sparsityFactor, constantFactor);

    /*
    CombiningParametricFactor terminalFactor = new CombiningParametricFactor(
        VariableNumMap.unionAll(terminalVar, parentVar, ruleVar),
        Arrays.asList("l_given_w", "w_given_l"),
        Arrays.asList(logicalFormGivenWord, wordGivenLogicalForm), false);
     */
    
    /*
    SparseCptTableFactor terminalFactor = new SparseCptTableFactor(VariableNumMap.EMPTY,
        VariableNumMap.unionAll(parentVar, ruleVar, terminalVar), sparsityFactor, constantFactor);
        */    

    /*
    VariableNumMap vars = VariableNumMap.unionAll(parentVar, ruleVar, terminalVar);
    int featureVarNum = Ints.max(vars.getVariableNumsArray()) + 1;
    VariableNumMap featureVar = VariableNumMap.singleton(featureVarNum, "features",
        featureVectorGenerator.getFeatureDictionary());
    ParametricLinearClassifierFactor terminalFactor = new ParametricLinearClassifierFactor(
        parentVar, terminalVar.union(ruleVar), VariableNumMap.EMPTY,
        featureVectorGenerator.getFeatureDictionary(), null, true);
        */

    return new ParametricCfgAlignmentModel(ruleFactor, nonterminalFactor, terminalFactor,
        terminalVar, leftVar, rightVar, parentVar, ruleVar, nGramLength);
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return new ListSufficientStatistics(Arrays.asList("rules", "nonterminals", "terminals"),
        Arrays.asList(ruleFactor.getNewSufficientStatistics(),
            nonterminalFactor.getNewSufficientStatistics(), terminalFactor.getNewSufficientStatistics()));
  }

  @Override
  public CfgAlignmentModel getModelFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();

    DiscreteFactor rules = ruleFactor.getModelFromParameters(parameterList.get(0)).coerceToDiscrete();
    DiscreteFactor ntf = nonterminalFactor.getModelFromParameters(parameterList.get(1))
        .coerceToDiscrete().product(rules);
    DiscreteFactor tf = terminalFactor.getModelFromParameters(parameterList.get(2))
        .coerceToDiscrete().product(rules);

    return new CfgAlignmentModel(ntf, tf, terminalVar, leftVar, rightVar, parentVar, ruleVar,
        nGramLength);
  }

  public void incrementSufficientStatistics(SufficientStatistics statistics,
      SufficientStatistics currentParameters, CfgParseChart chart, double count) {
    List<SufficientStatistics> statisticsList = statistics.coerceToList().getStatistics();
    List<SufficientStatistics> parameterList = currentParameters.coerceToList().getStatistics();
    
    DiscreteFactor terminalExpectations = chart.getTerminalRuleExpectations().coerceToDiscrete();
    Iterator<Outcome> iter = terminalExpectations.outcomeIterator();
    double partitionFunction = chart.getPartitionFunction();
    while (iter.hasNext()) {
      Outcome o = iter.next();
      Assignment a = o.getAssignment();
      double amount = count * o.getProbability() / partitionFunction;

      ruleFactor.incrementSufficientStatisticsFromAssignment(statisticsList.get(0),
          parameterList.get(0), a.intersection(ruleFactor.getVars().getVariableNumsArray()), amount);

      List<?> words = (List<?>) a.getValue(terminalVar.getOnlyVariableNum());
      Assignment remainder = a.removeAll(terminalVar.getOnlyVariableNum());
      for (Object word : words) {
        Assignment toIncrement = remainder.union(terminalVar.outcomeArrayToAssignment(Arrays.asList(word)));
        terminalFactor.incrementSufficientStatisticsFromAssignment(statisticsList.get(2),
          parameterList.get(2), toIncrement, amount);
      }
    }

    DiscreteFactor nonterminalExpectations = chart.getBinaryRuleExpectations().coerceToDiscrete();
    iter = nonterminalExpectations.outcomeIterator();
    while (iter.hasNext()) {
      Outcome o = iter.next();
      Assignment a = o.getAssignment();
      double amount = count * o.getProbability() / partitionFunction;
      nonterminalFactor.incrementSufficientStatisticsFromAssignment(statisticsList.get(1),
          parameterList.get(1), a, amount);
      ruleFactor.incrementSufficientStatisticsFromAssignment(statisticsList.get(0),
          parameterList.get(0), a.intersection(ruleFactor.getVars().getVariableNumsArray()), amount);
    }
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();

    StringBuilder sb = new StringBuilder();
    sb.append("rules:");
    sb.append(ruleFactor.getParameterDescription(parameterList.get(0), numFeatures));
    sb.append("nonterminals:");
    sb.append(nonterminalFactor.getParameterDescription(parameterList.get(1), numFeatures));
    sb.append("terminals:");
    sb.append(terminalFactor.getParameterDescription(parameterList.get(2), numFeatures));
    return sb.toString();
  }
}
