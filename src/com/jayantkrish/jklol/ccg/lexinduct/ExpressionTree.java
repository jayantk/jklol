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
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.tensor.Tensor;

public class ExpressionTree {
  private final Expression rootExpression;
  // Number of arguments of expression that get
  // applied in this tree.
  private final int numAppliedArguments;

  private final Tensor expressionFeatures;
  
  private final List<ExpressionTree> lefts;
  private final List<ExpressionTree> rights;

  public ExpressionTree(Expression rootExpression, int numAppliedArguments,
      Tensor expressionFeatures, List<ExpressionTree> lefts, List<ExpressionTree> rights) {
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
    this.numAppliedArguments = numAppliedArguments;
    this.expressionFeatures = expressionFeatures;

    Preconditions.checkArgument(lefts.size() == rights.size());
    this.lefts = ImmutableList.copyOf(lefts);
    this.rights = ImmutableList.copyOf(rights);
  }

  public static ExpressionTree fromExpression(Expression expression) {
    return fromExpression(expression, 0, Collections.<ConstantExpression>emptySet());
  }
  
  public static ExpressionTree fromExpression(Expression expression,
      Set<ConstantExpression> constantsThatDontCount) {
    return fromExpression(expression, 0, constantsThatDontCount);
  }

  /**
   * Decompose the body of an expression into pieces that combine using
   * function application to produce a tree.
   * 
   * @param expression
   * @return
   */
  public static ExpressionTree fromExpression(Expression expression, int numAppliedArguments,
      Set<ConstantExpression> constantsThatDontCount) {
    List<ConstantExpression> args = Collections.emptyList();
    expression = expression.simplify();
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
      
      // Canonicalize the order in which we remove arguments from multiargument
      // functions.
      int startIndex = 1;
      for (int i = 1; i < subexpressions.size(); i++) {
        ConstantExpression var = null;
        if (subexpressions.get(i) instanceof ConstantExpression) {
          var = (ConstantExpression) subexpressions.get(i);
        } else if (subexpressions.get(i) instanceof ApplicationExpression) {
          Expression function = ((ApplicationExpression) subexpressions.get(i)).getFunction();
          if (function instanceof ConstantExpression) {
            var = (ConstantExpression) function;
          }
        }

        // TODO: better way to check if we made this variable.
        if (var != null && var.getName().startsWith("var")) {
          startIndex = i + 1;
        }
      }

      for (int i = startIndex; i < subexpressions.size(); i++) {
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
        
        Set<ConstantExpression> rightFreeVars = rightExpression.getFreeVariables();
        Set<ConstantExpression> leftFreeVars = leftExpression.getFreeVariables();
        rightFreeVars.removeAll(constantsThatDontCount);
        leftFreeVars.removeAll(constantsThatDontCount);

        if (rightFreeVars.size() == 0 || leftFreeVars.size() == 0) {
          // This expression is purely some lambda arguments applied to each other
          // in some way, e.g., (lambda f g (g f)). Don't generate these.
          continue;
        }

        ExpressionTree left = fromExpression(leftExpression, 0, constantsThatDontCount);
        ExpressionTree right = fromExpression(rightExpression, numAppliedArguments + 1, constantsThatDontCount);
        lefts.add(left);
        rights.add(right);
      }

      doGeoquery(expression, numAppliedArguments, lefts, rights, constantsThatDontCount);
    }
    return new ExpressionTree(expression, numAppliedArguments, null, lefts, rights);
  }
  
  private static void doGeoquery(Expression expression, int numAppliedArguments,
      List<ExpressionTree> lefts, List<ExpressionTree> rights,
      Set<ConstantExpression> constantsThatDontCount) {
    List<ConstantExpression> args = Collections.emptyList();
    Expression body = expression.simplify();
    if (expression instanceof LambdaExpression) {
      args = ((LambdaExpression) expression).getArguments();
      body = ((LambdaExpression) expression).getBody();
    }
    Set<ConstantExpression> argSet = Sets.newHashSet(args); 

    if (body instanceof ApplicationExpression) {
      ApplicationExpression applicationExpression = (ApplicationExpression) body;
      List<Expression> subexpressions = applicationExpression.getSubexpressions();

      if (applicationExpression.getFunction().toString().equals("and:<t*,t>")) {
        // Hack for processing GeoQuery expressions.
        Set<ConstantExpression> freeVarsInFunction = applicationExpression.getFunction().getFreeVariables();
        for (int i = 1; i < subexpressions.size(); i++) {
          // Pull out the arguments of any functions that are part of
          // a conjunction.
          if (subexpressions.get(i) instanceof ApplicationExpression) {
            ApplicationExpression applicationSubexpression = (ApplicationExpression) subexpressions.get(i);
            List<Expression> arguments = applicationSubexpression.getSubexpressions();
            for (int j = 1; j < arguments.size(); j++) {
              Expression argument = arguments.get(j);
              List<ConstantExpression> argumentArgs = Lists.newArrayList(argSet);
              argumentArgs.retainAll(argument.getFreeVariables());

              Expression leftExpression = arguments.get(j);
              if (argumentArgs.size() > 0) {
                leftExpression = new LambdaExpression(argumentArgs, leftExpression);
              }

              ConstantExpression functionArg = ConstantExpression.generateUniqueVariable();
              Expression functionBody = functionArg;
              if (argumentArgs.size() > 0) {
                functionBody = new ApplicationExpression(functionBody, argumentArgs);
              }
              
              List<Expression> newArguments = Lists.newArrayList(arguments);
              newArguments.set(j, functionBody);

              Expression initialBody = new ApplicationExpression(newArguments);
              List<Expression> newSubexpressions = Lists.newArrayList(subexpressions);
              newSubexpressions.set(i, initialBody);
              Expression otherBody = new ApplicationExpression(newSubexpressions);

              List<ConstantExpression> newArgs = Lists.newArrayList(functionArg);
              newArgs.addAll(args);
              LambdaExpression rightExpression = new LambdaExpression(newArgs, otherBody);

              /*
              System.out.println(expression);
              System.out.println(leftExpression);
              System.out.println(rightExpression);
              */
              
              Set<ConstantExpression> rightFreeVars = rightExpression.getFreeVariables();
              Set<ConstantExpression> leftFreeVars = leftExpression.getFreeVariables();
              rightFreeVars.removeAll(constantsThatDontCount);
              rightFreeVars.removeAll(freeVarsInFunction);
              leftFreeVars.removeAll(constantsThatDontCount);

              if (rightFreeVars.size() == 0 || leftExpression.getFreeVariables().size() == 0) {
                // This expression is purely some lambda arguments applied to each other
                // in some way, e.g., (lambda f g (g f)). Don't generate these.
                continue;
              }
        
              ExpressionTree left = fromExpression(leftExpression, 0, constantsThatDontCount);
              ExpressionTree right = fromExpression(rightExpression, numAppliedArguments + 1, constantsThatDontCount);
              lefts.add(left);
              rights.add(right);
            }
          }
        }
      }
    }
  }

  public Expression getExpression() {
    return rootExpression;
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
  public void getAllExpressions(Collection<Expression> accumulator) {
    accumulator.add(rootExpression);

    for (int i = 0; i < lefts.size(); i++) {
      lefts.get(i).getAllExpressions(accumulator);
      rights.get(i).getAllExpressions(accumulator);
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
  public ExpressionTree applyFeatureVectorGenerator(FeatureVectorGenerator<Expression> generator) {
    List<ExpressionTree> newLefts = Lists.newArrayList();
    List<ExpressionTree> newRights = Lists.newArrayList();
    for (int i = 0; i < lefts.size(); i++) {
      newLefts.add(lefts.get(i).applyFeatureVectorGenerator(generator));
      newRights.add(rights.get(i).applyFeatureVectorGenerator(generator));
    }

    return new ExpressionTree(rootExpression, numAppliedArguments,
        generator.apply(rootExpression), newLefts, newRights);
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
