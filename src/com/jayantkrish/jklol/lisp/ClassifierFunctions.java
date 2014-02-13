package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;

public class ClassifierFunctions {

  public static class MakeIndicatorClassifier implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env, ParametricBfgBuilder builder) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      Preconditions.checkArgument(argumentValues.get(0) instanceof AmbValue);
      Preconditions.checkArgument(argumentValues.get(1) instanceof SufficientStatistics);

      AmbValue ambValue = (AmbValue) argumentValues.get(0);
      SufficientStatistics parameters = (SufficientStatistics) argumentValues.get(1);
      VariableNumMap var = ambValue.getVar();
      
      VariableNumMap relabeledVar = var.relabelVariableNums(new int[] {0});
      ParametricFactor pf = new DenseIndicatorLogLinearFactor(relabeledVar, false);
      Factor factor = pf.getModelFromParameters(parameters).relabelVariables(
          VariableRelabeling.createFromVariables(relabeledVar, var));

      builder.addConstantFactor("classifier-" + ambValue.getVar().getVariableNums(), factor);
      builder.addMark(var, pf, -1);

      return ConstantValue.UNDEFINED;
    }
  }

  public static class MakeIndicatorClassifierParameters implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env, ParametricBfgBuilder builder) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      List<Object> possibleValues = ConsValue.consListToList(argumentValues.get(0), Object.class);
      
      String varName = argumentValues.get(0).toString();
      DiscreteVariable fgVarType = new DiscreteVariable(varName, possibleValues);
      VariableNumMap var = VariableNumMap.singleton(0, varName, fgVarType);

      ParametricFactor pf = new DenseIndicatorLogLinearFactor(var, false);

      return new FactorParameterSpec(pf);
    }
  }
}
