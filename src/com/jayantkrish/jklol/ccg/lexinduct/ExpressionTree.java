package com.jayantkrish.jklol.ccg.lexinduct;

import java.io.Serializable;
import java.util.Arrays;
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
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.CommutativeReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.LambdaApplicationReplacementRule;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.Scope;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis.ScopeSet;
import com.jayantkrish.jklol.ccg.lambda2.VariableCanonicalizationReplacementRule;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.util.SubsetIterator;

public class ExpressionTree {
  private final Expression2 rootExpression;
  private final Type rootType;
  // Number of arguments of expression that get
  // applied in this tree.
  private final int numAppliedArguments;

  private final List<ExpressionTree> substitutions;
  
  private final List<ExpressionTree> lefts;
  private final List<ExpressionTree> rights;

  public ExpressionTree(Expression2 rootExpression, Type rootType, int numAppliedArguments,
      List<ExpressionTree> substitutions, List<ExpressionTree> lefts, List<ExpressionTree> rights) {
    this.rootExpression = Preconditions.checkNotNull(rootExpression);
    this.rootType = Preconditions.checkNotNull(rootType);
    this.numAppliedArguments = numAppliedArguments;

    Preconditions.checkArgument(lefts.size() == rights.size());
    this.substitutions = ImmutableList.copyOf(substitutions);
    this.lefts = ImmutableList.copyOf(lefts);
    this.rights = ImmutableList.copyOf(rights);
  }

  public static ExpressionTree fromExpression(Expression2 expression) {
    TypeDeclaration typeDeclaration = ExplicitTypeDeclaration.getDefault();
    Type type = StaticAnalysis.inferType(expression, typeDeclaration);
    return fromExpression(expression, type, ExpressionSimplifier.lambdaCalculus(),
        typeDeclaration, Collections.<String>emptySet(), 0, 2, 3);
  }

  public static ExpressionTree fromExpression(Expression2 expression, int numAppliedArguments) {
    TypeDeclaration typeDeclaration = ExplicitTypeDeclaration.getDefault();
    Type type = StaticAnalysis.inferType(expression, typeDeclaration);
    return fromExpression(expression, type, ExpressionSimplifier.lambdaCalculus(),
        typeDeclaration, Collections.<String>emptySet(), numAppliedArguments, 2, 2);
  }

  public static ExpressionTree fromExpression(Expression2 expression,
      ExpressionSimplifier simplifier, TypeDeclaration typeDeclaration,
      Set<String> constantsToIgnore, int numAppliedArguments, int maxDepth, int maxAppliedArguments) {

    Type type = StaticAnalysis.inferType(expression, typeDeclaration);
    return fromExpression(expression, type, simplifier,
        typeDeclaration, constantsToIgnore, numAppliedArguments, maxDepth, maxAppliedArguments);
  }

