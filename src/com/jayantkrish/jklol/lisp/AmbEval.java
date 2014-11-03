package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Doubles;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MarginalSet;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.lisp.LispEval.EvalResult;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.training.DefaultLogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IntegerArrayIterator;

public class AmbEval {

  public static final String OPT_EPOCHS_VAR_NAME="OPT-EPOCHS";
  public static final String OPT_L2_VAR_NAME="OPT-L2";
  public static final String OPT_L2_FREQ_VAR_NAME="OPT-L2-FREQ";
 
  public static final String CLI_ARGV_VAR_NAME="ARGV";

  // Indexes in the symbol table for built-in special forms.
  // The indexes are defined by the order in getInitialSymbolTable()
  private static final int DEFINE_SYMBOL_INDEX = 0;
  private static final int BEGIN_SYMBOL_INDEX = 1;
  private static final int LET_SYMBOL_INDEX = 2;
  private static final int LAMBDA_SYMBOL_INDEX = 3;
  private static final int QUOTE_SYMBOL_INDEX = 4;

  private static final int EVAL_SYMBOL_INDEX = 5;
  private static final int APPLY_SYMBOL_INDEX = 6;
  private static final int IF_SYMBOL_INDEX = 7;
  private static final int AMB_SYMBOL_INDEX = 8;
  private static final int GET_BEST_VALUE_SYMBOL_INDEX = 9;

  private static final int GET_MARGINALS_SYMBOL_INDEX = 10;
  private static final int ADD_WEIGHT_SYMBOL_INDEX = 11;
  private static final int OPT_SYMBOL_INDEX = 12;
  private static final int OPT_MM_SYMBOL_INDEX = 13;
  private static final int NEW_FG_SCOPE_INDEX = 14;
  
  private final IndexedList<String> symbolTable;

  public AmbEval(IndexedList<String> symbolTable) {
    this.symbolTable = Preconditions.checkNotNull(symbolTable);
  }

  public IndexedList<String> getSymbolTable() {
    return symbolTable;
  }

  public EvalResult eval(SExpression expression) {
    return eval(expression, getDefaultEnvironment(symbolTable), new ParametricBfgBuilder(true));
  }

  public EvalResult eval(SExpression expression, Environment environment,
      ParametricBfgBuilder builder) {
    if (expression.isConstant()) {
      // The expression may be a primitive type or a variable.
      Object primitiveValue = expression.getConstantPrimitiveValue(); 

      if (primitiveValue != null) {
        return new EvalResult(primitiveValue);
      } else {
        // Variable name
        return new EvalResult(environment.getValue(expression.getConstantIndex(), symbolTable));
      }
    } else {
      List<SExpression> subexpressions = expression.getSubexpressions();
      SExpression first = subexpressions.get(0);
      if (first.isConstant()) {
        int constantNameIndex = first.getConstantIndex();

        switch (constantNameIndex) {
        case DEFINE_SYMBOL_INDEX: return doDefine(subexpressions, environment, builder); 
        case BEGIN_SYMBOL_INDEX: return doBegin(subexpressions, environment, builder);
        case LET_SYMBOL_INDEX: return doLet(subexpressions, environment, builder);

        case LAMBDA_SYMBOL_INDEX:
          // Create and return a function value representing this function.
          Preconditions.checkArgument(subexpressions.size() >= 3, "Invalid lambda expression arguments: " + subexpressions);
          return new EvalResult(makeLambda(subexpressions.get(1), subexpressions.subList(2, subexpressions.size()), environment));

        case QUOTE_SYMBOL_INDEX:
          Preconditions.checkArgument(subexpressions.size() == 2, "Invalid quote arguments: " + subexpressions);
          return new EvalResult(subexpressions.get(1));

        case EVAL_SYMBOL_INDEX:
          Preconditions.checkArgument(subexpressions.size() == 2, "Invalid eval arguments: " + subexpressions);
          Object value = eval(subexpressions.get(1), environment, builder).getValue();
          Preconditions.checkArgument(value instanceof SExpression, "Argument to eval must be an expression. Got: " + value);
          return eval((SExpression) value, environment, builder);

        case APPLY_SYMBOL_INDEX:
          Preconditions.checkArgument(subexpressions.size() == 3, "Invalid apply expression: " + subexpressions);
          AmbFunctionValue lambdaValue = (AmbFunctionValue) eval(subexpressions.get(1), environment, builder).getValue();
          List<Object> argumentValues = ConsValue.consListToList(
              eval(subexpressions.get(2), environment, builder).getValue(), Object.class);
          return new EvalResult(lambdaValue.apply(argumentValues, environment, builder));

        case IF_SYMBOL_INDEX: return doIf(subexpressions, environment, builder);
        case AMB_SYMBOL_INDEX: return doAmb(subexpressions, environment, builder);
        case GET_BEST_VALUE_SYMBOL_INDEX: return doGetBestValue(subexpressions, environment, builder);
        case GET_MARGINALS_SYMBOL_INDEX: return doGetMarginals(subexpressions, environment, builder);
        case ADD_WEIGHT_SYMBOL_INDEX: return doAddWeight(subexpressions, environment, builder);
        case OPT_SYMBOL_INDEX: return doOpt(subexpressions, environment, builder);
        case OPT_MM_SYMBOL_INDEX: return doOptMm(subexpressions, environment, builder);
        case NEW_FG_SCOPE_INDEX: return doNewFgScope(subexpressions, environment, builder);
        }
      }
      return doFunctionApplication(subexpressions, environment, builder);
    }
  }

