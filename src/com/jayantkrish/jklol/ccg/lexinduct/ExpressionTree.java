package com.jayantkrish.jklol.ccg.lexinduct;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.Scope;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.SubsetIterator;

public class ExpressionTree {
  private final Expression2 rootExpression;
  // Number of arguments of expression that get
  // applied in this tree.
  private final int numAppliedArguments;

  private final Tensor expressionFeatures;

  private final List<ExpressionTree> substitutions;
  
  private final List<ExpressionTree> lefts;
  private final List<ExpressionTree> rights;

  public ExpressionTree(Expression2 rootExpression, int numAppliedArguments,
      Tensor expressionFeatures, List<ExpressionTree> substitutions, List<ExpressionTree> lefts,
      List<ExpressionTree> rights) {
    this.rootExpression = Preconditions.checkNotNull(rootExpression);
    this.numAppliedArguments = numAppliedArguments;
    this.expressionFeatures = expressionFeatures;

    Preconditions.checkArgument(lefts.size() == rights.size());
    this.substitutions = ImmutableList.copyOf(substitutions);
    this.lefts = ImmutableList.copyOf(lefts);
    this.rights = ImmutableList.copyOf(rights);
  }

  public static ExpressionTree fromExpression(Expression2 expression) {
    return fromExpression(expression, ExpressionSimplifier.lambdaCalculus(),
        Collections.<String, String>emptyMap(), Collections.<String>emptySet(), 0, 2, 2);
  }

  public static ExpressionTree fromExpression(Expression2 expression,
      ExpressionSimplifier simplifier, Map<String, String> typeReplacements,
      Set<String> constantsToIgnore, int numAppliedArguments, int maxDepth, int maxAppliedArguments) {
    expression = simplifier.apply(expression);
    
    List<ExpressionTree> substitutions = Lists.newArrayList();
    List<ExpressionTree> lefts = Lists.newArrayList();
    List<ExpressionTree> rights = Lists.newArrayList();
    
    for (int i = 1; i < expression.size(); i++) {
      int depth = expression.getDepth(i);
      Scope scope = StaticAnalysis.getEnclosingScope(expression, i);
      if (depth <= (maxDepth + scope.getDepth()) && !StaticAnalysis.isPartOfSpecialForm(expression, i)) {

        List<Expression2> genLefts = Lists.newArrayList();
        List<Expression2> genRights = Lists.newArrayList();
        
        doBasicGeneration(expression, i, scope, genLefts, genRights);
        doAndGeneration(expression, i, scope, genLefts, genRights);
        
        for (int j = 0; j < genLefts.size(); j++) {
          Expression2 argExpression = genLefts.get(j);
          Expression2 funcExpression = genRights.get(j);

          Set<String> funcFreeVars = StaticAnalysis.getFreeVariables(funcExpression);
          Set<String> argFreeVars = StaticAnalysis.getFreeVariables(argExpression);
          funcFreeVars.removeAll(constantsToIgnore);
          argFreeVars.removeAll(constantsToIgnore);

          if (funcFreeVars.size() == 0 || argFreeVars.size() == 0) {
            // The function is something like (lambda x y (x y))
            continue;
          }

          Type argType = StaticAnalysis.inferType(argExpression, typeReplacements);
          if (argType.isFunctional() && argType.getReturnType().isFunctional()) {
            // The argument has a complex type that is unlikely to be
            // the argument of another category. 
            continue;
          }

          if (numAppliedArguments >= maxAppliedArguments) {
            // This means that the generated function will accept 3 arguments
            // in the sentence, which is quite unlikely.
            continue;
          }

          ExpressionTree left = ExpressionTree.fromExpression(argExpression, simplifier,
              typeReplacements, constantsToIgnore, 0, maxDepth, maxAppliedArguments);
          ExpressionTree right = ExpressionTree.fromExpression(funcExpression, simplifier,
              typeReplacements, constantsToIgnore, numAppliedArguments + 1, maxDepth, maxAppliedArguments);
          lefts.add(left);
          rights.add(right);
        }
      }
    }
    return new ExpressionTree(expression, numAppliedArguments, null, substitutions, lefts, rights);
  }
  
