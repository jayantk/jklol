package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTree.ExpressionNode;
import com.jayantkrish.jklol.cfg.CfgExpectation;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.cfg.CfgParseTree;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.bayesnet.SparseCptTableFactor;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ConstantParametricFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;

public class ParametricCfgAlignmentModel implements ParametricFamily<CfgAlignmentModel> {
  private static final long serialVersionUID = 1L;

  private final ParametricFactor rootFactor;
  private final ParametricFactor ruleFactor;
  private final ParametricFactor nonterminalFactor;
  private final ParametricFactor terminalFactor;
  private final VariableNumMap terminalVar;
  private final VariableNumMap leftVar;
  private final VariableNumMap rightVar;
  private final VariableNumMap parentVar;
  private final VariableNumMap ruleVar;
  
  private final int nGramLength;
  private final boolean loglinear;

  public static final String TERMINAL = "terminal";
  public static final String APPLICATION = "app";
  public static final String SKIP_LEFT = "skip_left";
  public static final String SKIP_RIGHT = "skip_right";
  public static final String SKIP_CONSTANT = "**skip**";
  public static final ExpressionNode SKIP_EXPRESSION = new ExpressionNode(Expression2.constant(SKIP_CONSTANT),
      Type.createAtomic(SKIP_CONSTANT), 0);

  public ParametricCfgAlignmentModel(ParametricFactor rootFactor, ParametricFactor ruleFactor,
      ParametricFactor nonterminalFactor, ParametricFactor terminalFactor,
      VariableNumMap terminalVar, VariableNumMap leftVar, VariableNumMap rightVar,
      VariableNumMap parentVar, VariableNumMap ruleVar, int nGramLength, boolean loglinear) {
    this.rootFactor = Preconditions.checkNotNull(rootFactor);
    this.ruleFactor = Preconditions.checkNotNull(ruleFactor);
    this.nonterminalFactor = Preconditions.checkNotNull(nonterminalFactor);
    this.terminalFactor = Preconditions.checkNotNull(terminalFactor);

    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.leftVar = Preconditions.checkNotNull(leftVar);
    this.rightVar = Preconditions.checkNotNull(rightVar);
    this.parentVar = Preconditions.checkNotNull(parentVar);
    this.ruleVar = Preconditions.checkNotNull(ruleVar);
    
    this.nGramLength = nGramLength;
    this.loglinear = loglinear;
  }

  public static ParametricCfgAlignmentModel buildAlignmentModelWithNGrams(
      Collection<AlignmentExample> examples, int nGramLength, TypeDeclaration typeDeclaration,
      boolean loglinear) {
    Set<List<String>> terminalVarValues = Sets.newHashSet();
    for (AlignmentExample example : examples) {
      terminalVarValues.addAll(example.getNGrams(nGramLength));
    }
    return buildAlignmentModel(examples, terminalVarValues, typeDeclaration, loglinear);
  }