  private AmbLambdaValue makeLambda(SExpression arguments, List<SExpression> bodyExpressions,
      Environment environment) {
    Preconditions.checkArgument(arguments.getSubexpressions() != null,
        "Illegal argument list in lambda: %s", arguments);

    List<SExpression> argumentExpressions = arguments.getSubexpressions();
    int[] argumentNameIndexes = new int[argumentExpressions.size()];
    int ind = 0;
    for (SExpression argumentExpression : argumentExpressions) {
      Preconditions.checkArgument(argumentExpression.isConstant(),
          "%s is not a constant. Argument list: %s", argumentExpression, arguments);
      argumentNameIndexes[ind] = argumentExpression.getConstantIndex();
      ind++;
    }

    SExpression functionBody = null;
    if (bodyExpressions.size() == 1) {
      functionBody = bodyExpressions.get(0);
    } else {
      List<SExpression> functionBodyComponents = Lists.newArrayList();
      functionBodyComponents.add(SExpression.constant("begin", symbolTable.getIndex("begin"), null));
      functionBodyComponents.addAll(bodyExpressions);
      functionBody = SExpression.nested(functionBodyComponents);
    }

    return new AmbLambdaValue(new LambdaValue(argumentExpressions, argumentNameIndexes,
        functionBody, environment), this);
  }
  
  /**
   * Evaluates the "define" special form.
   *  
   * @param subexpressions
   * @param environment
   * @param builder
   * @return
   */
  private final EvalResult doDefine(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder builder) {
    int nameToBind = subexpressions.get(1).getConstantIndex();
    if (subexpressions.size() == 3) {
      // (define name value-expression)
      // Binds a name to the value of value-expression
      Object valueToBind = eval(subexpressions.get(2), environment, builder).getValue();
      environment.bindName(nameToBind, valueToBind);
    } else if (subexpressions.size() >= 4) {
      // (define procedure-name (arg1 ...) procedure-body)
      // syntactic sugar equivalent to (define procedure-name (lambda (arg1 ...) procedure-body
      AmbLambdaValue lambdaValue =  makeLambda(subexpressions.get(2),
          subexpressions.subList(3, subexpressions.size()), environment);
      environment.bindName(nameToBind, lambdaValue);
    }
    return new EvalResult(ConstantValue.UNDEFINED);
  }
  