  private static void doBasicGeneration(Expression2 expression, int i, Scope scope,
      List<Expression2> argExpressions, List<Expression2> funcExpressions) {
    Expression2 lambdaTemplate = ExpressionParser.expression2()
        .parseSingleExpression("(lambda ARGS BODY)");
    Expression2 applicationTemplate = ExpressionParser.expression2()
        .parseSingleExpression("(FUNC VALUES)");

    Expression2 subexpression = expression.getSubexpression(i);
    // Don't remove the first element of applications
    int[] parentChildren = expression.getChildIndexes(i - 1);
    if (parentChildren.length > 0 && parentChildren[0] == i) {
      return;
    }

    // Don't remove lambda expressions, because removing their bodies 
    // produces an identical decomposition
    if (StaticAnalysis.isLambda(subexpression, 0)) {
      return;
    }

    // System.out.println(subexpression);
    Set<String> freeVars = Sets.newHashSet(StaticAnalysis.getFreeVariables(subexpression));
    Set<String> scopeBindings = scope.getBoundVariables();

    freeVars.retainAll(scopeBindings);
    List<Expression2> args = Lists.newArrayList();
    for (String freeVar : freeVars) {
      args.add(Expression2.constant(freeVar));
    }

    Expression2 argExpression = subexpression;
    if (args.size() > 0) {
      argExpression = lambdaTemplate.substituteInline("ARGS", args);
      argExpression = argExpression.substitute("BODY", subexpression);
    }

    Expression2 newVariable = Expression2.constant(StaticAnalysis.getNewVariableName(expression));
    Expression2 bodySub = newVariable;
    if (args.size() > 0) {
      bodySub = applicationTemplate.substitute("FUNC", newVariable);
      bodySub = bodySub.substituteInline("VALUES", args);
    }

    Expression2 funcExpression = lambdaTemplate.substitute("ARGS", newVariable);
    funcExpression = funcExpression.substitute("BODY", expression.substitute(i, bodySub));

    argExpressions.add(argExpression);
    funcExpressions.add(funcExpression);
  }
  
  private static void doAndGeneration(Expression2 expression, int i, Scope scope,
      List<Expression2> argExpressions, List<Expression2> funcExpressions) {
    Expression2 lambdaTemplate = ExpressionParser.expression2()
        .parseSingleExpression("(lambda ARGS BODY)");
    Expression2 andTemplate = ExpressionParser.expression2()
        .parseSingleExpression("(and:<t*,t> BODY)");
    Expression2 applicationTemplate = ExpressionParser.expression2()
        .parseSingleExpression("(FUNC VALUES)");

    Expression2 subexpression = expression.getSubexpression(i);
    if (!subexpression.isConstant() && subexpression.getSubexpression(1).isConstant() &&
        subexpression.getSubexpression(1).getConstant().equals("and:<t*,t>")) {
      
      int[] childIndexes = expression.getChildIndexes(i);
      int numTerms = childIndexes.length - 1;
      
      if (numTerms < 3) {
        // Only generate the types of functions that aren't generated
        // by the standard splitting above (which works for 2-term
        // conjunctions)
        return;
      }
      
      Iterator<boolean[]> iter = new SubsetIterator(numTerms);
      while (iter.hasNext()) {
        boolean[] selected = iter.next();
        
        List<Expression2> argTerms = Lists.newArrayList();
        List<Expression2> funcTerms = Lists.newArrayList();
        for (int j = 0; j < selected.length; j++) {
          if (selected[j]) {
            argTerms.add(expression.getSubexpression(childIndexes[j + 1]));
          } else {
            funcTerms.add(expression.getSubexpression(childIndexes[j + 1]));
          }
        }
        
        if (argTerms.size() <= 1 || argTerms.size() == numTerms) {
          continue;
        }

        // Find variables that are bound by outside lambdas.
        Set<String> freeVars = Sets.newHashSet();
        for (Expression2 argTerm : argTerms) {
          freeVars.addAll(StaticAnalysis.getFreeVariables(argTerm));
        }
        Set<String> scopeBindings = scope.getBoundVariables();

        freeVars.retainAll(scopeBindings);
        
        List<Expression2> args = Lists.newArrayList();
        for (String freeVar : freeVars) {
          args.add(Expression2.constant(freeVar));
        }

        Expression2 argExpression = andTemplate.substituteInline("BODY", argTerms);
        if (args.size() > 0) {
          argExpression = lambdaTemplate.substituteInline("ARGS", args).substitute("BODY", argExpression);
        }

        Expression2 newVariable = Expression2.constant(StaticAnalysis.getNewVariableName(expression));
        
        Expression2 functionConjunct = newVariable;
        if (args.size() > 0) {
          functionConjunct = applicationTemplate.substitute("FUNC", functionConjunct)
              .substituteInline("VALUES", args);
        }
        funcTerms.add(functionConjunct);
        Expression2 body = expression.substitute(i, andTemplate.substituteInline("BODY", funcTerms));
        Expression2 funcTerm = lambdaTemplate.substitute("ARGS", newVariable)
            .substitute("BODY", body);
        
        argExpressions.add(argExpression);
        funcExpressions.add(funcTerm);
      }
    }
  }

  public Expression2 getExpression() {
    return rootExpression;
  }
  
