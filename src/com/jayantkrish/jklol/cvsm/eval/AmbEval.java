package com.jayantkrish.jklol.cvsm.eval;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

public class AmbEval implements Eval {

  private static int nextVarNum = 0;

  public EvalResult eval(SExpression expression, Environment environment) {
    if (expression.isConstant()) {
      // The expression may be a primitive type or a variable.
      Object primitiveValue = null;
      String constantString = expression.getConstant();
      if (constantString.startsWith("\"") && constantString.endsWith("\"")) {
        String strippedQuotes = constantString.substring(1, constantString.length() - 1);
        primitiveValue = strippedQuotes;
      } else if (constantString.matches("-?[0-9]+")) {
        // Integer primitive type
        int intValue = Integer.parseInt(constantString);
        primitiveValue = intValue;
      } 
      
      if (primitiveValue != null) {
        return new EvalResult(primitiveValue);
      } else {
        // Variable name
        return new EvalResult(environment.getValue(constantString));
      }
    } else {
      List<SExpression> subexpressions = expression.getSubexpressions();
      SExpression first = subexpressions.get(0);
      if (first.isConstant()) {
        String constantName = first.getConstant();

        // Check for syntactic special forms (define, lambda, etc.)
        if (constantName.equals("define")) {
          // Binds a name to a value in the environment.
          String nameToBind = subexpressions.get(1).getConstant();
          Object valueToBind = eval(subexpressions.get(2), environment).getValue();
          environment.bindName(nameToBind, valueToBind);
          return new EvalResult(ConstantValue.UNDEFINED);
        } else if (constantName.equals("begin")) {
          // Sequentially evaluates its subexpressions, chaining any 
          // environment changes.
          EvalResult result = new EvalResult(ConstantValue.UNDEFINED);
          for (int i = 1; i < subexpressions.size(); i++) {
            result = eval(subexpressions.get(i), environment);
          }
          return result;
        } else if (constantName.equals("lambda")) {
          // Create and return a function value representing this function.
          Preconditions.checkArgument(subexpressions.size() == 3);

          List<String> argumentNames = Lists.newArrayList();
          List<SExpression> argumentExpressions = subexpressions.get(1).getSubexpressions();
          for (SExpression argumentExpression : argumentExpressions) {
            Preconditions.checkArgument(argumentExpression.isConstant());
            argumentNames.add(argumentExpression.getConstant());
          }

          SExpression functionBody = subexpressions.get(2); 
          return new EvalResult(new LambdaValue(argumentNames, functionBody, environment));
        } else if (constantName.equals("amb")) {
          Preconditions.checkArgument(subexpressions.size() == 3);

          List<Object> possibleValues = consListToList(eval(subexpressions.get(1), environment)
              .getValue(), Object.class);
          List<Integer> weights = consListToList(eval(subexpressions.get(2), environment)
              .getValue(), Integer.class);

          String varName = Integer.toHexString(possibleValues.hashCode());
          DiscreteVariable fgVarType = new DiscreteVariable(varName, possibleValues);
          VariableNumMap fgVar = VariableNumMap.singleton(getUniqueVarNum(), varName, fgVarType);
          environment.getFactorGraphBuilder().addVariables(fgVar);

          Assignment[] assignmentArray = new Assignment[possibleValues.size()];
          double[] weightArray = Doubles.toArray(weights);
          for (int i = 0; i < possibleValues.size(); i++) {
            assignmentArray[i] = fgVar.outcomeArrayToAssignment(possibleValues.get(i));
          }
          TableFactor factor = TableFactor.vector(fgVar, assignmentArray, weightArray);
          environment.getFactorGraphBuilder().addConstantFactor(varName, factor);

          return new EvalResult(new AmbValue(fgVar));
        } else if (constantName.equals("get-best-assignment")) {
          Preconditions.checkArgument(subexpressions.size() == 2);

          Object value = eval(subexpressions.get(1), environment).getValue();

          if (value instanceof AmbValue) {
            ParametricFactorGraph currentFactorGraph = environment.getFactorGraphBuilder().build();
            FactorGraph fg = currentFactorGraph.getModelFromParameters(
                currentFactorGraph.getNewSufficientStatistics()).conditional(DynamicAssignment.EMPTY);

            JunctionTree jt = new JunctionTree();
            MaxMarginalSet maxMarginals = jt.computeMaxMarginals(fg);
            Assignment assignment = maxMarginals.getNthBestAssignment(0);

            return new EvalResult(assignment.getValue(((AmbValue) value).getVar().getOnlyVariableNum())); 
          } else {
            return new EvalResult(value);
          }
        }
      }

      return doFunctionApplication(subexpressions, environment);
    }
  }
  