  private final EvalResult doBegin(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder builder) {
    // Sequentially evaluates its subexpressions, chaining any  
    // environment changes.
    EvalResult result = new EvalResult(ConstantValue.UNDEFINED);
    for (int i = 1; i < subexpressions.size(); i++) {
      result = eval(subexpressions.get(i), environment, builder);
    } 
    return result;
  }
  
  private final EvalResult doLet(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder builder) {
    // (let ((name1 value-expr1) (name2 value-expr2) ...) body)
    Environment newEnv = Environment.extend(environment);

    List<SExpression> bindings = subexpressions.get(1).getSubexpressions();
    for (SExpression binding : bindings) {
      Preconditions.checkArgument(binding.getSubexpressions().size() == 2,
          "Illegal element in let bindings: %s", binding);
      int nameIndex = binding.getSubexpressions().get(0).getConstantIndex();
      SExpression valueExpression = binding.getSubexpressions().get(1);

      Object value = eval(valueExpression, newEnv, builder).getValue();
      newEnv.bindName(nameIndex, value);
    }

    EvalResult result = new EvalResult(ConstantValue.UNDEFINED);
    for (int i = 2; i < subexpressions.size(); i++) {
      result = eval(subexpressions.get(i), newEnv, builder);
    }
    return result;
  }
  
