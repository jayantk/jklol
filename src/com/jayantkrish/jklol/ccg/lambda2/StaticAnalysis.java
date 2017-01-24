package com.jayantkrish.jklol.ccg.lambda2;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;

/**
 * Static analysis of lambda calculus expressions. Computes
 * the variables that are free / bound at every program 
 * location, as well as the types of expressions. 
 * 
 * @author jayant
 *
 */
public class StaticAnalysis {
  
  public static final String LAMBDA = "lambda";
  public static final String QUOTE = "quote";

  private StaticAnalysis() {
    // Prevent instantiation
  }

  /**
   * Gets all free variables in {@code expression}, i.e.,
   * variables whose values are determined by the environment
   * in which the expression is evaluated.
   *  
   * @param expression
   * @return
   */
  public static Set<String> getFreeVariables(Expression2 expression) {
    Set<String> freeVariables = Sets.newHashSet();
    ScopeSet scopes = getScopes(expression);
    for (int i = 0; i < expression.size(); i++) {
      Scope scope = scopes.getScope(i);
      String varName = getFreeVariable(expression, i, scope);
      if (varName != null) {
        freeVariables.add(varName);
      }
    }
    return freeVariables;
  }
  
  /**
   * If {@code index} points to a free variable in {@code expression},
   * return its name. Otherwise, returns null.
   * 
   * @param expression
   * @param index
   * @param scope
   * @return
   */
  private static String getFreeVariable(Expression2 expression, int index, Scope scope) {
    Expression2 sub = expression.getSubexpression(index);
    if (sub.isConstant()) {
      String constant = sub.getConstant();
      if (!constant.equals(LAMBDA) && !scope.isBound(constant)) {
        return sub.getConstant();
      } 
    }
    return null;
  }
  
  /**
   * Gets the set of indexes where {@code variableName} appears
   * as a free variable in {@code expression}.
   * 
   * @code expression
   * @param variableName
   * @return
   */
  public static int[] getIndexesOfFreeVariable(Expression2 expression, String variableName) {
    List<Integer> indexes = Lists.newArrayList();
    ScopeSet scopes = getScopes(expression);
    for (int i = 0; i < expression.size(); i++) {
      Scope scope = scopes.getScope(i);
      String varName = getFreeVariable(expression, i, scope);
      if (varName != null && varName.equals(variableName)) {
        indexes.add(i);
      }
    }
    return Ints.toArray(indexes);
  }

  /**
   * Gets the scope of {@code expression} in which the
   * subexpression at {@code index} is evaluated. This scope
   * includes the bound variables, etc.
   * 
   * @param expression
   * @param index
   * @return
   */
  public static Scope getEnclosingScope(Expression2 expression, int index) {
    Preconditions.checkArgument(index < expression.size());
    ScopeSet scopes = getScopes(expression);
    return scopes.getScope(index);
  }

  public static ScopeSet getScopes(Expression2 expression) {
    Scope scope = new Scope(0, expression.size(), Maps.<String,Integer>newHashMap(), null);
    List<Scope> allCreatedScopes = Lists.newArrayList();
    allCreatedScopes.add(scope);
    for (int i = 0; i < expression.size(); i++) {
      while (i >= scope.end) {
        scope = scope.getParent();
      }

      Expression2 sub = expression.getSubexpression(i);
      if (sub.isConstant()) {
        String constant = sub.getConstant();
        if (constant.equals(LAMBDA)) {
          // Read off the variables and add them to a static scope.
          Expression2 lambdaExpression = expression.getSubexpression(i - 1);
          // The nested expression after the lambda keyword contains the arguments.
          int argExpressionIndex = i + 1;
          Expression2 argExpression = expression.getSubexpression(argExpressionIndex);
          Preconditions.checkState(!argExpression.isConstant(),
              "Ill-formed lambda expression: %s", lambdaExpression);
          
          List<Expression2> subexpressions = argExpression.getSubexpressions();
          
          Map<String, Integer> bindings = Maps.newHashMap();
          for (int j = 0; j < subexpressions.size(); j++) {
            Preconditions.checkState(subexpressions.get(j).isConstant(),
                "Illegal lambda expression %s", lambdaExpression);
            bindings.put(subexpressions.get(j).getConstant(), argExpressionIndex + j + 1);
          }

          int scopeStart = i;
          int scopeEnd = i + lambdaExpression.size() - 1;

          scope = new Scope(scopeStart, scopeEnd, bindings, scope);
          allCreatedScopes.add(scope);
        }
      }
    }

    return new ScopeSet(allCreatedScopes);
  }
  
