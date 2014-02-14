package com.jayantkrish.jklol.lisp;

import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;

public class ClassifierFunctions {

  public static class MakeIndicatorClassifier implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env, ParametricBfgBuilder builder) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      Preconditions.checkArgument(argumentValues.get(0) instanceof AmbValue || argumentValues.get(0) instanceof ConsValue);
      Preconditions.checkArgument(argumentValues.get(1) instanceof ParameterSpec);

      ParameterSpec parameters = (ParameterSpec) argumentValues.get(1);

      VariableNumMap factorVars = null;
      VariableRelabeling relabeling = VariableRelabeling.EMPTY;
      if (argumentValues.get(0) instanceof AmbValue) {
        AmbValue ambValue = (AmbValue) argumentValues.get(0);
        factorVars = ambValue.getVar();
        relabeling = VariableRelabeling.createFromVariables(factorVars, factorVars.relabelVariableNums(new int[] {0}));
      } else if (argumentValues.get(0) instanceof ConsValue) {
        List<AmbValue> values = ConsValue.consListToList(argumentValues.get(0), AmbValue.class);
        factorVars = VariableNumMap.EMPTY;
        relabeling = VariableRelabeling.EMPTY;
        for (int i = 0; i < values.size(); i++) {
          VariableNumMap curVar = values.get(i).getVar();
          factorVars = factorVars.union(curVar);
          relabeling = relabeling.union(VariableRelabeling.createFromVariables(curVar, curVar.relabelVariableNums(new int[] {i})));
        }
      }

      VariableNumMap relabeledVars = relabeling.apply(factorVars);
      ParametricFactor pf = new IndicatorLogLinearFactor(relabeledVars, TableFactor.unity(relabeledVars));
      Factor factor = pf.getModelFromParameters(parameters.getCurrentParameters()).relabelVariables(
          relabeling.inverse());

      builder.addConstantFactor("classifier-" + factorVars.getVariableNums(), factor);
      builder.addMark(factorVars, pf, relabeling, parameters.getId());

      return ConstantValue.UNDEFINED;
    }
  }

  public static class MakeIndicatorClassifierParameters implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env, ParametricBfgBuilder builder) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      List<Object> varValueLists = ConsValue.consListToList(argumentValues.get(0), Object.class);
      
      VariableNumMap vars = VariableNumMap.EMPTY;
      for (int i = 0; i < varValueLists.size(); i++) {
        String varName = argumentValues.get(0).toString() + "-" + i;
        List<Object> varValues = ConsValue.consListToList(varValueLists.get(i), Object.class);

        DiscreteVariable fgVarType = new DiscreteVariable(varName, varValues);
        vars = vars.union(VariableNumMap.singleton(i, varName, fgVarType));
      }

      ParametricFactor pf = new IndicatorLogLinearFactor(vars, TableFactor.unity(vars));

      return new FactorParameterSpec(AbstractParameterSpec.getUniqueId(), pf, pf.getNewSufficientStatistics());
    }
  }
}