  private final EvalResult doIf(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder builder) {
    Preconditions.checkArgument(subexpressions.size() == 4, "Illegal if statement: %s", subexpressions);
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
      // We disallow this case for the moment.
      Preconditions.checkArgument(false,
          "Cannot use amb values in conditions of if statements. Subexpressions: %s", subexpressions);

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
      VariableNumMap returnValueVar = VariableNumMap.singleton(ParametricBfgBuilder.getUniqueVarNum(), varName,
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
  }

  private final EvalResult doAmb(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder builder) {
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
    VariableNumMap fgVar = VariableNumMap.singleton(ParametricBfgBuilder.getUniqueVarNum(), varName, fgVarType);
    builder.addVariables(fgVar);

    Assignment[] assignmentArray = new Assignment[possibleValues.size()];
    double[] weightArray = Doubles.toArray(weights);
    for (int i = 0; i < possibleValues.size(); i++) {
      assignmentArray[i] = fgVar.outcomeArrayToAssignment(possibleValues.get(i));
    }
    TableFactor factor = TableFactor.vector(fgVar, assignmentArray, weightArray);
    builder.addConstantFactor(varName, factor);

    return new EvalResult(new AmbValue(fgVar));
  }

  private final EvalResult doGetBestValue(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder builder) {
    Preconditions.checkArgument(subexpressions.size() == 2);
    Object value = eval(subexpressions.get(1), environment, builder).getValue();

    if (value instanceof AmbValue || value instanceof ConsValue) {
      BranchingFactorGraph fg = builder.build();
      // System.out.println("factor graph: " + fg.getParameterDescription());

      MaxMarginalSet maxMarginals = fg.getMaxMarginals();
      Assignment assignment = maxMarginals.getNthBestAssignment(0);

      Object bestAssignment = resolveAmbValueWithAssignment(value, assignment);
      return new EvalResult(bestAssignment); 
    } else {
      return new EvalResult(value);
    }
  }

  private final EvalResult doGetMarginals(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder builder) {
    Preconditions.checkArgument(subexpressions.size() == 2);
    Object value = eval(subexpressions.get(1), environment, builder).getValue();

    if (value instanceof AmbValue) {
      VariableNumMap targetVar = ((AmbValue) value).getVar();
      BranchingFactorGraph fg = builder.buildConnectedComponent(targetVar);

      MarginalSet marginals = fg.getMarginals(targetVar);
      DiscreteFactor varMarginal = marginals.getMarginal(targetVar.getOnlyVariableNum())
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
      Object outcomesConsList = ConsValue.listToConsList(Arrays.asList(value));
      Object weightsConsList = ConsValue.listToConsList(Arrays.asList(1.0));
      return new EvalResult(new ConsValue(outcomesConsList, new ConsValue(weightsConsList, ConstantValue.NIL)));
    }
  }

  private final EvalResult doAddWeight(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder builder) {
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

  private final EvalResult doOpt(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder builder) {
    Preconditions.checkArgument(subexpressions.size() == 4 || subexpressions.size() == 5);

    Object value = eval(subexpressions.get(1), environment, builder).getValue();
    Preconditions.checkArgument(value instanceof AmbFunctionValue);
    AmbFunctionValue modelFamily = (AmbFunctionValue) value;

    SpecAndParameters parameterSpec = (SpecAndParameters) eval(subexpressions.get(2),
        environment, builder).getValue();

    Object trainingDataValue = eval(subexpressions.get(3), environment, builder).getValue();
    List<ConsValue> trainingExampleObjects = ConsValue.consListOrArrayToList(
        trainingDataValue, ConsValue.class);
    List<Example<List<Object>, Object>> trainingData = Lists.newArrayList();
    for (ConsValue example : trainingExampleObjects) {
      List<Object> inputOutput = ConsValue.consListToList(example, Object.class);
      Preconditions.checkArgument(inputOutput.size() == 2);
      trainingData.add(Example.create(ConsValue.consListToList(inputOutput.get(0), Object.class),
          inputOutput.get(1)));
    }

    AmbLispLoglikelihoodOracle oracle = new AmbLispLoglikelihoodOracle(modelFamily, environment,
        parameterSpec.getParameterSpec(), new JunctionTree());

    // 4th argument is an optional parameter for providing optimization parameters.
    long epochs = (Long) environment.getValue(OPT_EPOCHS_VAR_NAME, symbolTable);
    double l2Penalty = (Double) environment.getValue(OPT_L2_VAR_NAME, symbolTable);
    double l2Frequency = (Double) environment.getValue(OPT_L2_FREQ_VAR_NAME, symbolTable);
    if (subexpressions.size() >= 5) {
      Object optimizationParamsAlist = eval(subexpressions.get(4), environment, builder).getValue(); 
      Map<String, Object> optimizationParams = ConsValue.associationListToMap(
          optimizationParamsAlist, String.class, Object.class);

      if (optimizationParams.containsKey("epochs")) {
        epochs = (Long) optimizationParams.get("epochs");
      }
      if (optimizationParams.containsKey("l2-regularization")) {
        l2Penalty = (Double) optimizationParams.get("l2-regularization");
      }
    }

    StochasticGradientTrainer trainer = StochasticGradientTrainer.createAdagrad(
        trainingData.size() * epochs, 1, 1, true, false, l2Penalty, l2Frequency,
        new DefaultLogFunction(10000, false));

    SufficientStatistics parameters = trainer.train(oracle, parameterSpec.getParameters(), trainingData);

    return new EvalResult(new SpecAndParameters(parameterSpec.getParameterSpec(), parameters));
  }

  private final EvalResult doOptMm(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder builder) {
    Preconditions.checkArgument(subexpressions.size() == 4 || subexpressions.size() == 5);

    Object value = eval(subexpressions.get(1), environment, builder).getValue();
    Preconditions.checkArgument(value instanceof AmbFunctionValue);
    AmbFunctionValue modelFamily = (AmbFunctionValue) value;

    SpecAndParameters parameterSpec = (SpecAndParameters) eval(subexpressions.get(2),
        environment, builder).getValue();

    Object trainingDataValue = eval(subexpressions.get(3), environment, builder).getValue();
    List<ConsValue> trainingExampleObjects = ConsValue.consListOrArrayToList(
        trainingDataValue, ConsValue.class);
    List<Example<List<Object>, Example<AmbFunctionValue, AmbFunctionValue>>> trainingData = Lists.newArrayList();
    for (ConsValue example : trainingExampleObjects) {
      List<Object> inputOutput = ConsValue.consListToList(example, Object.class);
      Preconditions.checkArgument(inputOutput.size() == 3);
      trainingData.add(Example.create(ConsValue.consListToList(inputOutput.get(0), Object.class),
          Example.create((AmbFunctionValue) inputOutput.get(1), (AmbFunctionValue) inputOutput.get(2))));
    }

    AmbLispMaxMarginOracle oracle = new AmbLispMaxMarginOracle(modelFamily, environment,
        parameterSpec.getParameterSpec(), new JunctionTree());

    // 4th argument is an optional parameter for providing optimization parameters.
    long epochs = (Integer) environment.getValue(OPT_EPOCHS_VAR_NAME, symbolTable);
    double l2Penalty = (Double) environment.getValue(OPT_L2_VAR_NAME, symbolTable);
    double l2Frequency = (Double) environment.getValue(OPT_L2_FREQ_VAR_NAME, symbolTable);
    if (subexpressions.size() >= 5) {
      Object optimizationParamsAlist = eval(subexpressions.get(4), environment, builder).getValue(); 
      Map<String, Object> optimizationParams = ConsValue.associationListToMap(
          optimizationParamsAlist, String.class, Object.class);

      if (optimizationParams.containsKey("epochs")) {
        epochs = (Long) optimizationParams.get("epochs");
      }
      if (optimizationParams.containsKey("l2-regularization")) {
        l2Penalty = (Double) optimizationParams.get("l2-regularization");
      }
    }

    StochasticGradientTrainer trainer = StochasticGradientTrainer.createWithStochasticL2Regularization(
        trainingData.size() * epochs, 1, 1, true, true, l2Penalty, l2Frequency, new NullLogFunction());

    SufficientStatistics parameters = trainer.train(oracle,
        parameterSpec.getParameters(), trainingData);

    // System.out.println(parameters.getDescription());

    return new EvalResult(new SpecAndParameters(parameterSpec.getParameterSpec(), parameters));
  }

  public EvalResult doNewFgScope(List<SExpression> subexpressions, Environment environment,
      ParametricBfgBuilder gfgBuilder) {
    // Sequentially evaluates its subexpressions, chaining any  
    // environment changes, in the context of a new builder.
    ParametricBfgBuilder newBuilder = new ParametricBfgBuilder(true);
    EvalResult result = new EvalResult(ConstantValue.UNDEFINED);
    for (int i = 1; i < subexpressions.size(); i++) {
      result = eval(subexpressions.get(i), environment, newBuilder);
    }
    return result;
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
      // TODO: This gets messed up if the called functions themselves modify gfgBuilder.
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
      VariableNumMap returnValueVar = VariableNumMap.singleton(ParametricBfgBuilder.getUniqueVarNum(), varName, varType);
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

  public static Environment getDefaultEnvironment(IndexedList<String> symbolTable) {
    Environment env = Environment.empty();
    env.bindName("cons", new RaisedBuiltinFunction(new BuiltinFunctions.ConsFunction()), symbolTable);
    env.bindName("car", new RaisedBuiltinFunction(new BuiltinFunctions.CarFunction()), symbolTable);
    env.bindName("cdr", new RaisedBuiltinFunction(new BuiltinFunctions.CdrFunction()), symbolTable);
    env.bindName("list", new RaisedBuiltinFunction(new BuiltinFunctions.ListFunction()), symbolTable);

    env.bindName("lifted-cons", new WrappedBuiltinFunction(new BuiltinFunctions.ConsFunction()), symbolTable);
    env.bindName("lifted-car", new WrappedBuiltinFunction(new BuiltinFunctions.CarFunction()), symbolTable);
    env.bindName("lifted-cdr", new WrappedBuiltinFunction(new BuiltinFunctions.CdrFunction()), symbolTable);
    env.bindName("lifted-list", new WrappedBuiltinFunction(new BuiltinFunctions.ListFunction()), symbolTable);

    env.bindName("make-dictionary", new RaisedBuiltinFunction(new BuiltinFunctions.MakeDictionaryFunction()), symbolTable);
    env.bindName("dictionary-lookup", new RaisedBuiltinFunction(new BuiltinFunctions.DictionaryLookupFunction()), symbolTable);
    env.bindName("dictionary-contains", new RaisedBuiltinFunction(new BuiltinFunctions.DictionaryContainsFunction()), symbolTable);
    env.bindName("dictionary-size", new RaisedBuiltinFunction(new BuiltinFunctions.DictionarySizeFunction()), symbolTable);
    env.bindName("dictionary-to-array", new RaisedBuiltinFunction(new BuiltinFunctions.DictionaryToArrayFunction()), symbolTable);
    env.bindName("dictionary-rand-elt", new RaisedBuiltinFunction(new BuiltinFunctions.DictionaryRandomElement()), symbolTable);

    env.bindName("make-histogram", new RaisedBuiltinFunction(new BuiltinFunctions.MakeHistogramFunction()), symbolTable);
    env.bindName("sample-histogram", new RaisedBuiltinFunction(new BuiltinFunctions.SampleHistogramFunction()), symbolTable);
    env.bindName("sample-histogram-conditional", new RaisedBuiltinFunction(new BuiltinFunctions.SampleHistogramConditionalFunction()), symbolTable);
    env.bindName("histogram-to-dictionary", new RaisedBuiltinFunction(new BuiltinFunctions.HistogramToDictionaryFunction()), symbolTable);

    env.bindName("make-dset", new RaisedBuiltinFunction(new BuiltinFunctions.MakeDset()), symbolTable);
    env.bindName("dset-empty?", new RaisedBuiltinFunction(new BuiltinFunctions.DsetEmpty()), symbolTable);
    env.bindName("dset-intersect", new RaisedBuiltinFunction(new BuiltinFunctions.DsetIntersect()), symbolTable);
    
    env.bindName("array", new RaisedBuiltinFunction(new BuiltinFunctions.MakeArrayFunction()), symbolTable);
    env.bindName("array-get-ith-element", new RaisedBuiltinFunction(new BuiltinFunctions.ArrayGetIthElement()), symbolTable); 
    env.bindName("array-map", new ArrayMapFunction(), symbolTable);
    env.bindName("array-foldr", new ArrayFoldRightFunction(), symbolTable);
    env.bindName("array-zip", new RaisedBuiltinFunction(new BuiltinFunctions.ArrayZipFunction()), symbolTable);
    env.bindName("array-sort", new RaisedBuiltinFunction(new BuiltinFunctions.ArraySortFunction()), symbolTable);
    env.bindName("array-merge-sets", new RaisedBuiltinFunction(new BuiltinFunctions.ArrayMergeSets()), symbolTable);

    env.bindName("make-indicator-classifier", new ClassifierFunctions.MakeIndicatorClassifier(), symbolTable);
    env.bindName("make-indicator-classifier-parameters", new ClassifierFunctions.MakeIndicatorClassifierParameters(), symbolTable);
    env.bindName("make-feature-factory", new WrappedBuiltinFunction(new ClassifierFunctions.MakeFeatureFactory()), symbolTable);
    env.bindName("make-featurized-classifier", new ClassifierFunctions.MakeFeaturizedClassifier(), symbolTable);
    env.bindName("make-featurized-classifier-parameters", new ClassifierFunctions.MakeFeaturizedClassifierParameters(), symbolTable);
    env.bindName("make-vector-parameters", new ClassifierFunctions.MakeVectorParameters(), symbolTable);
    env.bindName("make-inner-product-classifier", new ClassifierFunctions.MakeInnerProductClassifier(), symbolTable);
    env.bindName("make-ranking-inner-product-classifier", new ClassifierFunctions.MakeRankingInnerProductClassifier(), symbolTable);
    env.bindName("make-parameter-list", new ClassifierFunctions.MakeParameterList(), symbolTable);
    env.bindName("get-ith-parameter", new ClassifierFunctions.GetIthParameter(), symbolTable);
    env.bindName("perturb-parameters", new ClassifierFunctions.PerturbFunction(), symbolTable);
    env.bindName("parameters-to-string", new ClassifierFunctions.ParametersToString(), symbolTable);
    env.bindName("serialize", new ClassifierFunctions.Serialize(), symbolTable);
    env.bindName("deserialize", new ClassifierFunctions.Deserialize(), symbolTable);

    env.bindName("nil?", new RaisedBuiltinFunction(new BuiltinFunctions.NilFunction()), symbolTable);
    env.bindName("+", new RaisedBuiltinFunction(new BuiltinFunctions.PlusFunction()), symbolTable);
    env.bindName("-", new RaisedBuiltinFunction(new BuiltinFunctions.MinusFunction()), symbolTable);
    env.bindName("*", new RaisedBuiltinFunction(new BuiltinFunctions.MultiplyFunction()), symbolTable);
    env.bindName("/", new RaisedBuiltinFunction(new BuiltinFunctions.DivideFunction()), symbolTable);
    env.bindName("log", new RaisedBuiltinFunction(new BuiltinFunctions.LogFunction()), symbolTable);
    env.bindName("exp", new RaisedBuiltinFunction(new BuiltinFunctions.ExpFunction()), symbolTable);
    env.bindName("=", new RaisedBuiltinFunction(new BuiltinFunctions.EqualsFunction()), symbolTable);
    env.bindName("<", new RaisedBuiltinFunction(new BuiltinFunctions.LessThanFunction()), symbolTable);
    env.bindName(">", new RaisedBuiltinFunction(new BuiltinFunctions.GreaterThanFunction()), symbolTable);
    env.bindName("not", new RaisedBuiltinFunction(new BuiltinFunctions.NotFunction()), symbolTable);
    env.bindName("and", new RaisedBuiltinFunction(new BuiltinFunctions.AndFunction()), symbolTable);
    env.bindName("or", new RaisedBuiltinFunction(new BuiltinFunctions.OrFunction()), symbolTable);
    env.bindName("display", new WrappedBuiltinFunction(new BuiltinFunctions.DisplayFunction()), symbolTable);
    
    // Bind default environment parameters for opt and opt-mm.
    env.bindName(OPT_EPOCHS_VAR_NAME, 50L, symbolTable);
    env.bindName(OPT_L2_VAR_NAME, 0.0, symbolTable);
    env.bindName(OPT_L2_FREQ_VAR_NAME, 1.0, symbolTable);

    // Bind default command line arguments
    env.bindName(CLI_ARGV_VAR_NAME, ConstantValue.NIL, symbolTable);
    return env;
  }

  public static IndexedList<String> getInitialSymbolTable() {
    IndexedList<String> symbolTable = IndexedList.create();
    // The order of these additions must correspond to the symbol
    // table indexes declared as static variables at the beginning
    // of this class.
    symbolTable.add("define");
    symbolTable.add("begin");
    symbolTable.add("let");
    symbolTable.add("lambda");
    symbolTable.add("quote");

    symbolTable.add("eval");
    symbolTable.add("apply");
    symbolTable.add("if");
    symbolTable.add("amb");
    symbolTable.add("get-best-value");

    symbolTable.add("get-marginals");
    symbolTable.add("add-weight");
    symbolTable.add("opt");
    symbolTable.add("opt-mm");
    symbolTable.add("new-fg-scope");

    return symbolTable;
  }

  public static interface AmbFunctionValue {
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
      int[] argumentNameIndexes = lambdaValue.getArgumentNameIndexes(); 
      Preconditions.checkArgument(argumentNameIndexes.length == argumentValues.size(),
          "Wrong number of arguments: expected %s, got %s to procedure: %s",
          lambdaValue.getArgumentExpressions(), argumentValues, this);

      Environment boundEnvironment = Environment.extend(lambdaValue.getEnvironment());
      boundEnvironment.bindNames(argumentNameIndexes, argumentValues);

      return eval.eval(lambdaValue.getBody(), boundEnvironment, gfgBuilder).getValue();
    }
    
    @Override
    public String toString() {
      return lambdaValue.toString();
    }
  }

  public static class WrappedBuiltinFunction implements AmbFunctionValue {
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

  public static class RaisedBuiltinFunction implements AmbFunctionValue {
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
      boolean noAmbValues = true;
      for (int i = 0; i < argumentValues.size(); i++) {
        Object value = argumentValues.get(i);
        if (value instanceof AmbValue) {
          AmbValue ambValue = (AmbValue) value; 
          inputVarValues.add(ambValue.getPossibleValues());
          sizes[i] = ambValue.getPossibleValues().size();
          ambVars = ambVars.union(ambValue.getVar());
          inputVars.add(ambValue.getVar());
          noAmbValues = false;
        } else {
          inputVarValues.add(Lists.newArrayList(value));
          sizes[i] = 1;
          inputVars.add(null);
        }
      }
      
      if (noAmbValues) {
        // Short-circuit the more complex computation for efficiency.
        List<Object> arguments = Lists.newArrayList();
        for (List<Object> valueList : inputVarValues) {
          arguments.add(Iterables.getOnlyElement(valueList));
        }
        return baseFunction.apply(arguments, env);
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
      VariableNumMap fgVar = VariableNumMap.singleton(ParametricBfgBuilder.getUniqueVarNum(), varName, fgVarType);
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
          Preconditions.checkState(false, "Probabilistic functions not yet supported. baseFunction %s", baseFunction);
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
  
  /**
   * Applies a given function to each element of an array.
   * This function cannot be implemented using raising because
   * the function argument has a different type in {@code AmbEval}
   * and {@code LispEval}.
   * 
   * @author jayant
   */
  public static class ArrayMapFunction implements AmbFunctionValue {
    @Override    
    public Object apply(List<Object> argumentValues, Environment env,
        ParametricBfgBuilder gfgBuilder) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      AmbFunctionValue function = (AmbFunctionValue) argumentValues.get(0);
      Object[] values = (Object[]) argumentValues.get(1);
      
      Object[] result = new Object[values.length];
      for (int i = 0; i < values.length; i++) {
        result[i] = function.apply(Arrays.asList(values[i]), env, gfgBuilder);
      }
      return result;
    }
  }
  
  /**
   * Applies a given function to each element of an array.
   * This function cannot be implemented using raising because
   * the function argument has a different type in {@code AmbEval}
   * and {@code LispEval}.
   * 
   * @author jayant
   */
  public static class ArrayFoldRightFunction implements AmbFunctionValue {
    @Override    
    public Object apply(List<Object> argumentValues, Environment env,
        ParametricBfgBuilder gfgBuilder) {
      // Arguments are: <function> <array> <initial val>
      Preconditions.checkArgument(argumentValues.size() == 3);
      AmbFunctionValue function = (AmbFunctionValue) argumentValues.get(0);
      Object[] values = (Object[]) argumentValues.get(1);
      Object initialValue = argumentValues.get(2);

      Object result = initialValue;
      for (int i = values.length - 1; i >= 0; i--) {
        result = function.apply(Arrays.asList(values[i], result), env, gfgBuilder);
      }
      return result;
    }
  }
}
