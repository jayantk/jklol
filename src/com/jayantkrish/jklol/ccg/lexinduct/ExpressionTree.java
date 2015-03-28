package com.jayantkrish.jklol.ccg.lexinduct;

import java.util.Collection;
import java.util.Collections;
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

public class ExpressionTree {
  private final Expression2 rootExpression;
  // Number of arguments of expression that get
  // applied in this tree.
  private final int numAppliedArguments;

  private final Tensor expressionFeatures;
  
  private final List<ExpressionTree> lefts;
  private final List<ExpressionTree> rights;

  public ExpressionTree(Expression2 rootExpression, int numAppliedArguments,
      Tensor expressionFeatures, List<ExpressionTree> lefts, List<ExpressionTree> rights) {
    // Canonicalize variable names.
    // rootExpression = canonicalizeVariableNames(rootExpression.simplify(), 0);
    this.rootExpression = Preconditions.checkNotNull(rootExpression);
    this.numAppliedArguments = numAppliedArguments;
    this.expressionFeatures = expressionFeatures;

    Preconditions.checkArgument(lefts.size() == rights.size());
    this.lefts = ImmutableList.copyOf(lefts);
    this.rights = ImmutableList.copyOf(rights);
  }

  public static ExpressionTree fromExpression(Expression2 expression) {
    return fromExpression(expression, ExpressionSimplifier.lambdaCalculus(),
        Collections.<String, String>emptyMap(), 0, 2);
  }

  public static ExpressionTree fromExpression(Expression2 expression,
      ExpressionSimplifier simplifier, Map<String, String> typeReplacements,
      int numAppliedArguments, int maxDepth) {
    Expression2 lambdaTemplate = ExpressionParser.expression2()
        .parseSingleExpression("(lambda ARGS BODY)");
    Expression2 applicationTemplate = ExpressionParser.expression2()
        .parseSingleExpression("(FUNC VALUES)");

    expression = simplifier.apply(expression);
    
    List<ExpressionTree> lefts = Lists.newArrayList();
    List<ExpressionTree> rights = Lists.newArrayList();
    
    for (int i = 1; i < expression.size(); i++) {
      int depth = expression.getDepth(i);
      Scope scope = StaticAnalysis.getEnclosingScope(expression, i);
      if (depth <= (maxDepth + scope.getDepth()) && !StaticAnalysis.isPartOfSpecialForm(expression, i)) {
        Expression2 subexpression = expression.getSubexpression(i);

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

        if (StaticAnalysis.getFreeVariables(funcExpression).size() == 0 ||
            StaticAnalysis.getFreeVariables(argExpression).size() == 0) {
          // The function is something like (lambda x y (x y))
          continue;
        }
        
        Type argType = StaticAnalysis.inferType(argExpression, typeReplacements);
        if (argType.isFunctional() && argType.getReturnType().isFunctional()) {
          // The argument has a complex type that is unlikely to be
          // the argument of another category. 
          continue;
        }

        ExpressionTree left = ExpressionTree.fromExpression(argExpression, simplifier,
            typeReplacements, 0, maxDepth);
        ExpressionTree right = ExpressionTree.fromExpression(funcExpression, simplifier,
            typeReplacements, numAppliedArguments + 1, maxDepth);
        lefts.add(left);
        rights.add(right);
      }
    }
    return new ExpressionTree(expression, numAppliedArguments, null, lefts, rights);
  }

  /**
   * Decompose the body of an expression into pieces that combine using
   * function application to produce a tree.
   * 
   * @param expression
   * @return
   */
  /*
  public static ExpressionTree fromExpression(Expression2 expression, int numAppliedArguments,
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

              System.out.println(expression);
              System.out.println(leftExpression);
              System.out.println(rightExpression);
              
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
  */

  public Expression2 getExpression() {
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
  public void getAllExpressions(Collection<Expression2> accumulator) {
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
  public ExpressionTree applyFeatureVectorGenerator(FeatureVectorGenerator<Expression2> generator) {
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

    for (int i = 0; i < tree.lefts.size(); i++) {
      toStringHelper(tree.lefts.get(i), sb, depth + 2);      
      toStringHelper(tree.rights.get(i), sb, depth + 2);
    }
  }
}