  public static ParametricCfgAlignmentModel buildAlignmentModel(Collection<AlignmentExample> examples,
      Set<List<String>> terminalVarValues, TypeDeclaration typeDeclaration, boolean loglinear) {
    Set<ExpressionNode> expressions = Sets.newHashSet();
    expressions.add(SKIP_EXPRESSION);

    int nGramLength = 0;
    for (List<String> terminalVarValue : terminalVarValues) {
      nGramLength = Math.max(nGramLength, terminalVarValue.size());
    }

    for (AlignmentExample example : examples) {
      example.getTree().getAllExpressionNodes(expressions);
    }

    Set<Type> types = Sets.newHashSet();
    for (ExpressionNode expression : expressions) {
      types.add(expression.getType());
    }
    
    DiscreteVariable expressionVarType = new DiscreteVariable("expressions", expressions);
    DiscreteVariable typeVarType = new DiscreteVariable("types", types); 
    DiscreteVariable terminalVarType = new DiscreteVariable("words", terminalVarValues);
    DiscreteVariable ruleVarType = new DiscreteVariable("rule", Arrays.asList(TERMINAL,
        APPLICATION, SKIP_LEFT, SKIP_RIGHT));

    VariableNumMap terminalVar = VariableNumMap.singleton(0, "terminal", terminalVarType);
    VariableNumMap leftVar = VariableNumMap.singleton(3, "left", expressionVarType);
    VariableNumMap rightVar = VariableNumMap.singleton(6, "right", expressionVarType);
    VariableNumMap parentVar = VariableNumMap.singleton(9, "parent", expressionVarType);
    VariableNumMap ruleVar = VariableNumMap.singleton(12, "rule", ruleVarType);
    
    VariableNumMap nonterminalVars = VariableNumMap.unionAll(leftVar, rightVar, parentVar, ruleVar);
    TableFactor ones = TableFactor.logUnity(nonterminalVars);
    TableFactorBuilder nonterminalBuilder = new TableFactorBuilder(nonterminalVars,
        SparseTensorBuilder.getFactory());
    for (AlignmentExample example : examples) {
      example.getTree().populateBinaryRuleDistribution(nonterminalBuilder, ones);
    }
    
    VariableNumMap terminalVars = VariableNumMap.unionAll(parentVar, terminalVar, ruleVar);
    TableFactorBuilder terminalBuilder = new TableFactorBuilder(terminalVars,
        SparseTensorBuilder.getFactory());
    Set<ExpressionNode> exampleNodes = Sets.newHashSet();
    for (AlignmentExample example : examples) {
      exampleNodes.clear();
      exampleNodes.add(SKIP_EXPRESSION);
      example.getTree().getAllExpressionNodes(exampleNodes);

      List<String> words = example.getWords();
      for (int i = 0; i < words.size(); i++) {
        for (int j = i; j < Math.min(i + nGramLength, words.size()); j++) {
          List<String> terminals = words.subList(i, j + 1);
          if (terminalVar.isValidOutcomeArray(terminals)) {
            for (ExpressionNode node : exampleNodes) {
              terminalBuilder.setWeight(1.0, terminals, node, TERMINAL);
            }
          }
        }
      }
    }

    DiscreteFactor nonterminalSparsityFactor = nonterminalBuilder.build();
    DiscreteFactor nonterminalConstantFactor = TableFactor.zero(nonterminalVars);
    DiscreteFactor sparsityFactor = terminalBuilder.build();
    DiscreteFactor constantFactor = TableFactor.zero(VariableNumMap.unionAll(terminalVar, parentVar, ruleVar));
    
    System.out.println("Grammar statistics:");
    System.out.println("  # of nonterminal production rules: " + nonterminalSparsityFactor.size());
    System.out.println("  # of terminal production rules: " + sparsityFactor.size());

    ParametricFactor rootFactor = null;
    ParametricFactor ruleFactor = null;
    ParametricFactor nonterminalFactor = null;
    ParametricFactor terminalFactor = null;
    // Maximize P(word | logical form). This works better.
    if (loglinear) {
      ParametricFactor ruleIndicatorFactor = IndicatorLogLinearFactor.createDenseFactor(parentVar.union(ruleVar));
      ParametricFactor nonterminalIndicatorFactor = new IndicatorLogLinearFactor(nonterminalVars, nonterminalSparsityFactor);
      ParametricFactor terminalIndicatorFactor = new IndicatorLogLinearFactor(sparsityFactor.getVars(), sparsityFactor);

      // Assign all binary rules probability 1
      // nonterminalFactor = new ConstantParametricFactor(nonterminalVars, nonterminalSparsityFactor);
      // Constant probability of invoking a rule or not.
      // ruleFactor = new ConstantParametricFactor(parentVar.union(ruleVar),
      // TableFactor.unity(parentVar.union(ruleVar)));

      ParametricFactor ruleFeatureFactor = DiscreteLogLinearFactor.fromFeatureGeneratorSparse(
          TableFactor.unity(parentVar.union(ruleVar)), new RuleFeatureGen(typeDeclaration));
      DiscreteLogLinearFactor nonterminalFeatureFactor = DiscreteLogLinearFactor.fromFeatureGeneratorSparse(
          nonterminalSparsityFactor, new NonterminalFeatureGen(typeDeclaration));
      ParametricFactor terminalFeatureFactor = DiscreteLogLinearFactor.fromFeatureGeneratorSparse(
          sparsityFactor, new TerminalFeatureGen());

      // Print features:
      Factor f = nonterminalFeatureFactor.getFeatureValues();
      VariableNumMap allVars = f.getVars();
      VariableNumMap featureVar = allVars.intersection(allVars.getVariableByName(DiscreteLogLinearFactor.FEATURE_VAR_NAME));
      VariableNumMap otherVars = allVars.removeAll(featureVar);

      VariableRelabeling relabeling = VariableRelabeling.identity(otherVars).union(
          VariableRelabeling.createFromVariables(featureVar, featureVar.relabelVariableNums(new int[] {0})));
      f = f.relabelVariables(relabeling);

      // List<String> factorNames = Arrays.asList("indicators", "features");
      // ruleFactor = new CombiningParametricFactor(ruleFeatureFactor.getVars(), factorNames,
      // Arrays.asList(ruleIndicatorFactor, ruleFeatureFactor), false);
      // nonterminalFactor = new CombiningParametricFactor(nonterminalFeatureFactor.getVars(), factorNames,
      // Arrays.asList(nonterminalIndicatorFactor, nonterminalFeatureFactor), false);
      // terminalFactor = new CombiningParametricFactor(terminalFeatureFactor.getVars(), factorNames,
      // Arrays.asList(terminalIndicatorFactor, terminalFeatureFactor), false);

      rootFactor = new ConstantParametricFactor(parentVar, TableFactor.unity(parentVar));
      ruleFactor = ruleFeatureFactor;
      nonterminalFactor = nonterminalFeatureFactor;
      terminalFactor = terminalFeatureFactor;
    } else {
      // Uniform distribution over root symbols
      rootFactor = new ConstantParametricFactor(parentVar, TableFactor.unity(parentVar));
      // Assign all binary rules probability 1
      nonterminalFactor = new ConstantParametricFactor(nonterminalVars, nonterminalSparsityFactor);
      // Constant probability of invoking a rule or not.
      ruleFactor = new ConstantParametricFactor(parentVar.union(ruleVar),
          TableFactor.unity(parentVar.union(ruleVar)));

      /*
        ruleFactor = new CptTableFactor(parentVar, ruleVar);
        nonterminalFactor = new SparseCptTableFactor(parentVar.union(ruleVar),
            leftVar.union(rightVar), nonterminalSparsityFactor, nonterminalConstantFactor);
       */
      terminalFactor = new SparseCptTableFactor(parentVar.union(ruleVar),
          terminalVar, sparsityFactor, constantFactor);
    }

    return new ParametricCfgAlignmentModel(rootFactor, ruleFactor, nonterminalFactor, terminalFactor,
        terminalVar, leftVar, rightVar, parentVar, ruleVar, nGramLength, loglinear);
  }
  
