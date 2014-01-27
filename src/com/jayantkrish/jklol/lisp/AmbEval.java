package com.jayantkrish.jklol.lisp;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;
import com.jayantkrish.jklol.inference.DualDecomposition;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MarginalCalculator;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
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
      } else if (constantString.matches("^-?[0-9]+$")) {
        // Integer primitive type
        int intValue = Integer.parseInt(constantString);
        primitiveValue = intValue;
      } else if (constantString.matches("^-?[0-9]+\\.[0-9]*$")) {
        double doubleValue = Double.parseDouble(constantString);
        primitiveValue = doubleValue;
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
          Preconditions.checkArgument(subexpressions.size() >= 3, "Invalid lambda expression arguments: " + subexpressions);

          List<String> argumentNames = Lists.newArrayList();
          List<SExpression> argumentExpressions = subexpressions.get(1).getSubexpressions();
          for (SExpression argumentExpression : argumentExpressions) {
            Preconditions.checkArgument(argumentExpression.isConstant());
            argumentNames.add(argumentExpression.getConstant());
          }

          List<SExpression> functionBodyComponents = Lists.newArrayList();
          functionBodyComponents.add(SExpression.constant("begin"));
          functionBodyComponents.addAll(subexpressions.subList(2, subexpressions.size()));

          SExpression functionBody = SExpression.nested(functionBodyComponents); 
          return new EvalResult(new LambdaValue(argumentNames, functionBody, environment));
        } else if (constantName.equals("if")) {
          Preconditions.checkArgument(subexpressions.size() == 4);
          Object testCondition = eval(subexpressions.get(1), environment).getValue();
          Preconditions.checkState(!(testCondition instanceof AmbValue));

          if (ConstantValue.TRUE.equals(testCondition)) {
            return eval(subexpressions.get(2), environment);
          } else {
            return eval(subexpressions.get(3), environment);
          }
        } else if (constantName.equals("amb")) {
          Preconditions.checkArgument(subexpressions.size() >= 2 && subexpressions.size() <= 3);

          List<Object> possibleValues = ConsValue.consListToList(eval(subexpressions.get(1), environment)
              .getValue(), Object.class);
          List<Number> weights;
          if (subexpressions.size() > 2) {
            weights = ConsValue.consListToList(eval(subexpressions.get(2), environment)
                .getValue(), Number.class);
          } else {
            weights = Collections.<Number>nCopies(possibleValues.size(), 1);
          }

          String varName = subexpressions.get(1).toString();
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
          Preconditions.checkArgument(subexpressions.size() == 2 || subexpressions.size() == 3);
          Object value = eval(subexpressions.get(1), environment).getValue();
          
          // Second, optional argument selects the inference algorithm.
          String inferenceAlgString = "junction-tree";
          if (subexpressions.size() == 3) {
            inferenceAlgString = (String) eval(subexpressions.get(2), environment).getValue();
          }

          if (value instanceof AmbValue) {
            ParametricFactorGraph currentFactorGraph = environment.getFactorGraphBuilder().build();
            FactorGraph fg = currentFactorGraph.getModelFromParameters(
                currentFactorGraph.getNewSufficientStatistics()).conditional(DynamicAssignment.EMPTY);

            System.out.println("factor graph: " + fg.getParameterDescription());

            MarginalCalculator inferenceAlg = null;
            if (inferenceAlgString.equals("junction-tree")) {
              inferenceAlg = new JunctionTree();
            } else if (inferenceAlgString.equals("dual-decomposition")) {
              inferenceAlg = new DualDecomposition(100);
            } else {
              throw new IllegalArgumentException("Unsupported inference algorithm: " + inferenceAlgString);
            }
            MaxMarginalSet maxMarginals = inferenceAlg.computeMaxMarginals(fg);
            Assignment assignment = maxMarginals.getNthBestAssignment(0);

            return new EvalResult(assignment.getValue(((AmbValue) value).getVar().getOnlyVariableNum())); 
          } else {
            return new EvalResult(value);
          }
        } else if (constantName.equals("get-marginals")) {
          Preconditions.checkArgument(subexpressions.size() == 2 || subexpressions.size() == 3);
          Object value = eval(subexpressions.get(1), environment).getValue();

          // Second, optional argument selects the inference algorithm.
          String inferenceAlgString = "junction-tree";
          if (subexpressions.size() == 3) {
            inferenceAlgString = (String) eval(subexpressions.get(2), environment).getValue();
          }

          if (value instanceof AmbValue) {
            ParametricFactorGraph currentFactorGraph = environment.getFactorGraphBuilder().build();
            FactorGraph fg = currentFactorGraph.getModelFromParameters(
                currentFactorGraph.getNewSufficientStatistics()).conditional(DynamicAssignment.EMPTY);

            System.out.println("factor graph: " + fg.getParameterDescription());
            
            MarginalCalculator inferenceAlg = null;
            if (inferenceAlgString.equals("junction-tree")) {
              inferenceAlg = new JunctionTree();
            } else {
              throw new IllegalArgumentException("Unsupported inference algorithm: " + inferenceAlgString);
            }
            MarginalSet marginals = inferenceAlg.computeMarginals(fg);
            DiscreteFactor varMarginal = marginals.getMarginal(((AmbValue) value).getVar().getOnlyVariableNum())
                .coerceToDiscrete();

            Iterator<Outcome> iter = varMarginal.outcomeIterator();
            List<Object> outcomes = Lists.newArrayList();
            List<Double> weights = Lists.newArrayList();
            while (iter.hasNext()) {
              Outcome outcome = iter.next();
              outcomes.add(outcome.getAssignment().getOnlyValue());
              weights.add(outcome.getProbability());
            }
            
            System.out.println(varMarginal.getParameterDescription());
            Object outcomesConsList = ConsValue.listToConsList(outcomes);
            Object weightsConsList = ConsValue.listToConsList(weights);

            return new EvalResult(new ConsValue(outcomesConsList, weightsConsList)); 
          } else {
            return new EvalResult(value);
          }
        } else if (constantName.equals("add-weight")) {
          Preconditions.checkArgument(subexpressions.size() == 3);
          Object value = eval(subexpressions.get(1), environment).getValue();
          double weight = ((Number) eval(subexpressions.get(2), environment).getValue()).doubleValue();

          if (value instanceof AmbValue) {
            VariableNumMap fgVar = ((AmbValue) value).getVar();

            TableFactorBuilder builder = TableFactorBuilder.ones(fgVar);
            builder.setWeight(weight, ConstantValue.TRUE); 
            TableFactor factor = builder.build();
            environment.getFactorGraphBuilder().addConstantFactor(fgVar.getOnlyVariableName(), factor);
          } 
          return new EvalResult(ConstantValue.UNDEFINED);
        }
      }

      return doFunctionApplication(subexpressions, environment);
    }
  }

  public EvalResult doFunctionApplication(List<SExpression> subexpressions, Environment environment) {
    List<Object> values = Lists.newArrayList();
    for (SExpression expression : subexpressions) {
      values.add(eval(expression, environment).getValue());
    }

    Object functionObject = values.get(0);
    List<Object> argumentValues = values.subList(1, values.size());
    if (functionObject instanceof FunctionValue) {
      FunctionValue function = (FunctionValue) functionObject;
      return new EvalResult(function.apply(argumentValues, environment, this));
    } else if (functionObject instanceof AmbValue) {
      AmbValue functionAmb = ((AmbValue) functionObject);
      List<Object> possibleFunctionObjects = functionAmb.getPossibleValues();
      List<Object> functionResults = Lists.newArrayList();
      List<Object> possibleReturnValues = Lists.newArrayList();
      for (Object possibleFunctionObject : possibleFunctionObjects) {
        FunctionValue function = (FunctionValue) possibleFunctionObject;
        Object result = function.apply(argumentValues, environment, this);
        functionResults.add(result);

        if (result instanceof AmbValue) {
          possibleReturnValues.addAll(((AmbValue) result).getPossibleValues());
        } else {
          possibleReturnValues.add(result);
        }
      }
      
      if (possibleReturnValues.size() == 1) {
        // Although there are possibly many functions being run,
        // only a single return value is possible in every case.
        return new EvalResult(Iterables.getOnlyElement(possibleReturnValues));
      }

      String varName = Integer.toHexString(possibleReturnValues.hashCode());
      DiscreteVariable varType = new DiscreteVariable(varName, possibleReturnValues);
      VariableNumMap returnValueVar = VariableNumMap.singleton(getUniqueVarNum(), varName, varType);
      environment.getFactorGraphBuilder().addVariables(returnValueVar);
      VariableNumMap functionVar = functionAmb.getVar();

      for (int i = 0; i < possibleFunctionObjects.size(); i++) {
        Object function = possibleFunctionObjects.get(i);
        Object returnValue = functionResults.get(i);
        Assignment functionAssignment = functionVar.outcomeArrayToAssignment(function);

        if (returnValue instanceof AmbValue) {
          AmbValue returnValueAmb = (AmbValue) returnValue;
          VariableNumMap functionReturnValueVar = returnValueAmb.getVar();
          TableFactorBuilder builder = TableFactorBuilder.ones(VariableNumMap
              .unionAll(returnValueVar, functionVar, functionReturnValueVar));

          builder.incrementWeight(TableFactor.pointDistribution(functionVar, functionAssignment)
              .outerProduct(TableFactor.unity(returnValueVar)).outerProduct(
                  TableFactor.unity(functionReturnValueVar)).product(-1.0));

          for (Object returnValueObject : returnValueAmb.getPossibleValues()) {
            Assignment functionReturnAssignment = functionReturnValueVar.outcomeArrayToAssignment(returnValueObject);
            Assignment returnAssignment = returnValueVar.outcomeArrayToAssignment(returnValueObject);
            builder.setWeight(Assignment.unionAll(functionReturnAssignment, returnAssignment, functionAssignment), 1.0);
          }

          environment.getFactorGraphBuilder().addConstantFactor(varName, builder.build());
        } else {
          TableFactorBuilder builder = TableFactorBuilder.ones(returnValueVar.union(functionVar));
          Assignment returnValueAssignment = returnValueVar.outcomeArrayToAssignment(returnValue);
          builder.incrementWeight(TableFactor.pointDistribution(functionVar, functionAssignment)
              .outerProduct(TableFactor.unity(returnValueVar)).product(-1.0));
          builder.setWeight(returnValueAssignment.union(functionAssignment), 1.0);

          environment.getFactorGraphBuilder().addConstantFactor(varName, builder.build());
        }
      }

      return new EvalResult(new AmbValue(returnValueVar));
    } else {
      throw new IllegalArgumentException("Tried applying a non-function value: " + functionObject
          + "\n subexpressions: " + subexpressions);
    }
  }

  private static int getUniqueVarNum() {
    return nextVarNum++;
  }

  public static Environment getDefaultEnvironment() {
    Environment env = Environment.empty();
    env.bindName("cons", new RaisedBuiltinFunction(new BuiltinFunctions.ConsFunction()));
    env.bindName("car", new RaisedBuiltinFunction(new BuiltinFunctions.CarFunction()));
    env.bindName("cdr", new RaisedBuiltinFunction(new BuiltinFunctions.CdrFunction()));
    env.bindName("list", new RaisedBuiltinFunction(new BuiltinFunctions.ListFunction()));
    env.bindName("nil?", new RaisedBuiltinFunction(new BuiltinFunctions.NilFunction()));
    env.bindName("+", new RaisedBuiltinFunction(new BuiltinFunctions.PlusFunction()));
    env.bindName("-", new RaisedBuiltinFunction(new BuiltinFunctions.MinusFunction()));
    env.bindName("=", new RaisedBuiltinFunction(new BuiltinFunctions.EqualsFunction()));
    env.bindName("not", new RaisedBuiltinFunction(new BuiltinFunctions.NotFunction()));
    env.bindName("and", new RaisedBuiltinFunction(new BuiltinFunctions.AndFunction()));
    env.bindName("or", new RaisedBuiltinFunction(new BuiltinFunctions.OrFunction()));
    env.bindName("display", new BuiltinFunctions.DisplayFunction());
    return env;
  }

  private static class RaisedBuiltinFunction implements FunctionValue {
    private final FunctionValue baseFunction;

    public RaisedBuiltinFunction(FunctionValue baseFunction) {
      this.baseFunction = baseFunction;
    }

    @Override
    public Object apply(List<Object> argumentValues, Environment env, Eval eval) {
      // Default case: perform function application.
      List<List<Object>> inputVarValues = Lists.newArrayList();
      List<VariableNumMap> inputVars = Lists.newArrayList();
      VariableNumMap ambVars = VariableNumMap.EMPTY;
      int[] sizes = new int[argumentValues.size()];
      for (int i = 0; i < argumentValues.size(); i++) {
        Object value = argumentValues.get(i);
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

        Object result = baseFunction.apply(chosenValues, env, eval);
        if (result instanceof AmbValue) {
          possibleValues.addAll(((AmbValue) result).getPossibleValues());
        } else {
          possibleValues.add(result);
        }
      }

      if (possibleValues.size() == 1) {
        return Iterables.getOnlyElement(possibleValues);
      }

      String varName = Integer.toHexString(possibleValues.hashCode());
      DiscreteVariable fgVarType = new DiscreteVariable(varName, possibleValues);
      VariableNumMap fgVar = VariableNumMap.singleton(getUniqueVarNum(), varName, fgVarType);
      env.getFactorGraphBuilder().addVariables(fgVar);

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

        Object result = baseFunction.apply(chosenValues, env, eval);
        if (result instanceof AmbValue) {
          Preconditions.checkState(false, "Probabilistic functions not yet supported.");
        } else {
          assignment = assignment.union(fgVar.outcomeArrayToAssignment(result));
          builder.setWeight(assignment, 1.0);
        }
      }

      TableFactor factor = builder.build();
      env.getFactorGraphBuilder().addConstantFactor(varName, factor);

      return new AmbValue(fgVar);
    }
  }
}
