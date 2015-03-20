package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;

public class ExpressionTree {
  private final Expression rootExpression;
  
  private final List<ExpressionTree> lefts;
  private final List<ExpressionTree> rights;
  
  public ExpressionTree(Expression rootExpression, List<ExpressionTree> lefts,
      List<ExpressionTree> rights) {
    // Canonicalize variable names.
    rootExpression = rootExpression.simplify();
    if (rootExpression instanceof LambdaExpression) {
      LambdaExpression lambdaExpression = (LambdaExpression) rootExpression;
      List<ConstantExpression> args = lambdaExpression.getArguments();
      List<ConstantExpression> newArgs = Lists.newArrayList();
      for (int i = 0; i < args.size(); i++) {
        newArgs.add(new ConstantExpression("$f" + i));
      }
      rootExpression = lambdaExpression.renameVariables(args, newArgs);
    }
    this.rootExpression = Preconditions.checkNotNull(rootExpression);

    Preconditions.checkArgument(lefts.size() == rights.size());
    this.lefts = ImmutableList.copyOf(lefts);
    this.rights = ImmutableList.copyOf(rights);
  }
  
  /**
   * Decompose the body of an expression into pieces that combine using
   * function application to produce a tree.
   * 
   * @param expression
   * @return
   */
  public static ExpressionTree fromExpression(Expression expression) {
    List<ConstantExpression> args = Collections.emptyList();
    Expression body = expression;
    if (expression instanceof LambdaExpression) {
      args = ((LambdaExpression) expression).getArguments();
      body = ((LambdaExpression) expression).getBody();
    }
    Set<ConstantExpression> argSet = Sets.newHashSet(args); 

    List<ExpressionTree> lefts = Lists.newArrayList();
    List<ExpressionTree> rights = Lists.newArrayList();
    
    if (body instanceof ApplicationExpression) {
      ApplicationExpression applicationExpression = (ApplicationExpression) body;
      List<Expression> subexpressions = applicationExpression.getSubexpressions();

      for (int i = 0; i < subexpressions.size(); i++) {
        List<ConstantExpression> subexpressionArgs = Lists.newArrayList(argSet);
        subexpressionArgs.retainAll(subexpressions.get(i).getFreeVariables());

        Expression leftExpression = subexpressions.get(i);
        if (subexpressionArgs.size() > 0) {
          leftExpression = new LambdaExpression(subexpressionArgs, subexpressions.get(i));
        }
        
        ConstantExpression functionArg = ConstantExpression.generateUniqueVariable();
        Expression functionBody = functionArg;
        if (subexpressionArgs.size() > 0) {
          functionBody = new ApplicationExpression(functionBody, subexpressionArgs);
        }
        List<Expression> newSubexpressions = Lists.newArrayList(subexpressions);
        newSubexpressions.set(i, functionBody);

        Expression otherBody = new ApplicationExpression(newSubexpressions);
        List<ConstantExpression> newArgs = Lists.newArrayList(functionArg);
        newArgs.addAll(args);
        LambdaExpression rightExpression = new LambdaExpression(newArgs, otherBody);

        if (rightExpression.getFreeVariables().size() == 0 || leftExpression.getFreeVariables().size() == 0) {
          // This expression is purely some lambda arguments applied to each other
          // in some way, e.g., (lambda f g (g f)). Don't generate these.
          continue;
        }
        
        ExpressionTree left = fromExpression(leftExpression);
        ExpressionTree right = fromExpression(rightExpression);
        lefts.add(left);
        rights.add(right);
      }
    }
    return new ExpressionTree(expression, lefts, rights);
  }

  /**
   * Gets all of the expressions contained in this tree and 
   * its subtrees.
   * 
   * @param accumulator
   */
  public void getAllExpressions(Collection<Expression> accumulator) {
    accumulator.add(rootExpression);

    for (int i = 0; i < lefts.size(); i++) {
      lefts.get(i).getAllExpressions(accumulator);
      rights.get(i).getAllExpressions(accumulator);
    }
  }

  public int size() {
    int sum = 1;
    for (int i = 0; i < lefts.size(); i++) {
      sum += lefts.get(i).size();
      sum += rights.get(i).size();
    }
    return sum;
  }

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

    for (int i = 0; i < tree.lefts.size(); i++) {
      toStringHelper(tree.lefts.get(i), sb, depth + 2);      
      toStringHelper(tree.rights.get(i), sb, depth + 2);
    }
  }
}