  public ExpressionNode getExpressionNode() {
    return new ExpressionNode(rootExpression, numAppliedArguments);
  }
  
  public int getNumAppliedArguments() {
    return numAppliedArguments;
  }
  
  public Tensor getExpressionFeatures() {
    return expressionFeatures;
  }

  public boolean hasChildren() {
    return lefts.size() > 0;
  }

  public boolean hasSubstitutions() {
    return substitutions.size() > 0;
  }

  public List<ExpressionTree> getSubstitutions() {
    return substitutions;
  }

  public List<ExpressionTree> getLeftChildren() {
    return lefts;
  }

  public List<ExpressionTree> getRightChildren() {
    return rights;
  }

  /**
   * Gets all of the expressions contained in this tree and 
   * its subtrees.
   * 
   * @param accumulator
   */
  public void getAllExpressions(Collection<Expression2> accumulator) {
    accumulator.add(rootExpression);

    for (int i = 0; i < substitutions.size(); i++) {
      substitutions.get(i).getAllExpressions(accumulator);
    }

    for (int i = 0; i < lefts.size(); i++) {
      lefts.get(i).getAllExpressions(accumulator);
      rights.get(i).getAllExpressions(accumulator);
    }
  }
  
  /**
   * Gets all of the expressions contained in this tree and 
   * its subtrees.
   * 
   * @param accumulator
   */
  public void getAllExpressionNodes(Collection<ExpressionNode> accumulator) {
    accumulator.add(new ExpressionNode(rootExpression, numAppliedArguments));
    
    for (int i = 0; i < substitutions.size(); i++) {
      substitutions.get(i).getAllExpressionNodes(accumulator);
    }

    for (int i = 0; i < lefts.size(); i++) {
      lefts.get(i).getAllExpressionNodes(accumulator);
      rights.get(i).getAllExpressionNodes(accumulator);
    }
  }

  /**
   * Returns a new expression tree with the same structure as this one,
   * where {@code generator} has been applied to generate feature vectors
   * for each expression in the tree.
   *
   * @param generator
   * @return
   */
  public ExpressionTree applyFeatureVectorGenerator(FeatureVectorGenerator<Expression2> generator) {
    List<ExpressionTree> newSubstitutions = Lists.newArrayList();
    for (int i = 0; i < substitutions.size(); i++) {
      newSubstitutions.add(substitutions.get(i).applyFeatureVectorGenerator(generator));
    }
    
    List<ExpressionTree> newLefts = Lists.newArrayList();
    List<ExpressionTree> newRights = Lists.newArrayList();
    for (int i = 0; i < lefts.size(); i++) {
      newLefts.add(lefts.get(i).applyFeatureVectorGenerator(generator));
      newRights.add(rights.get(i).applyFeatureVectorGenerator(generator));
    }

    return new ExpressionTree(rootExpression, numAppliedArguments,
        generator.apply(rootExpression), newSubstitutions, newLefts, newRights);
  }

  public int size() {
    int sum = 1;
    for (int i = 0; i < lefts.size(); i++) {
      sum += lefts.get(i).size();
      sum += rights.get(i).size();
    }
    return sum;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    toStringHelper(this, sb, 0);
    return sb.toString();
  }
  
  private static void toStringHelper(ExpressionTree tree, StringBuilder sb, int depth) {
    for (int i = 0 ; i < depth; i++) {
      sb.append(" ");
    }
    sb.append(tree.rootExpression);
    sb.append("\n");
    
    if (tree.substitutions.size() > 0) {
      for (int i = 0; i < tree.substitutions.size(); i++) {
        toStringHelper(tree.substitutions.get(i), sb, depth + 2);
      }
    }
    for (int i = 0; i < tree.lefts.size(); i++) {
      toStringHelper(tree.lefts.get(i), sb, depth + 2);      
      toStringHelper(tree.rights.get(i), sb, depth + 2);
    }
  }
  
  public static class ExpressionNode implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Expression2 expression;
    private final int numAppliedArguments;
    
    public ExpressionNode(Expression2 expression, int numAppliedArguments) {
      this.expression = Preconditions.checkNotNull(expression);
      this.numAppliedArguments = numAppliedArguments;
    }

    public Expression2 getExpression() {
      return expression;
    }

    public int getNumAppliedArguments() {
      return numAppliedArguments;
    }

    @Override
    public String toString() {
      return numAppliedArguments + ":" + expression;
    }
    
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((expression == null) ? 0 : expression.hashCode());
      result = prime * result + numAppliedArguments;
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      ExpressionNode other = (ExpressionNode) obj;
      if (expression == null) {
        if (other.expression != null)
          return false;
      } else if (!expression.equals(other.expression))
        return false;
      if (numAppliedArguments != other.numAppliedArguments)
        return false;
      return true;
    }
  }
}