  public static ExpressionTree fromExpression(Expression2 expression, Type type,
      ExpressionSimplifier simplifier, TypeDeclaration typeDeclaration,
      Set<String> constantsToIgnore, int numAppliedArguments, int maxDepth, int maxAppliedArguments) {
    expression = simplifier.apply(expression);
    
    List<ExpressionTree> substitutions = Lists.newArrayList();
    List<ExpressionTree> lefts = Lists.newArrayList();
    List<ExpressionTree> rights = Lists.newArrayList();
    
    Map<Integer, Type> typeMap = StaticAnalysis.inferTypeMap(expression, type, typeDeclaration);
    for (int i = 1; i < expression.size(); i++) {
      int depth = expression.getDepth(i);
      Scope scope = StaticAnalysis.getEnclosingScope(expression, i);
      if (depth <= (maxDepth + scope.getDepth()) && !StaticAnalysis.isPartOfSpecialForm(expression, scope, i)) {

        List<Expression2> genLefts = Lists.newArrayList();
        List<Type> leftTypes = Lists.newArrayList();
        List<Expression2> genRights = Lists.newArrayList();
        List<Type> rightTypes = Lists.newArrayList();
        
        doBasicGeneration(expression, typeMap, i, scope, genLefts, leftTypes, genRights, rightTypes);
        doAndGeneration(expression, typeMap, i, scope, genLefts, leftTypes, genRights, rightTypes);
        
        for (int j = 0; j < genLefts.size(); j++) {
          Expression2 argExpression = genLefts.get(j);
          Expression2 funcExpression = genRights.get(j);
          Type argType = leftTypes.get(j);
          Type funcType = rightTypes.get(j);

          Set<String> funcFreeVars = StaticAnalysis.getFreeVariables(funcExpression);
          Set<String> argFreeVars = StaticAnalysis.getFreeVariables(argExpression);
          funcFreeVars.removeAll(constantsToIgnore);
          argFreeVars.removeAll(constantsToIgnore);

          if (funcFreeVars.size() == 0 || argFreeVars.size() == 0) {
            // The function is something like (lambda x y (x y))
            continue;
          }

          if (argType.isFunctional() && argType.getReturnType().isFunctional()) {
            // The argument has a complex type that is unlikely to be
            // the argument of another category. 
            continue;
          }

          if (numAppliedArguments >= maxAppliedArguments) {
            // This means that the generated function will accept too
            // many arguments in the sentence.
            continue;
          }
          
          if (StaticAnalysis.isLambda(argExpression)) {
            // Disallow expressions of the type (lambda f (f x y ...)) 
            List<String> args = StaticAnalysis.getLambdaArguments(argExpression);
            Expression2 body = StaticAnalysis.getLambdaBody(argExpression);
            if (args.size() == 1) {
              int[] indexes = StaticAnalysis.getIndexesOfFreeVariable(body, args.get(0));
              if (indexes.length == 1 && indexes[0] == 1) {
                continue;
              }
            }
          }

          ExpressionTree left = ExpressionTree.fromExpression(argExpression, argType, simplifier,
              typeDeclaration, constantsToIgnore, 0, maxDepth, maxAppliedArguments);
          ExpressionTree right = ExpressionTree.fromExpression(funcExpression, funcType, simplifier,
              typeDeclaration, constantsToIgnore, numAppliedArguments + 1, maxDepth, maxAppliedArguments);
          lefts.add(left);
          rights.add(right);
        }
      }
    }
    return new ExpressionTree(expression, type, numAppliedArguments, substitutions, lefts, rights);
  }
  
  private static void doBasicGeneration(Expression2 expression, Map<Integer, Type> typeMap, int i, Scope scope,
      List<Expression2> argExpressions, List<Type> argTypes, List<Expression2> funcExpressions,
      List<Type> funcTypes) {
    Expression2 lambdaTemplate = ExpressionParser.expression2()
        .parse("(lambda (ARGS) BODY)");
    Expression2 applicationTemplate = ExpressionParser.expression2()
        .parse("(FUNC VALUES)");

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

    Type argType = typeMap.get(i);
    freeVars.retainAll(scopeBindings);
    List<Expression2> args = Lists.newArrayList();
    List<Type> argArgTypes = Lists.newArrayList();
    for (String freeVar : freeVars) {
      Expression2 freeVarExpr = Expression2.constant(freeVar);
      args.add(freeVarExpr);
      Type freeVarType = typeMap.get(scope.getBindingIndex(freeVar));
      argArgTypes.add(freeVarType);
    }
    Collections.reverse(argArgTypes);
    argType = argType.addArguments(argArgTypes);

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
    Type funcType = typeMap.get(0).addArgument(argType); 

    argExpressions.add(argExpression);
    argTypes.add(argType);
    funcExpressions.add(funcExpression);
    funcTypes.add(funcType);
  }
  