  public boolean isLoglinear() {
    return loglinear;
  }

  public VariableNumMap getNonterminalVar() {
    return parentVar;
  }
  
  public VariableNumMap getRuleVar() {
    return ruleVar;
  }

  public VariableNumMap getTerminalVar() {
    return terminalVar;
  }

  public ParametricFactor getRootFactor() {
    return rootFactor;
  }

  public ParametricFactor getRuleFactor() {
    return ruleFactor;
  }
  
  public ParametricFactor getNonterminalFactor() {
    return nonterminalFactor;
  }

  public ParametricFactor getTerminalFactor() {
    return terminalFactor;
  }

  @Override
  public SufficientStatistics getNewSufficientStatistics() {
    return new ListSufficientStatistics(Arrays.asList("root", "rules", "nonterminals", "terminals"),
        Arrays.asList(rootFactor.getNewSufficientStatistics(), ruleFactor.getNewSufficientStatistics(),
            nonterminalFactor.getNewSufficientStatistics(), terminalFactor.getNewSufficientStatistics()));
  }

  @Override
  public CfgAlignmentModel getModelFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();

    DiscreteFactor root = rootFactor.getModelFromParameters(parameterList.get(0))
        .coerceToDiscrete();
    DiscreteFactor rules = ruleFactor.getModelFromParameters(parameterList.get(1))
        .coerceToDiscrete();
    DiscreteFactor ntf = nonterminalFactor.getModelFromParameters(parameterList.get(2))
        .coerceToDiscrete();
    DiscreteFactor tf = terminalFactor.getModelFromParameters(parameterList.get(3))
        .coerceToDiscrete();

