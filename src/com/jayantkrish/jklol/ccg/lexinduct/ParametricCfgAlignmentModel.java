package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lexinduct.ExpressionTree.ExpressionNode;
import com.jayantkrish.jklol.cfg.CfgExpectation;
import com.jayantkrish.jklol.cfg.CfgParseChart;
import com.jayantkrish.jklol.cfg.CfgParseTree;
import com.jayantkrish.jklol.experiments.geoquery.LexiconInductionCrossValidation;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.bayesnet.CptTableFactor;
import com.jayantkrish.jklol.models.bayesnet.SparseCptTableFactor;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ConstantParametricFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFamily;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.preprocessing.FeatureGenerator;
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
  private final boolean loglinear;

  public static final String TERMINAL = "terminal";
  public static final String FORWARD_APPLICATION = "fa";
  public static final String BACKWARD_APPLICATION = "ba";
  public static final String SKIP_RULE = "skip";
  public static final String SKIP_CONSTANT = "**skip**";
  public static final ExpressionNode SKIP_EXPRESSION = new ExpressionNode(Expression2.constant(SKIP_CONSTANT),
      Type.createAtomic(SKIP_CONSTANT), 0);

  public ParametricCfgAlignmentModel(ParametricFactor ruleFactor, ParametricFactor nonterminalFactor,
      ParametricFactor terminalFactor, VariableNumMap terminalVar, VariableNumMap leftVar, VariableNumMap rightVar,
      VariableNumMap parentVar, VariableNumMap ruleVar, int nGramLength, boolean loglinear) {
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
      Collection<AlignmentExample> examples, FeatureVectorGenerator<Expression2> featureVectorGenerator,
      int nGramLength, boolean discriminative, boolean loglinear) {
    Set<List<String>> terminalVarValues = Sets.newHashSet();
    for (AlignmentExample example : examples) {
      terminalVarValues.addAll(example.getNGrams(nGramLength));
    }
    return buildAlignmentModel(examples, featureVectorGenerator, terminalVarValues,
        discriminative, loglinear);
  }

  public static ParametricCfgAlignmentModel buildAlignmentModel(Collection<AlignmentExample> examples,
      FeatureVectorGenerator<Expression2> featureVectorGenerator, Set<List<String>> terminalVarValues,
      boolean discriminative, boolean loglinear) {
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

    Set<Type> types = Sets.newHashSet();
    for (ExpressionNode expression : expressions) {
      types.add(expression.getType());
    }
    
    Multimap<Expression2, ExpressionNode> templateExpressionMap = HashMultimap.create();
    for (ExpressionNode expression : expressions) {
      // TODO: this is terrible.
      for (int i = 0; i < 2; i++) {
        Expression2 template = expression.getExpressionTemplate(LexiconInductionCrossValidation.typeReplacements, i);
        templateExpressionMap.put(template, expression);
      }
    }

    for (Expression2 template : templateExpressionMap.keySet()) {
      System.out.println(template);
      for (ExpressionNode node : templateExpressionMap.get(template)) {
        System.out.println("  " + node);
      }
    }

    DiscreteVariable expressionVarType = new DiscreteVariable("expressions", expressions);
    DiscreteVariable typeVarType = new DiscreteVariable("types", types); 
    DiscreteVariable terminalVarType = new DiscreteVariable("words", terminalVarValues);
    DiscreteVariable ruleVarType = new DiscreteVariable("rule", Arrays.asList(TERMINAL,
        FORWARD_APPLICATION, BACKWARD_APPLICATION, SKIP_RULE));

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
    /*
    DiscreteFactor sparsityFactor = TableFactor.unity(parentVar.union(terminalVar))
        .outerProduct(TableFactor.pointDistribution(ruleVar, ruleVar.outcomeArrayToAssignment(TERMINAL)));
        */
    DiscreteFactor sparsityFactor = terminalBuilder.build();
    DiscreteFactor constantFactor = TableFactor.zero(VariableNumMap.unionAll(terminalVar, parentVar, ruleVar));
    
    System.out.println("nonterminal sparsity: " + nonterminalSparsityFactor.size());
    System.out.println("terminal sparsity: " + sparsityFactor.size());

    // Learn the nonterminal probabilities
    ParametricFactor ruleFactor = null;
    ParametricFactor nonterminalFactor = null;
    ParametricFactor terminalFactor = null;
    if (!discriminative) {      
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
            TableFactor.unity(parentVar.union(ruleVar)), new RuleFeatureGen());
        DiscreteLogLinearFactor nonterminalFeatureFactor = DiscreteLogLinearFactor.fromFeatureGeneratorSparse(
            nonterminalSparsityFactor, new NonterminalFeatureGen());
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
        System.out.println(f.getParameterDescription());

        /*
        List<String> factorNames = Arrays.asList("indicators", "features");
        ruleFactor = new CombiningParametricFactor(ruleFeatureFactor.getVars(), factorNames,
            Arrays.asList(ruleIndicatorFactor, ruleFeatureFactor), false);
        nonterminalFactor = new CombiningParametricFactor(nonterminalFeatureFactor.getVars(), factorNames,
            Arrays.asList(nonterminalIndicatorFactor, nonterminalFeatureFactor), false);
        terminalFactor = new CombiningParametricFactor(terminalFeatureFactor.getVars(), factorNames,
            Arrays.asList(terminalIndicatorFactor, terminalFeatureFactor), false);
            */
        
        ruleFactor = ruleFeatureFactor;
        nonterminalFactor = nonterminalFeatureFactor;
        terminalFactor = terminalFeatureFactor;
      } else {
        ruleFactor = new CptTableFactor(parentVar, ruleVar);
        nonterminalFactor = new SparseCptTableFactor(parentVar.union(ruleVar),
            leftVar.union(rightVar), nonterminalSparsityFactor, nonterminalConstantFactor);
        terminalFactor = new SparseCptTableFactor(parentVar.union(ruleVar),
            terminalVar, sparsityFactor, constantFactor);
      }
    } else {
      // Probability distribution over the different CFG rule types
      // Assign all binary rules probability 1
      nonterminalFactor = new ConstantParametricFactor(nonterminalVars, nonterminalSparsityFactor);
      // Constant probability of invoking a rule or not.
      ruleFactor = new ConstantParametricFactor(parentVar.union(ruleVar),
          TableFactor.unity(parentVar.union(ruleVar)));

      // Maximize P(logical form | word) for the terminals.
      terminalFactor = new SparseCptTableFactor(terminalVar.union(ruleVar),
          parentVar, sparsityFactor, constantFactor);
    }

    // This is some stuff for using features in the nonterminals.
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
    return new ListSufficientStatistics(Arrays.asList("rules", "nonterminals", "terminals"),
        Arrays.asList(ruleFactor.getNewSufficientStatistics(),
            nonterminalFactor.getNewSufficientStatistics(), terminalFactor.getNewSufficientStatistics()));
  }

  @Override
  public CfgAlignmentModel getModelFromParameters(SufficientStatistics parameters) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();

    DiscreteFactor rules = ruleFactor.getModelFromParameters(parameterList.get(0))
        .coerceToDiscrete();
    DiscreteFactor ntf = nonterminalFactor.getModelFromParameters(parameterList.get(1))
        .coerceToDiscrete();
    DiscreteFactor tf = terminalFactor.getModelFromParameters(parameterList.get(2))
        .coerceToDiscrete();

    if (loglinear) {
      // Normalize each distribution.
      rules = rules.product(rules.marginalize(ruleVar).inverse());
      ntf = ntf.product(ntf.marginalize(ntf.getVars().removeAll(ruleVar.union(parentVar))).inverse());
      tf = tf.product(tf.marginalize(tf.getVars().removeAll(ruleVar.union(parentVar))).inverse());
    }
    
    // Incorporate the conditional probabilities of choosing terminals vs. nonterminals.
    ntf = ntf.product(rules);
    tf = tf.product(rules);
    
    return new CfgAlignmentModel(ntf, tf, terminalVar, leftVar, rightVar, parentVar, ruleVar,
        nGramLength);
  }
  
  public void incrementExpectations(CfgExpectation expectations, CfgParseChart chart, double count) {
    TableFactorBuilder ruleBuilder = expectations.getRuleBuilder();
    TableFactorBuilder terminalBuilder = expectations.getTerminalBuilder();
    TableFactorBuilder nonterminalBuilder = expectations.getNonterminalBuilder();
    
    DiscreteFactor terminalExpectations = chart.getTerminalRuleExpectations().coerceToDiscrete();
    Iterator<Outcome> iter = terminalExpectations.outcomeIterator();
    double partitionFunction = chart.getPartitionFunction();
    while (iter.hasNext()) {
      Outcome o = iter.next();
      Assignment a = o.getAssignment();
      double amount = count * o.getProbability() / partitionFunction;

      ruleBuilder.incrementWeight(a.intersection(ruleFactor.getVars().getVariableNumsArray()), amount);
      terminalBuilder.incrementWeight(a, amount);
    }

    DiscreteFactor nonterminalExpectations = chart.getBinaryRuleExpectations().coerceToDiscrete();
    iter = nonterminalExpectations.outcomeIterator();
    while (iter.hasNext()) {
      Outcome o = iter.next();
      Assignment a = o.getAssignment();
      double amount = count * o.getProbability() / partitionFunction;
      
      ruleBuilder.incrementWeight(a.intersection(ruleFactor.getVars().getVariableNumsArray()), amount);
      nonterminalBuilder.incrementWeight(a, amount);
    }
  }
  
  public void incrementExpectations(CfgExpectation expectations, CfgParseTree tree, double count) {
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
    
    ruleFactor.incrementSufficientStatisticsFromMarginal(statisticList.get(0),
        parameterList.get(0), expectations.getRuleBuilder().build(), Assignment.EMPTY, 1, 1.0);
    nonterminalFactor.incrementSufficientStatisticsFromMarginal(statisticList.get(1),
        parameterList.get(1), expectations.getNonterminalBuilder().build(), Assignment.EMPTY, 1, 1.0);
    terminalFactor.incrementSufficientStatisticsFromMarginal(statisticList.get(2),
        parameterList.get(2), expectations.getTerminalBuilder().build(), Assignment.EMPTY, 1, 1.0);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters) {
    return getParameterDescription(parameters, -1);
  }

  @Override
  public String getParameterDescription(SufficientStatistics parameters, int numFeatures) {
    List<SufficientStatistics> parameterList = parameters.coerceToList().getStatistics();

    StringBuilder sb = new StringBuilder();
    sb.append("rules:\n");
    sb.append(ruleFactor.getParameterDescription(parameterList.get(0), numFeatures));
    sb.append("nonterminals:\n");
    sb.append(nonterminalFactor.getParameterDescription(parameterList.get(1), numFeatures));
    sb.append("terminals:\n");
    sb.append(terminalFactor.getParameterDescription(parameterList.get(2), numFeatures));
    return sb.toString();
  }
  
  private static class NonterminalFeatureGen implements FeatureGenerator<Assignment, String> {
    private static final long serialVersionUID = 1L;

    @Override
    public Map<String, Double> generateFeatures(Assignment item) {
      Map<String, Double> featureVals = Maps.newHashMap();
      for (int i = 0; i < 2; i++) {
        List<Object> values = item.getValues();
        List<Object> featureValue = Lists.newArrayList();
        /*
        featureValue.add(((ExpressionNode) values.get(0)).getType());
        featureValue.add(((ExpressionNode) values.get(0)).getExpressionTemplate(LexiconInductionCrossValidation.typeReplacements, i));
        featureValue.add(((ExpressionNode) values.get(1)).getType());
        featureValue.add(((ExpressionNode) values.get(1)).getExpressionTemplate(LexiconInductionCrossValidation.typeReplacements, i));
        featureValue.add(((ExpressionNode) values.get(2)).getType());
        featureValue.add(((ExpressionNode) values.get(2)).getExpressionTemplate(LexiconInductionCrossValidation.typeReplacements, i));
        featureValue.add(values.get(3));
        */
        
        ExpressionNode root = (ExpressionNode) values.get(2);
        ExpressionNode func = null;
        String rootPosition = null;
        if (values.get(3).equals(FORWARD_APPLICATION)) {
          func = (ExpressionNode) values.get(0);
          rootPosition = "left";
        } else if (values.get(3).equals(BACKWARD_APPLICATION)) {
          func = (ExpressionNode) values.get(1);
          rootPosition = "right";
        } else if (values.get(3).equals(SKIP_RULE)) {
          func = root;
          if (values.get(0).equals(SKIP_EXPRESSION)) {
            rootPosition = "right";
          } else {
            rootPosition = "left";
          }
        }
        Preconditions.checkNotNull(func, "Unknown rule: %s", values.get(3));

        featureValue.add(root.getExpressionTemplate(LexiconInductionCrossValidation.typeReplacements, i));
        featureValue.add(func.getExpressionTemplate(LexiconInductionCrossValidation.typeReplacements, i));
        featureValue.add(rootPosition);

        String featureName = Joiner.on("->").join(featureValue);
        featureVals.put(featureName, 1.0);
        /*
      if (!values.get(3).equals(SKIP_RULE)) {
        String featureName = Joiner.on(" ").join(featureValue);
        featureVals.put(featureName, 1.0);
      }
         */
      }
      return featureVals;
    }
  }
  
  private static class RuleFeatureGen implements FeatureGenerator<Assignment, String> {
    private static final long serialVersionUID = 1L;

    @Override
    public Map<String, Double> generateFeatures(Assignment item) {
      Map<String, Double> featureVals = Maps.newHashMap();
      for (int i = 0; i < 2; i++) {
        List<Object> values = item.getValues();
        List<Object> featureValue = Lists.newArrayList();
        ExpressionNode expressionNode = (ExpressionNode) values.get(0);
        Type t = expressionNode.getType();
        Expression2 template = expressionNode.getExpressionTemplate(LexiconInductionCrossValidation.typeReplacements, i);
        featureValue.add(t);
        featureValue.add(template);
        featureValue.add(expressionNode.getNumAppliedArguments());
        featureValue.add(values.get(1));

        String featureName = Joiner.on(" ").join(featureValue);
        featureVals.put(featureName, 1.0);
        /*
      if (values.get(1).equals(SKIP_RULE)) {
        String featureName = "skip";
        featureVals.put(featureName, 1.0);
      } else {
        String featureName = Joiner.on(" ").join(featureValue);
        featureVals.put(featureName, 1.0);
      }
         */
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
      // featureVals.put(featureName, 1.0);
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