  /**
   * Returns {@code true} if {@code expression} is of the form
   * (quote ...).
   *  
   * @param expression
   * @return
   */
  public static boolean isQuote(Expression2 expression) {
    return isQuote(expression, 0);
  }

  public static boolean isQuote(Expression2 expression, int index) {
    Expression2 subexpression = expression.getSubexpression(index);
    return !subexpression.isConstant() && subexpression.getSubexpression(1).isConstant() && 
        subexpression.getSubexpression(1).getConstant().equals(QUOTE);
  }

  public static boolean isLambda(Expression2 expression) {
    return isLambda(expression, 0);
  }

  public static boolean isLambda(Expression2 expression, int index) {
    Expression2 subexpression = expression.getSubexpression(index);
    return !subexpression.isConstant() && subexpression.getSubexpression(1).isConstant() && 
        subexpression.getSubexpression(1).getConstant().equals(LAMBDA);
  }
  
  public static List<String> getLambdaArguments(Expression2 expression) {
    return getLambdaArguments(expression, 0);
  }
  
  public static List<String> getLambdaArguments(Expression2 expression, int index) {
    Preconditions.checkArgument(isLambda(expression, index));

    int[] children = getLambdaArgumentIndexes(expression, index);
    List<String> args = Lists.newArrayList();
    for (int i = 0; i < children.length; i++) {
      args.add(expression.getSubexpression(children[i]).getConstant());
    }
    return args;
  }
  
  public static int[] getLambdaArgumentIndexes(Expression2 expression, int index) {
    Preconditions.checkArgument(isLambda(expression, index));
    // index + 2 is the nested expression containing argument names.
    return expression.getChildIndexes(index + 2);
  }

  public static Expression2 getLambdaBody(Expression2 expression) {
    return getLambdaBody(expression, 0);
  }

  public static Expression2 getLambdaBody(Expression2 expression, int index) {
    return expression.getSubexpression(getLambdaBodyIndex(expression, index));
  }
  
  public static int getLambdaBodyIndex(Expression2 expression, int index) {
    Preconditions.checkArgument(isLambda(expression, index));
    int[] children = expression.getChildIndexes(index);
    return children[children.length - 1];
  }
  
  public static boolean isPartOfSpecialForm(Expression2 expression, Scope scope, int index) {
    if (scope.getParent() == null) {
      return false;
    } else {
      // This is a hacky way to detect if index points to a lambda or its arguments
      int lambdaIndex = scope.getStart() - 1;
      int[] childIndexes = expression.getChildIndexes(lambdaIndex);
      return index < childIndexes[childIndexes.length - 1];
    }
  }

  public static Type inferType(Expression2 expression, TypeDeclaration typeDeclaration) {
    return inferType(expression, TypeDeclaration.TOP, typeDeclaration);
  }

  /**
   * Implementation of type inference that infers types for
   * expressions using the basic type information in typeDeclaration.
   * 
   * @param expression
   * @param rootType
   * @param typeDeclaration
   * @return
   */
  public static Type inferType(Expression2 expression, Type rootType, TypeDeclaration typeDeclaration) {
    Map<Integer, Type> subexpressionTypeMap = inferTypeMap(expression, rootType, typeDeclaration);
    return subexpressionTypeMap.get(0);
  }