    if (loglinear) {
      // Normalize each distribution.
      root = root.product(root.marginalize(parentVar).inverse());
      rules = rules.product(rules.marginalize(ruleVar).inverse());
      ntf = ntf.product(ntf.marginalize(ntf.getVars().removeAll(ruleVar.union(parentVar))).inverse());
      tf = tf.product(tf.marginalize(tf.getVars().removeAll(ruleVar.union(parentVar))).inverse());
    }
    
    // Incorporate the conditional probabilities of choosing terminals vs. nonterminals.
    ntf = ntf.product(rules);
    tf = tf.product(rules);
    
    return new CfgAlignmentModel(root, ntf, tf, terminalVar, leftVar, rightVar, parentVar, ruleVar,
        nGramLength);
  }
  
  public void incrementExpectations(CfgExpectation expectations, DiscreteFactor rootExpectations,
      DiscreteFactor nonterminalExpectations, DiscreteFactor terminalExpectations,
      double count, double partitionFunction) {
    TableFactorBuilder rootBuilder = expectations.getRootBuilder();
    TableFactorBuilder ruleBuilder = expectations.getRuleBuilder();
    TableFactorBuilder terminalBuilder = expectations.getTerminalBuilder();
    TableFactorBuilder nonterminalBuilder = expectations.getNonterminalBuilder();
    
    Iterator<Outcome> iter = rootExpectations.outcomeIterator();
    while (iter.hasNext()) {
      Outcome o = iter.next();
      Assignment a = o.getAssignment();
      double amount = count * o.getProbability() / partitionFunction;
      rootBuilder.incrementWeight(a, amount);
    }
    
    iter = terminalExpectations.outcomeIterator();
    while (iter.hasNext()) {
      Outcome o = iter.next();
      Assignment a = o.getAssignment();
      double amount = count * o.getProbability() / partitionFunction;

      ruleBuilder.incrementWeight(a.intersection(ruleFactor.getVars().getVariableNumsArray()), amount);
      terminalBuilder.incrementWeight(a, amount);
    }

    iter = nonterminalExpectations.outcomeIterator();
    while (iter.hasNext()) {
      Outcome o = iter.next();
      Assignment a = o.getAssignment();
      double amount = count * o.getProbability() / partitionFunction;
      
      ruleBuilder.incrementWeight(a.intersection(ruleFactor.getVars().getVariableNumsArray()), amount);
      nonterminalBuilder.incrementWeight(a, amount);
    }
  }
  
  public void incrementExpectations(CfgExpectation expectations, CfgParseChart chart, double count) {
    incrementExpectations(expectations, chart.getMarginalEntriesRoot().coerceToDiscrete(),
        chart.getBinaryRuleExpectations().coerceToDiscrete(),
        chart.getTerminalRuleExpectations().coerceToDiscrete(), count, chart.getPartitionFunction());
  }
  
  public void incrementExpectations(CfgExpectation expectations, CfgParseTree tree, double count) {
    TableFactorBuilder rootBuilder = expectations.getRootBuilder();
    rootBuilder.incrementWeight(parentVar.outcomeArrayToAssignment(tree.getRoot()), count);
    incrementExpectationsHelper(expectations, tree, count);
  }

  private void incrementExpectationsHelper(CfgExpectation expectations, CfgParseTree tree, double count) {
    TableFactorBuilder ruleBuilder = expectations.getRuleBuilder();
    TableFactorBuilder terminalBuilder = expectations.getTerminalBuilder();
    TableFactorBuilder nonterminalBuilder = expectations.getNonterminalBuilder();
    
    if (tree.isTerminal()) {
      Object root = tree.getRoot();
      Object rule = tree.getRuleType();
      Assignment rootAndRule = parentVar.outcomeArrayToAssignment(root).union(
          ruleVar.outcomeArrayToAssignment(rule));
      
      ruleBuilder.incrementWeight(rootAndRule.intersection(ruleFactor.getVars().getVariableNumsArray()), count);
      
      Assignment a = terminalVar.outcomeArrayToAssignment(tree.getTerminalProductions()).union(rootAndRule);
      terminalBuilder.incrementWeight(a, count);
    } else {
      Object root = tree.getRoot();
      Object left = tree.getLeft().getRoot();
      Object right = tree.getRight().getRoot();
      Object rule = tree.getRuleType();

      Assignment a = Assignment.unionAll(parentVar.outcomeArrayToAssignment(root),
          leftVar.outcomeArrayToAssignment(left), rightVar.outcomeArrayToAssignment(right),
          ruleVar.outcomeArrayToAssignment(rule));
      
      ruleBuilder.incrementWeight(a.intersection(ruleFactor.getVars().getVariableNumsArray()), count);
      nonterminalBuilder.incrementWeight(a, count);
      
      incrementExpectations(expectations, tree.getLeft(), count);
      incrementExpectations(expectations, tree.getRight(), count);
    }
  }
  
  public void incrementSufficientStatistics(CfgExpectation expectations,
      SufficientStatistics statistics, SufficientStatistics parameters, double count) {
    List<SufficientStatistics> statisticList = statistics.coerceToList().getStatistics();
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();
    
    rootFactor.incrementSufficientStatisticsFromMarginal(statisticList.get(0),
        parameterList.get(0), expectations.getRootBuilder().build(), Assignment.EMPTY, 1, 1.0);
    ruleFactor.incrementSufficientStatisticsFromMarginal(statisticList.get(1),
        parameterList.get(1), expectations.getRuleBuilder().build(), Assignment.EMPTY, 1, 1.0);
    nonterminalFactor.incrementSufficientStatisticsFromMarginal(statisticList.get(2),
        parameterList.get(2), expectations.getNonterminalBuilder().build(), Assignment.EMPTY, 1, 1.0);
    terminalFactor.incrementSufficientStatisticsFromMarginal(statisticList.get(3),
        parameterList.get(3), expectations.getTerminalBuilder().build(), Assignment.EMPTY, 1, 1.0);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();

    StringBuilder sb = new StringBuilder();
    sb.append("root:\n");
    sb.append(rootFactor.getParameterDescription(parameterList.get(0), numFeatures));
    sb.append("rules:\n");
    sb.append(ruleFactor.getParameterDescription(parameterList.get(1), numFeatures));
    sb.append("nonterminals:\n");
    sb.append(nonterminalFactor.getParameterDescription(parameterList.get(2), numFeatures));
    sb.append("terminals:\n");
    sb.append(terminalFactor.getParameterDescription(parameterList.get(3), numFeatures));
    return sb.toString();
  }
  
  private static class NonterminalFeatureGen implements FeatureGenerator<Assignment, String> {
    private static final long serialVersionUID = 2L;
    
    private final TypeDeclaration typeDeclaration;
    
    public NonterminalFeatureGen(TypeDeclaration typeDeclaration) {
      this.typeDeclaration = Preconditions.checkNotNull(typeDeclaration);
    }

    @Override
    public Map<String, Double> generateFeatures(Assignment item) {
      List<Object> values = item.getValues();
      ExpressionNode left = (ExpressionNode) values.get(0);
      ExpressionNode right = (ExpressionNode) values.get(1);
      ExpressionNode root = (ExpressionNode) values.get(2);
      Object rule = values.get(3);
      String leftSyntax = left.getNumAppliedArguments() + ":" + left.getType();
      String rightSyntax = right.getNumAppliedArguments() + ":" + right.getType();
      String rootSyntax = root.getNumAppliedArguments() + ":" + root.getType();
      
      ExpressionNode func = null;
      if (values.get(3).equals(APPLICATION)) {
        if (right.getNumAppliedArguments() == 0) {
          func = (ExpressionNode) values.get(0);
        } else if (left.getNumAppliedArguments() == 0) {
          func = (ExpressionNode) values.get(1);
        }
      }

      Map<String, Double> featureVals = Maps.newHashMap();
      String baseFeatureName = rootSyntax + " -" + rule + "-> " + leftSyntax + " " + rightSyntax;
      featureVals.put(baseFeatureName, 1.0);
      
      if (func != null) {
        for (int i = 0; i < 2; i++) {
          Expression2 rootTemplate = root.getExpressionTemplate(typeDeclaration, i);
          Expression2 funcTemplate = func.getExpressionTemplate(typeDeclaration, i);
          String featureName = baseFeatureName + " + " + rootTemplate + " -> " + funcTemplate;
          featureVals.put(featureName, 1.0);
        }
        
        // Backoff features for the generated syntactic types.
        featureVals.put("generated=" + leftSyntax, 1.0);
        featureVals.put("generated=" + rightSyntax, 1.0);
      }
      return featureVals;
    }
  }
  
  private static class RuleFeatureGen implements FeatureGenerator<Assignment, String> {
    private static final long serialVersionUID = 2L;
    
    private final TypeDeclaration typeDeclaration;
    
    public RuleFeatureGen(TypeDeclaration typeDeclaration) {
      this.typeDeclaration = Preconditions.checkNotNull(typeDeclaration);
    }

    @Override
    public Map<String, Double> generateFeatures(Assignment item) {
      List<Object> values = item.getValues();
      ExpressionNode expressionNode = (ExpressionNode) values.get(0);
      Type type = expressionNode.getType();
      String approximateSyntax = expressionNode.getNumAppliedArguments() + ":" + type;

      Object ruleName = values.get(1);

      Map<String, Double> featureVals = Maps.newHashMap();
      featureVals.put(approximateSyntax + " " + ruleName, 1.0);
      for (int i = 0; i < 2; i++) {
        Expression2 template = expressionNode.getExpressionTemplate(typeDeclaration, i);
        featureVals.put(approximateSyntax + " " + template + " " + ruleName, 1.0);
      }
      return featureVals;
    }
  }

  private static class TerminalFeatureGen implements FeatureGenerator<Assignment, String> {
    private static final long serialVersionUID = 1L;

    @Override
    public Map<String, Double> generateFeatures(Assignment item) {
      List<Object> values = item.getValues();
      Expression2 e = ((ExpressionNode) values.get(1)).getExpression();
      List<String> freeVars = Lists.newArrayList(StaticAnalysis.getFreeVariables(e));
      Collections.sort(freeVars);
      Object terminals = values.get(0);

      Map<String, Double> featureVals = Maps.newHashMap();
      // One feature for the combined set of predicates
      // String featureName = Joiner.on(" ").join(freeVars) + " -> " + terminals.toString();
      // featureVals.put(item.toString(), 1.0);
      // One feature per predicate

      for (String freeVar : freeVars) {
        if (!(freeVar.equals("and:<t*,t>") || freeVar.equals("exists:<<e,t>,t>"))) {
          String featureName = freeVar + " -> " + terminals.toString();
          featureVals.put(featureName, 1.0);
        }
      }
      return featureVals;
    }
  }
}