  public EvalResult doFunctionApplication(List<SExpression> subexpressions, Environment environment) {
    // Default case: perform function application.
    List<List<Object>> inputVarValues = Lists.newArrayList();
    List<VariableNumMap> inputVars = Lists.newArrayList();
    VariableNumMap ambVars = VariableNumMap.EMPTY;
    int[] sizes = new int[subexpressions.size()];
    for (int i = 0; i < subexpressions.size(); i++) {
      Object value = eval(subexpressions.get(i), environment).getValue();
      if (value instanceof AmbValue) {
        AmbValue ambValue = (AmbValue) value; 
        inputVarValues.add(ambValue.getPossibleValues());
        sizes[i] = ambValue.getPossibleValues().size();
        ambVars = ambVars.union(ambValue.getVar());
        inputVars.add(ambValue.getVar());
      } else {
        inputVarValues.add(Lists.newArrayList(value));
        sizes[i] = 1;
        inputVars.add(null);
      }
    }

    // Apply the function to every possible combination of 
    // input values.
    Set<Object> possibleValues = Sets.newHashSet();
    Iterator<int[]> allValueIterator = new IntegerArrayIterator(sizes, new int[0]);
    List<Object> chosenValues = Lists.newArrayList();
    while (allValueIterator.hasNext()) {
      int[] indexes = allValueIterator.next();
      chosenValues.clear();
      for (int i = 0; i < indexes.length; i++) {
        chosenValues.add(inputVarValues.get(i).get(indexes[i]));
      }

      FunctionValue functionToApply = (FunctionValue) chosenValues.get(0);
      List<Object> arguments = chosenValues.subList(1, chosenValues.size());
      Object result = functionToApply.apply(arguments, this);
      if (result instanceof AmbValue) {
        possibleValues.addAll(((AmbValue) result).getPossibleValues());
      } else {
        possibleValues.add(result);
      }
    }

    if (possibleValues.size() == 1) {
      return new EvalResult(Iterables.getOnlyElement(possibleValues));
    }

    String varName = Integer.toHexString(possibleValues.hashCode());
    DiscreteVariable fgVarType = new DiscreteVariable(varName, possibleValues);
    VariableNumMap fgVar = VariableNumMap.singleton(getUniqueVarNum(), varName, fgVarType);
    environment.getFactorGraphBuilder().addVariables(fgVar);

    // Construct the factor representing the function application.
    VariableNumMap factorVars = ambVars.union(fgVar);
    TableFactorBuilder builder = new TableFactorBuilder(factorVars, SparseTensorBuilder.getFactory());
    allValueIterator = new IntegerArrayIterator(sizes, new int[0]);
    while (allValueIterator.hasNext()) {
      int[] indexes = allValueIterator.next();
      chosenValues.clear();
      Assignment assignment = Assignment.EMPTY;
      for (int i = 0; i < indexes.length; i++) {
        Object chosenValue = inputVarValues.get(i).get(indexes[i]);
        chosenValues.add(chosenValue);
        if (inputVars.get(i) != null) {
          assignment = assignment.union(inputVars.get(i).outcomeArrayToAssignment(chosenValue));
        }
      }

      FunctionValue functionToApply = (FunctionValue) chosenValues.get(0);
      List<Object> arguments = chosenValues.subList(1, chosenValues.size());
      Object result = functionToApply.apply(arguments, this);
      if (result instanceof AmbValue) {
        Preconditions.checkState(false, "Probabilistic functions not yet supported.");
      } else {
        assignment = assignment.union(fgVar.outcomeArrayToAssignment(result));
        builder.setWeight(assignment, 1.0);
      }
    }

    TableFactor factor = builder.build();
    environment.getFactorGraphBuilder().addConstantFactor(varName, factor);

    return new EvalResult(new AmbValue(fgVar));
  }

  private static <T> List<T> consListToList(Object consList, Class<T> clazz) {
    List<T> accumulator = Lists.newArrayList();
    consListToListHelper(consList, accumulator, clazz);
    return accumulator;
  }

  private static <T> void consListToListHelper(Object consList, List<T> accumulator, Class<T> clazz) {
    if (ConstantValue.NIL.equals(consList)) {
      return;
    } else {
      ConsValue consValue = (ConsValue) consList;
      accumulator.add(clazz.cast(consValue.getCar()));
      consListToListHelper(consValue.getCdr(), accumulator, clazz);
    }
  }

  private static int getUniqueVarNum() {
    return nextVarNum++;
  }
}