  public static Map<Integer, Type> inferTypeMap(Expression2 expression, Type rootType,
      TypeDeclaration typeDeclaration) {
    TypeInference inference = typeInference(expression, rootType, typeDeclaration);
    // TODO: figure out a better interface for error handling here.
    Preconditions.checkState(inference.getSolvedConstraints().isSolvable(),
        "Could not type %s", expression);

    return inference.getExpressionTypes();
  }

  public static TypeInference typeInference(Expression2 expression, Type rootType,
      TypeDeclaration typeDeclaration) {
    TypeInference inference = new TypeInference(expression, typeDeclaration);
    inference.infer();
    return inference;
  }

  public static String getNewVariableName(Expression2... expressions) {
    return getNewVariableNames(1, expressions).get(0);
  }

  public static List<String> getNewVariableNames(int num, Expression2... expressions) {
    List<String> names = Lists.newArrayList();
    Expression2 combined = Expression2.nested(expressions);

    // TODO: do this in a canonical way.
    String varName = null;
    for (int i = 0; i < num; i++) {
      do {
        int random = (int) (Math.random() * 1000000.0);
        varName = "var" + random;
      } while (combined.hasSubexpression(Expression2.constant(varName)) || names.contains(varName));
      names.add(varName);
    }
    return names;
  }

  public static class Scope {
    // Index in the program of the first expression within an
    // expression that defines a new scope. For example, in:
    // (foo (lambda (x) body))
    // a scope would start at index 3 and end at 7.
    private final int start;
    // End of the expression that defines a new scope.
    // Exclusive index.
    private final int end;
    
    private final Map<String, Integer> bindings;

    private final Scope parent;

    public Scope(int start, int end, Map<String, Integer> bindings, Scope parent) {
      this.start = start;
      this.end = end;
      this.bindings = Maps.newHashMap(bindings);
      this.parent = parent;
    }

    public boolean isBound(String variable) {
      return getBindingIndex(variable) != -1;
    }
    
    /**
     * Gets the index in the program where variable occurs
     * in the binding expression that added to to this scope.
     * Returns -1 if there is no such binding.
     *  
     * @param variable
     * @return
     */
    public int getBindingIndex(String variable) {
      if (bindings.containsKey(variable)) {
        return bindings.get(variable);
      } else if (parent != null) {
        return parent.getBindingIndex(variable);
      } else {
        return -1;
      }
    }

    public Scope getParent() {
      return parent;
    }

    public int getStart() {
      return start;
    }

    public int getEnd() {
      return end;
    }

    public int getDepth() {
      if (parent != null) {
        return 1 + parent.getDepth();
      } else {
        return 0;
      }
    }

    /**
     * Gets all variables bound in this scope, including
     * those bound in its parents.
     * 
     * @return
     */
    public Set<String> getBoundVariables() {
      Set<String> allBindings = Sets.newHashSet();
      if (parent != null) {
        allBindings.addAll(parent.getBoundVariables());
      } 
      allBindings.addAll(bindings.keySet());
      return allBindings;
    }
    
    public int getNumBindings() {
      int num = bindings.size();
      if (parent != null) {
        num += parent.getNumBindings();
      }
      return num;
    }
  }

  public static class ScopeSet {
    private final List<Scope> scopes;
    
    public ScopeSet(List<Scope> scopes) {
      this.scopes = ImmutableList.copyOf(scopes);
    }
    
    public Scope getScope(int index) {
      int minSize = Integer.MAX_VALUE;
      Scope best = null;
      for (Scope scope : scopes) {
        if (scope.getStart() <= index && index < scope.getEnd() && 
            scope.getEnd() - scope.getStart() < minSize) {
          best = scope;
          minSize = scope.getEnd() - scope.getStart();
        }
      }
      return best;
    }
    
    public List<Scope> getScopes() {
      return scopes;
    }
  }
}