  private static void doAndGeneration(Expression2 expression, Map<Integer, Type> typeMap, int i,
      Scope scope, List<Expression2> argExpressions, List<Type> argTypes, List<Expression2> funcExpressions,
      List<Type> funcTypes) {
    Expression2 lambdaTemplate = ExpressionParser.expression2()
        .parse("(lambda (ARGS) BODY)");
    Expression2 andTemplate = ExpressionParser.expression2()
        .parse("(and:<t*,t> BODY)");
    Expression2 applicationTemplate = ExpressionParser.expression2()
        .parse("(FUNC VALUES)");

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
        List<Type> argTypeList = Lists.newArrayList();
        for (String freeVar : freeVars) {
          Expression2 freeVarExpression = Expression2.constant(freeVar);
          args.add(freeVarExpression);
          Type freeVarType = typeMap.get(scope.getBindingIndex(freeVar)); 
          argTypeList.add(freeVarType);
        }
        Collections.reverse(argTypeList);

        Expression2 argExpression = andTemplate.substituteInline("BODY", argTerms);
        Type argExpressionType = Type.createAtomic("t");
        if (args.size() > 0) {
          argExpression = lambdaTemplate.substituteInline("ARGS", args).substitute("BODY", argExpression);
          argExpressionType = argExpressionType.addArguments(argTypeList);
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
        Type functionType = typeMap.get(0).addArgument(argExpressionType);
        
        argExpressions.add(argExpression);
        argTypes.add(argExpressionType);
        funcExpressions.add(funcTerm);
        funcTypes.add(functionType);
      }
    }
  }

  public Expression2 getExpression() {
    return rootExpression;
  }
  
  public ExpressionNode getExpressionNode() {
    return new ExpressionNode(rootExpression, rootType, numAppliedArguments);
  }
  
  public int getNumAppliedArguments() {
    return numAppliedArguments;
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
    accumulator.add(getExpressionNode());
    
    for (int i = 0; i < substitutions.size(); i++) {
      substitutions.get(i).getAllExpressionNodes(accumulator);
    }

    for (int i = 0; i < lefts.size(); i++) {
      lefts.get(i).getAllExpressionNodes(accumulator);
      rights.get(i).getAllExpressionNodes(accumulator);
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

  public void populateBinaryRuleDistribution(TableFactorBuilder builder, DiscreteFactor binaryRuleProbs) {
    ExpressionNode root = getExpressionNode();
    if (hasChildren()) {
      List<ExpressionTree> argChildren = getLeftChildren();
      List<ExpressionTree> funcChildren = getRightChildren();
      for (int i = 0; i < argChildren.size(); i++) {
        ExpressionTree arg = argChildren.get(i);
        ExpressionTree func = funcChildren.get(i);

        // Add binary rule for this combination of expressions. Note
        // that the expressions can occur in either order in the sentence.
        // Backward application'
        double prob = binaryRuleProbs.getUnnormalizedProbability(arg.getExpressionNode(),
            func.getExpressionNode(), root, ParametricCfgAlignmentModel.APPLICATION);
        builder.setWeight(prob, arg.getExpressionNode(),
            func.getExpressionNode(), root, ParametricCfgAlignmentModel.APPLICATION);

        // Forward application
        prob = binaryRuleProbs.getUnnormalizedProbability(func.getExpressionNode(),
            arg.getExpressionNode(), root, ParametricCfgAlignmentModel.APPLICATION);
        builder.setWeight(prob, func.getExpressionNode(),
            arg.getExpressionNode(), root, ParametricCfgAlignmentModel.APPLICATION);
        
        // Populate children
        arg.populateBinaryRuleDistribution(builder, binaryRuleProbs);
        func.populateBinaryRuleDistribution(builder, binaryRuleProbs);
      }
    }

    // Add word-skipping rules.
    double prob = binaryRuleProbs.getUnnormalizedProbability(ParametricCfgAlignmentModel.SKIP_EXPRESSION, root,
         root, ParametricCfgAlignmentModel.SKIP_LEFT);
    builder.setWeight(prob, ParametricCfgAlignmentModel.SKIP_EXPRESSION, root,
         root, ParametricCfgAlignmentModel.SKIP_LEFT);
    prob = binaryRuleProbs.getUnnormalizedProbability(root, ParametricCfgAlignmentModel.SKIP_EXPRESSION,
        root, ParametricCfgAlignmentModel.SKIP_RIGHT);
    builder.setWeight(prob, root, ParametricCfgAlignmentModel.SKIP_EXPRESSION,
        root, ParametricCfgAlignmentModel.SKIP_RIGHT);

    List<ExpressionTree> substitutions = getSubstitutions();
    for (int i = 0; i < substitutions.size(); i++) {
      substitutions.get(i).populateBinaryRuleDistribution(builder, binaryRuleProbs);
    }
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
    sb.append(":");
    sb.append(tree.rootType);
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
    private final Type type;
    private final int numAppliedArguments;

    public ExpressionNode(Expression2 expression, Type type, int numAppliedArguments) {
      this.expression = Preconditions.checkNotNull(expression);
      this.type = Preconditions.checkNotNull(type);
      this.numAppliedArguments = numAppliedArguments;
    }

    public Expression2 getExpression() {
      return expression;
    }

    public Expression2 getExpressionTemplate(TypeDeclaration typeDeclaration, int maxDepth) {
      Map<Integer, Type> locTypeMap = StaticAnalysis.inferTypeMap(expression, type, typeDeclaration);

      ScopeSet scopes = StaticAnalysis.getScopes(expression);
      Expression2 uncanonicalTemplate = toTemplate(expression, scopes, locTypeMap, 0, 0, maxDepth);
      
      ExpressionSimplifier simplifier = new ExpressionSimplifier(Arrays.
        <ExpressionReplacementRule>asList(new LambdaApplicationReplacementRule(),
            new VariableCanonicalizationReplacementRule(),
            new CommutativeReplacementRule("<t,t>"),
            new CommutativeReplacementRule("<t,<t,t>>"),
            new CommutativeReplacementRule("<t,<t,<t,t>>>"),
            new CommutativeReplacementRule("<t,<t,<t,<t,t>>>>"),
            new CommutativeReplacementRule("<t,<t,<t,<t,<t,t>>>>>")));

      return simplifier.apply(uncanonicalTemplate);
      /*
      Collection<String> freeVars = StaticAnalysis.getFreeVariables(expression);
      Expression2 result = expression;
      for (String freeVar : freeVars) {
        result = result.substitute(freeVar, "c");
      }
      return result;
      */
    }
    
    private static Expression2 toTemplate(Expression2 expression, ScopeSet scopes, Map<Integer, Type> locTypeMap,
        int index, int depth, int maxDepth) {
      Expression2 subexpression = expression.getSubexpression(index);
      Scope scope = scopes.getScope(index);

      if (subexpression.isConstant()) {
        if (subexpression.getConstant().equals(StaticAnalysis.LAMBDA) || scope.isBound(subexpression.getConstant())) {
          return subexpression;
        } else {
          return Expression2.constant(locTypeMap.get(index).toString());
        }
      } else {
        if (depth > maxDepth) {
          Set<String> freeVars = Sets.newHashSet(StaticAnalysis.getFreeVariables(subexpression));
          Set<String> usedBoundVars = Sets.newHashSet(freeVars);
          usedBoundVars.retainAll(scope.getBoundVariables());
          freeVars.removeAll(usedBoundVars);

          Type type = locTypeMap.get(index);
          if (usedBoundVars.size() == 0) {
            return Expression2.constant(type.toString());
          } else if (freeVars.size() == 0) {
            return subexpression;
          } else {
            List<String> sortedBoundVars = Lists.newArrayList(usedBoundVars);
            Collections.sort(sortedBoundVars);

            // Generate a type for the function that takes the arguments and
            // returns the appropriate type.
            for (int i = sortedBoundVars.size() -  1; i >= 0; i--) {
              String sortedBoundVar = sortedBoundVars.get(i);
              type = type.addArgument(locTypeMap.get(scope.getBindingIndex(sortedBoundVar)));
            }

            List<Expression2> components = Lists.newArrayList();
            components.add(Expression2.constant(type.toString()));
            components.addAll(Expression2.constants(sortedBoundVars));

            return Expression2.nested(components);
          }
        }

        // Recursively generate a template from this expression's children.
        int[] children = expression.getChildIndexes(index);
        // Only non-lambda expressions count toward depth.
        int nextDepth = depth;
        if (!StaticAnalysis.isLambda(expression, index)) {
          nextDepth++;
        }

        List<Expression2> templates = Lists.newArrayList(); 
        for (int i = 0; i < children.length; i++) {
          templates.add(toTemplate(expression, scopes, locTypeMap, children[i], nextDepth, maxDepth));
        }
        return Expression2.nested(templates);
      }
    }

    public Type getType() {
      return type;
    }

    public int getNumAppliedArguments() {
      return numAppliedArguments;
    }

    @Override
    public String toString() {
      return numAppliedArguments + ":" + expression + " : " + type;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((expression == null) ? 0 : expression.hashCode());
      result = prime * result + numAppliedArguments;
      result = prime * result + ((type == null) ? 0 : type.hashCode());
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
      if (type == null) {
        if (other.type != null)
          return false;
      } else if (!type.equals(other.type))
        return false;
      return true;
    }
  }
}
