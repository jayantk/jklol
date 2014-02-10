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
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraphBuilder;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

public class AmbEval {

  private static int nextVarNum = 0;

  public EvalResult eval(SExpression expression) {
    return eval(expression, getDefaultEnvironment(),
        new ParametricBfgBuilder(new ParametricFactorGraphBuilder(), true));
  }

  public EvalResult eval(SExpression expression, Environment environment,
      ParametricBfgBuilder builder) {
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
      } else if (constantString.equals("#t")) {
        primitiveValue = ConstantValue.TRUE;
      } else if (constantString.equals("#f")) {
        primitiveValue = ConstantValue.FALSE;
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
          Object valueToBind = eval(subexpressions.get(2), environment, builder).getValue();
          environment.bindName(nameToBind, valueToBind);
          return new EvalResult(ConstantValue.UNDEFINED);
        } else if (constantName.equals("begin")) {
          // Sequentially evaluates its subexpressions, chaining any 
          // environment changes.
          EvalResult result = new EvalResult(ConstantValue.UNDEFINED);
          for (int i = 1; i < subexpressions.size(); i++) {
            result = eval(subexpressions.get(i), environment, builder);
          }
          return result;
        } else if (constantName.equals("lambda")) {
          // Create and return a function value representing this function.
          Preconditions.checkArgument(subexpressions.size() >= 3, "Invalid lambda expression arguments: " + subexpressions);
          Preconditions.checkArgument(subexpressions.get(1).getSubexpressions() != null, "Illegal argument list in lambda: " + subexpressions);

          List<String> argumentNames = Lists.newArrayList();
          List<SExpression> argumentExpressions = subexpressions.get(1).getSubexpressions();
          for (SExpression argumentExpression : argumentExpressions) {
            Preconditions.checkArgument(argumentExpression.isConstant());
            argumentNames.add(argumentExpression.getConstant());
          }

          SExpression functionBody = null;
          if (subexpressions.size() == 3) {
            functionBody = subexpressions.get(2);
          } else {
            List<SExpression> functionBodyComponents = Lists.newArrayList();
            functionBodyComponents.add(SExpression.constant("begin"));
            functionBodyComponents.addAll(subexpressions.subList(2, subexpressions.size()));
            functionBody = SExpression.nested(functionBodyComponents);
          }

          return new EvalResult(new AmbLambdaValue(new LambdaValue(argumentNames, functionBody, environment), this));
        } else if (constantName.equals("if")) {
          Preconditions.checkArgument(subexpressions.size() == 4);
          Object testCondition = eval(subexpressions.get(1), environment, builder).getValue();

          if (!(testCondition instanceof AmbValue)) {
            // This condition evaluates to the same value in all program 
            // executions that reach this point.
            if (ConstantValue.TRUE.equals(testCondition)) {
              return eval(subexpressions.get(2), environment, builder);
            } else {
              return eval(subexpressions.get(3), environment, builder);
            }
          } else {
            // Some program executions evaluate the test condition to true,
            // and others evaluate the condition to false. Create a branch 
            // in the graphical model and execute each component of the if
            // body in the corresponding builder.
            AmbValue testConditionAmb = (AmbValue) testCondition;
            VariableNumMap ambVar = testConditionAmb.getVar();
            Assignment trueAssignment = ambVar.outcomeArrayToAssignment(ConstantValue.TRUE);
            ParametricBfgBuilder trueBuilder = builder.createChild(testConditionAmb.getVar(),
                trueAssignment);
            EvalResult trueResult = eval(subexpressions.get(2), environment, trueBuilder);

            Assignment falseAssignment = testConditionAmb.getVar()
                .outcomeArrayToAssignment(ConstantValue.FALSE);
            ParametricBfgBuilder falseBuilder = builder.createChild(testConditionAmb.getVar(),
                falseAssignment);
            EvalResult falseResult = eval(subexpressions.get(3), environment, falseBuilder);

            // The return value of the if statement is 
            Object trueValue = trueResult.getValue();
            Object falseValue = falseResult.getValue();
            Preconditions.checkArgument(!(trueValue instanceof AmbValue) &&
                !(falseValue instanceof AmbValue));
            
            List<Object> returnValues = Lists.newArrayList(trueValue, falseValue);
            String varName = Integer.toHexString(returnValues.hashCode());
            DiscreteVariable returnVarType = new DiscreteVariable(varName, returnValues);
            VariableNumMap returnValueVar = VariableNumMap.singleton(getUniqueVarNum(), varName,
                returnVarType);
            builder.addVariables(returnValueVar);

            TableFactorBuilder tfBuilder = new TableFactorBuilder(ambVar.union(returnValueVar),
                SparseTensorBuilder.getFactory());
            tfBuilder.setWeight(ambVar.outcomeArrayToAssignment(ConstantValue.TRUE)
                .union(returnValueVar.outcomeArrayToAssignment(trueValue)), 1.0);
            tfBuilder.setWeight(ambVar.outcomeArrayToAssignment(ConstantValue.FALSE)
                .union(returnValueVar.outcomeArrayToAssignment(falseValue)), 1.0);

            builder.addConstantFactor(varName, tfBuilder.build());
            return new EvalResult(new AmbValue(returnValueVar));
          }
        } else if (constantName.equals("amb")) {
          Preconditions.checkArgument(subexpressions.size() >= 2 && subexpressions.size() <= 3);

          List<Object> possibleValues = ConsValue.consListToList(eval(subexpressions.get(1), environment, builder)
              .getValue(), Object.class);
          List<Number> weights;
          if (subexpressions.size() > 2) {
            weights = ConsValue.consListToList(eval(subexpressions.get(2), environment, builder)
                 .getValue(), Number.class);
          } else {
            weights = Collections.<Number>nCopies(possibleValues.size(), 1);
          }

          String varName = subexpressions.get(1).toString();
          DiscreteVariable fgVarType = new DiscreteVariable(varName, possibleValues);
          VariableNumMap fgVar = VariableNumMap.singleton(getUniqueVarNum(), varName, fgVarType);
          builder.addVariables(fgVar);

          Assignment[] assignmentArray = new Assignment[possibleValues.size()];
          double[] weightArray = Doubles.toArray(weights);
          for (int i = 0; i < possibleValues.size(); i++) {
            assignmentArray[i] = fgVar.outcomeArrayToAssignment(possibleValues.get(i));
          }
          TableFactor factor = TableFactor.vector(fgVar, assignmentArray, weightArray);
          builder.addConstantFactor(varName, factor);

          return new EvalResult(new AmbValue(fgVar));
        } else if (constantName.equals("get-best-assignment")) {
          Preconditions.checkArgument(subexpressions.size() == 2);
          Object value = eval(subexpressions.get(1), environment, builder).getValue();

          if (value instanceof AmbValue || value instanceof ConsValue) {
            BranchingFactorGraph fg = builder.build();
            // System.out.println("factor graph: " + fg.getParameterDescription());

            MaxMarginalSet maxMarginals = fg.getMaxMarginals();
            Assignment assignment = maxMarginals.getNthBestAssignment(0);

            return new EvalResult(resolveAmbValueWithAssignment(value, assignment)); 
          } else {
            return new EvalResult(value);
          }
        } else if (constantName.equals("get-marginals")) {
          Preconditions.checkArgument(subexpressions.size() == 2);
          Object value = eval(subexpressions.get(1), environment, builder).getValue();

          if (value instanceof AmbValue) {
            BranchingFactorGraph fg = builder.build();
            // System.out.println("factor graph: " + fg.getParameterDescription());

            MarginalSet marginals = fg.getMarginals();
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

            Object outcomesConsList = ConsValue.listToConsList(outcomes);
            Object weightsConsList = ConsValue.listToConsList(weights);
            return new EvalResult(new ConsValue(outcomesConsList, new ConsValue(weightsConsList, ConstantValue.NIL)));
          } else {
            return new EvalResult(value);
          }
        } else if (constantName.equals("add-weight")) {
          Preconditions.checkArgument(subexpressions.size() == 3);
          Object value = eval(subexpressions.get(1), environment, builder).getValue();
          double weight = ((Number) eval(subexpressions.get(2), environment, builder).getValue()).doubleValue();

          if (value instanceof AmbValue) {
            VariableNumMap fgVar = ((AmbValue) value).getVar();

            TableFactorBuilder tfBuilder = TableFactorBuilder.ones(fgVar);
            tfBuilder.setWeight(weight, ConstantValue.TRUE); 
            TableFactor factor = tfBuilder.build();
            builder.addConstantFactor(fgVar.getOnlyVariableName(), factor);
          } else if (ConstantValue.TRUE.equals(value)) {
            builder.addConstantFactor("constant-factor",
                TableFactor.unity(VariableNumMap.EMPTY).product(weight));
          }
          return new EvalResult(ConstantValue.UNDEFINED);
        }
      }

      return doFunctionApplication(subexpressions, environment, builder);
    }
  }

  public EvalResult doFunctionApplication(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder gfgBuilder) {
    List<Object> values = Lists.newArrayList();
    for (SExpression expression : subexpressions) {
      values.add(eval(expression, environment, gfgBuilder).getValue());
    }

    Object functionObject = values.get(0);
    List<Object> argumentValues = values.subList(1, values.size());
    if (functionObject instanceof AmbFunctionValue) {
      AmbFunctionValue function = (AmbFunctionValue) functionObject;
      return new EvalResult(function.apply(argumentValues, environment, gfgBuilder));
    } else if (functionObject instanceof AmbValue) {
      // TODO: This gets fucked up if the called functions themselves modify gfgBuilder.
      AmbValue functionAmb = ((AmbValue) functionObject);
      List<Object> possibleFunctionObjects = functionAmb.getPossibleValues();
      List<Object> functionResults = Lists.newArrayList();
      List<Object> possibleReturnValues = Lists.newArrayList();
      for (Object possibleFunctionObject : possibleFunctionObjects) {
        AmbFunctionValue function = (AmbFunctionValue) possibleFunctionObject;
        Object result = function.apply(argumentValues, environment, gfgBuilder);
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
      gfgBuilder.addVariables(returnValueVar);
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

          gfgBuilder.addConstantFactor(varName, builder.build());
        } else {
          TableFactorBuilder builder = TableFactorBuilder.ones(returnValueVar.union(functionVar));
          Assignment returnValueAssignment = returnValueVar.outcomeArrayToAssignment(returnValue);
          builder.incrementWeight(TableFactor.pointDistribution(functionVar, functionAssignment)
              .outerProduct(TableFactor.unity(returnValueVar)).product(-1.0));
          builder.setWeight(returnValueAssignment.union(functionAssignment), 1.0);

          gfgBuilder.addConstantFactor(varName, builder.build());
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

  private static Object resolveAmbValueWithAssignment(Object value, Assignment assignment) {
    if (value instanceof AmbValue) {
      return assignment.getValue(((AmbValue) value).getVar().getOnlyVariableNum());
    } else if (value instanceof ConsValue) {
      ConsValue consValue = (ConsValue) value;
      return new ConsValue(resolveAmbValueWithAssignment(consValue.getCar(), assignment),
          resolveAmbValueWithAssignment(consValue.getCdr(), assignment));
    } else {
      return value;
    }
  }

  public static Environment getDefaultEnvironment() {
    Environment env = Environment.empty();
    env.bindName("cons", new WrappedBuiltinFunction(new BuiltinFunctions.ConsFunction()));
    env.bindName("car", new WrappedBuiltinFunction(new BuiltinFunctions.CarFunction()));
    env.bindName("cdr", new WrappedBuiltinFunction(new BuiltinFunctions.CdrFunction()));
    env.bindName("list", new WrappedBuiltinFunction(new BuiltinFunctions.ListFunction()));
    env.bindName("nil?", new RaisedBuiltinFunction(new BuiltinFunctions.NilFunction()));
    env.bindName("+", new RaisedBuiltinFunction(new BuiltinFunctions.PlusFunction()));
    env.bindName("-", new RaisedBuiltinFunction(new BuiltinFunctions.MinusFunction()));
    env.bindName("*", new RaisedBuiltinFunction(new BuiltinFunctions.MultiplyFunction()));
    env.bindName("/", new RaisedBuiltinFunction(new BuiltinFunctions.DivideFunction()));
    env.bindName("log", new RaisedBuiltinFunction(new BuiltinFunctions.LogFunction()));
    env.bindName("=", new RaisedBuiltinFunction(new BuiltinFunctions.EqualsFunction()));
    env.bindName("not", new RaisedBuiltinFunction(new BuiltinFunctions.NotFunction()));
    env.bindName("and", new RaisedBuiltinFunction(new BuiltinFunctions.AndFunction()));
    env.bindName("or", new RaisedBuiltinFunction(new BuiltinFunctions.OrFunction()));
    env.bindName("display", new WrappedBuiltinFunction(new BuiltinFunctions.DisplayFunction()));
    return env;
  }

  private static interface AmbFunctionValue {
    
    public Object apply(List<Object> argumentValues, Environment env,
        ParametricBfgBuilder gfgBuilder);
  }
  
  private static class AmbLambdaValue implements AmbFunctionValue {
    private final LambdaValue lambdaValue;
    private final AmbEval eval;

    public AmbLambdaValue(LambdaValue lambdaValue, AmbEval eval) {
      this.lambdaValue = Preconditions.checkNotNull(lambdaValue);
      this.eval = Preconditions.checkNotNull(eval);
    }

    @Override
    public Object apply(List<Object> argumentValues, Environment env, ParametricBfgBuilder gfgBuilder) {
      List<String> argumentNames = lambdaValue.getArgumentNames(); 
      Preconditions.checkArgument(argumentNames.size() == argumentValues.size(),
          "Wrong number of arguments: expected %s, got %s", argumentNames, argumentValues);

      Environment boundEnvironment = Environment.extend(lambdaValue.getEnvironment());
      boundEnvironment.bindNames(argumentNames, argumentValues);

      return eval.eval(lambdaValue.getBody(), boundEnvironment, gfgBuilder).getValue();
    }
    
    @Override
    public String toString() {
      return lambdaValue.toString();
    }
  }

  private static class WrappedBuiltinFunction implements AmbFunctionValue {
    private final FunctionValue baseFunction;

    public WrappedBuiltinFunction(FunctionValue baseFunction) {
      this.baseFunction = baseFunction;
    }

    @Override
    public Object apply(List<Object> argumentValues, Environment env, ParametricBfgBuilder gfgBuilder) {
      return baseFunction.apply(argumentValues, env);
    }
    
    @Override
    public String toString() {
      return baseFunction.toString();
    }
  }

  private static class RaisedBuiltinFunction implements AmbFunctionValue {
    private final FunctionValue baseFunction;

    public RaisedBuiltinFunction(FunctionValue baseFunction) {
      this.baseFunction = baseFunction;
    }

    @Override
    public Object apply(List<Object> argumentValues, Environment env, ParametricBfgBuilder gfgBuilder) {
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

        Object result = baseFunction.apply(chosenValues, env);
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
      gfgBuilder.addVariables(fgVar);

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

        Object result = baseFunction.apply(chosenValues, env);
        if (result instanceof AmbValue) {
          Preconditions.checkState(false, "Probabilistic functions not yet supported.");
        } else {
          assignment = assignment.union(fgVar.outcomeArrayToAssignment(result));
          builder.setWeight(assignment, 1.0);
        }
      }

      TableFactor factor = builder.build();
      gfgBuilder.addConstantFactor(varName, factor);

      return new AmbValue(fgVar);
    }
    
    @Override
    public String toString() {
      return baseFunction.toString();
    }
  }
}
