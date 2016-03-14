package com.jayantkrish.jklol.lisp;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.lisp.AmbEval.WrappedBuiltinFunction;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.ObjectVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.VariableNumMap.VariableRelabeling;
import com.jayantkrish.jklol.models.loglinear.ParametricLinearClassifierFactor;
import com.jayantkrish.jklol.models.loglinear.IndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.ListSufficientStatistics;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.tensor.TensorBuilder;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Implementations of functions for creating classifiers,
 * their parameter vectors, and feature vectors.
 * 
 * @author jayantk
 */
public class ClassifierFunctions {

  /**
   * Classifier with an indicator feature for every possible
   * outcome of the given random variables. 
   * 
   * @author jayantk
   */
  public static class MakeIndicatorClassifier implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      LispUtil.checkArgument(argumentValues.get(0) instanceof AmbValue || argumentValues.get(0) instanceof ConsValue);
      LispUtil.checkArgument(argumentValues.get(1) instanceof SpecAndParameters);

      SpecAndParameters parameters = (SpecAndParameters) argumentValues.get(1);

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
      Factor factor = pf.getModelFromParameters(parameters.getParameters()).relabelVariables(
          relabeling.inverse());

      builder.addConstantFactor("classifier-" + factorVars.getVariableNums(), factor);
      builder.addMark(factorVars, pf, relabeling, parameters.getParameterSpec().getId());

      return ConstantValue.UNDEFINED;
    }
  }

  public static class MakeIndicatorClassifierParameters implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      List<Object> varValueLists = ConsValue.consListToList(argumentValues.get(0), Object.class);
      
      VariableNumMap vars = VariableNumMap.EMPTY;
      for (int i = 0; i < varValueLists.size(); i++) {
        String varName = argumentValues.get(0).toString() + "-" + i;
        List<Object> varValues = ConsValue.consListToList(varValueLists.get(i), Object.class);

        DiscreteVariable fgVarType = new DiscreteVariable(varName, varValues);
        vars = vars.union(VariableNumMap.singleton(i, varName, fgVarType));
      }

      ParametricFactor pf = new IndicatorLogLinearFactor(vars, TableFactor.unity(vars));

      FactorParameterSpec fps = new FactorParameterSpec(AbstractParameterSpec.getUniqueId(), pf);
      SufficientStatistics params = fps.getNewParameters();
      return new SpecAndParameters(fps, params);
    }
  }

  /**
   * Classifier using an input feature vector. The classifier
   * has independent parameters for each (label, feature) pair.
   * 
   * @author jayantk
   */
  public static class MakeFeaturizedClassifier implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 3);
      LispUtil.checkArgument(argumentValues.get(0) instanceof AmbValue || argumentValues.get(0) instanceof ConsValue);
      LispUtil.checkArgument(argumentValues.get(1) instanceof Tensor);
      LispUtil.checkArgument(argumentValues.get(2) instanceof SpecAndParameters);

      Tensor featureVector = (Tensor) argumentValues.get(1);
      SpecAndParameters parameters = (SpecAndParameters) argumentValues.get(2);

      VariableNumMap factorVars = null;
      VariableRelabeling relabeling = VariableRelabeling.EMPTY;
      if (argumentValues.get(0) instanceof AmbValue) {
        AmbValue ambValue = (AmbValue) argumentValues.get(0);
        factorVars = ambValue.getVar();
        relabeling = VariableRelabeling.createFromVariables(factorVars,
            factorVars.relabelVariableNums(new int[] {1}));
      } else if (argumentValues.get(0) instanceof ConsValue) {
        List<AmbValue> values = ConsValue.consListToList(argumentValues.get(0), AmbValue.class);
        factorVars = VariableNumMap.EMPTY;
        relabeling = VariableRelabeling.EMPTY;
        for (int i = 0; i < values.size(); i++) {
          VariableNumMap curVar = values.get(i).getVar();
          factorVars = factorVars.union(curVar);
          relabeling = relabeling.union(VariableRelabeling.createFromVariables(curVar,
              curVar.relabelVariableNums(new int[] {i + 1})));
        }
      }
      
      // Add a variable to the graphical model containing the feature vector.
      int featureVectorVarNum = ParametricBfgBuilder.getUniqueVarNum();
      VariableNumMap featureVectorVar = VariableNumMap.singleton(featureVectorVarNum,
          "feature-vector-var" + featureVectorVarNum, new ObjectVariable(Tensor.class));
      builder.addVariables(featureVectorVar);
      builder.addAssignment(featureVectorVar.outcomeArrayToAssignment(featureVector));
      relabeling = relabeling.union(VariableRelabeling.createFromVariables(featureVectorVar,
          featureVectorVar.relabelVariableNums(new int[] {0})));
      
      VariableNumMap relabeledVars = relabeling.apply(factorVars);
      VariableNumMap relabeledFeatureVectorVar = relabeling.apply(featureVectorVar);

      DiscreteVariable featureDictionary = ((ParametricLinearClassifierFactor)
          ((FactorParameterSpec) parameters.getParameterSpec()).getFactor()).getFeatureDictionary();
      Preconditions.checkArgument(featureDictionary.numValues() == featureVector.getDimensionSizes()[0],
                                  "feature vector and classifier dimension don't match (" +
                                  featureVector.getDimensionSizes()[0] + " vs. " +
                                  featureDictionary.numValues() +")");
      ParametricFactor pf = new ParametricLinearClassifierFactor(relabeledFeatureVectorVar,
          relabeledVars, VariableNumMap.EMPTY, featureDictionary, null, false);

      Factor factor = pf.getModelFromParameters(parameters.getParameters())
          .relabelVariables(relabeling.inverse());

      builder.addConstantFactor("classifier-" + factor.getVars().getVariableNums(), factor);
      builder.addMark(factor.getVars(), pf, relabeling, parameters.getParameterSpec().getId());

      return ConstantValue.UNDEFINED;
    }
  }

  public static class MakeFeaturizedClassifierParameters implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      List<Object> varValueLists = ConsValue.consListToList(argumentValues.get(0), Object.class);
      List<Object> featureNames = ConsValue.consListToList(argumentValues.get(1), Object.class);
      DiscreteVariable featureDictionary = new DiscreteVariable("foo", featureNames);

      VariableNumMap vars = VariableNumMap.EMPTY;
      for (int i = 0; i < varValueLists.size(); i++) {
        String varName = argumentValues.get(0).toString() + "-" + (i + 1);
        List<Object> varValues = ConsValue.consListToList(varValueLists.get(i), Object.class);

        DiscreteVariable fgVarType = new DiscreteVariable(varName, varValues);
        vars = vars.union(VariableNumMap.singleton(i + 1, varName, fgVarType));
      }
      VariableNumMap featureVectorVar = VariableNumMap.singleton(0, "feature-vector-var",
          new ObjectVariable(Tensor.class));

      ParametricFactor pf = new ParametricLinearClassifierFactor(featureVectorVar, vars,
          VariableNumMap.EMPTY, featureDictionary, null, false);

      FactorParameterSpec fps = new FactorParameterSpec(AbstractParameterSpec.getUniqueId(), pf);
      SufficientStatistics params = fps.getNewParameters();
      return new SpecAndParameters(fps, params);
    }
  }


  public static class MakeFeatureFactory implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      List<Object> values = ConsValue.consListToList(argumentValues.get(0), Object.class);

      return new WrappedBuiltinFunction(new FeatureFactory(new DiscreteVariable("foo", values)));
    }
  }

  public static class FeatureFactory implements FunctionValue {
    private final DiscreteVariable dictionary;

    public FeatureFactory(DiscreteVariable dictionary) {
      this.dictionary = Preconditions.checkNotNull(dictionary);
    }

    public DiscreteVariable getFeatureDictionary() {
      return dictionary;
    }

    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      List<Object> values = ConsValue.consListToList(argumentValues.get(0), Object.class);
      
      TensorBuilder builder = new SparseTensorBuilder(new int[] {0}, new int[] {dictionary.numValues()});
      for (Object value : values) {
        List<Object> tuple = ConsValue.consListToList(value, Object.class);
        Object featureName = tuple.get(0);
        if (dictionary.canTakeValue(featureName)) {
          int featureIndex = dictionary.getValueIndex(featureName);
          double featureValue = (Double) tuple.get(1);
          builder.incrementEntry(featureValue, featureIndex);
        }
      }

      return builder.build();
    }
  }

  /**
   * Classifier that sets the weight of a single outcome 
   * based on the inner product of two given parameter vectors.
   * 
   * @author jayantk
   */
  public static class MakeInnerProductClassifier implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      // Argument spec: 1st arg is the variable to add weight to
      // 2nd arg is the value of that variable to weight
      // 3rd and 4th args are the parameter vectors whose inner product
      // determines the weight.
      LispUtil.checkArgument(argumentValues.size() == 4);
      LispUtil.checkArgument(argumentValues.get(0) instanceof AmbValue);
      LispUtil.checkArgument(argumentValues.get(2) instanceof SpecAndParameters,
          "Third argument to make-inner-product-classifier must be a parameter vector");
      LispUtil.checkArgument(argumentValues.get(3) instanceof SpecAndParameters,
          "Fourth argument to make-inner-product-classifier must be a parameter vector");

      SpecAndParameters parameters1 = (SpecAndParameters) argumentValues.get(2);
      SpecAndParameters parameters2 = (SpecAndParameters) argumentValues.get(3);

      AmbValue ambValue = (AmbValue) argumentValues.get(0);
      VariableNumMap factorVars = ambValue.getVar();
      VariableRelabeling relabeling = VariableRelabeling.createFromVariables(
          factorVars, factorVars.relabelVariableNums(new int[] {0}));
      VariableNumMap relabeledVars = relabeling.apply(factorVars);
      Assignment assignment = relabeledVars.outcomeArrayToAssignment(argumentValues.get(1));

      ParametricFactor pf = new InnerProductParametricFactor(relabeledVars, assignment, -1);

      ListSufficientStatistics combinedParameters = new ListSufficientStatistics(Arrays.asList("0", "1"),
          Arrays.asList(parameters1.getParameters(), parameters2.getParameters()));
      Factor factor = pf.getModelFromParameters(combinedParameters).relabelVariables(
          relabeling.inverse());

      builder.addConstantFactor("classifier-" + factorVars.getVariableNums(), factor);
      builder.addMark(factorVars, pf, relabeling, new int[] {parameters1.getParameterSpec().getId(),
          parameters2.getParameterSpec().getId()});

      return ConstantValue.UNDEFINED;
    }
  }
  
  public static class MakeRankingInnerProductClassifier implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      // Argument spec: 1st arg is the variable to add weight to
      // 2nd arg is the value of that variable to weight
      // 3rd arg is the parameter vector.
      // 4th and 5th args are the parameter vectors whose difference is
      // multiplied by the 3rd arg to determine the weight. 4th is positive, 5th is negative.
      LispUtil.checkArgument(argumentValues.size() == 5);
      LispUtil.checkArgument(argumentValues.get(0) instanceof AmbValue);
      LispUtil.checkArgument(argumentValues.get(2) instanceof SpecAndParameters,
          "Third argument to make-inner-product-classifier must be a parameter vector");
      LispUtil.checkArgument(argumentValues.get(3) instanceof SpecAndParameters,
          "Fourth argument to make-inner-product-classifier must be a parameter vector");
      LispUtil.checkArgument(argumentValues.get(4) instanceof SpecAndParameters,
          "Fifth argument to make-inner-product-classifier must be a parameter vector");

      SpecAndParameters parameters1 = (SpecAndParameters) argumentValues.get(2);
      SpecAndParameters parameters2 = (SpecAndParameters) argumentValues.get(3);
      SpecAndParameters parameters3 = (SpecAndParameters) argumentValues.get(4);

      AmbValue ambValue = (AmbValue) argumentValues.get(0);
      VariableNumMap factorVars = ambValue.getVar();
      VariableRelabeling relabeling = VariableRelabeling.createFromVariables(
          factorVars, factorVars.relabelVariableNums(new int[] {0}));
      VariableNumMap relabeledVars = relabeling.apply(factorVars);
      Assignment assignment = relabeledVars.outcomeArrayToAssignment(argumentValues.get(1));

      ParametricFactor pf = new RankingInnerProductParametricFactor(relabeledVars, assignment, -1);

      ListSufficientStatistics combinedParameters = new ListSufficientStatistics(Arrays.asList("0", "1", "2"),
          Arrays.asList(parameters1.getParameters(), parameters2.getParameters(), parameters3.getParameters()));
      Factor factor = pf.getModelFromParameters(combinedParameters).relabelVariables(
          relabeling.inverse());

      builder.addConstantFactor("classifier-" + factorVars.getVariableNums(), factor);
      builder.addMark(factorVars, pf, relabeling, new int[] {parameters1.getParameterSpec().getId(),
          parameters2.getParameterSpec().getId(), parameters3.getParameterSpec().getId()});

      return ConstantValue.UNDEFINED;
    }
  }
  
  public static class MakeVectorParameters implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      int dimensionality = (Integer) argumentValues.get(0);

      String name = "vector-features-" + dimensionality;
      VariableNumMap parameterVar = VariableNumMap.singleton(0, name,
          DiscreteVariable.sequence(name, dimensionality));

      TensorParameterSpec spec = new TensorParameterSpec(AbstractParameterSpec.getUniqueId(),
          parameterVar);
      SufficientStatistics params = spec.getNewParameters();
      return new SpecAndParameters(spec, params);        
    }
  }

  public static class MakeParameterList implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      List<SpecAndParameters> childParameterList = ConsValue.consListOrArrayToList(
          argumentValues.get(0), SpecAndParameters.class);
      List<ParameterSpec> specs = Lists.newArrayList();
      List<SufficientStatistics> childStatistics = Lists.newArrayList();
      for (SpecAndParameters childParameters : childParameterList) {
        specs.add(childParameters.getParameterSpec());
        childStatistics.add(childParameters.getParameters());
      }

      ListParameterSpec listSpec = new ListParameterSpec(AbstractParameterSpec.getUniqueId(),
          specs);
      SufficientStatistics listStatistics = listSpec.wrap(childStatistics);
      return new SpecAndParameters(listSpec, listStatistics);
    }
  }

  public static class GetIthParameter implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      SpecAndParameters parameters = (SpecAndParameters) argumentValues.get(0);
      int index = (Integer) argumentValues.get(1);

      ListParameterSpec spec = (ListParameterSpec) parameters.getParameterSpec();
      ParameterSpec childSpec = spec.get(index);

      SufficientStatistics params = spec.getParameter(index, parameters.getParameters());
      return new SpecAndParameters(childSpec, params);
    }
  }
  
  public static class PerturbFunction implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      SpecAndParameters parameters = (SpecAndParameters) argumentValues.get(0);
      double stddev = (Double) argumentValues.get(1);
      parameters.getParameters().perturb(stddev);

      return parameters;
    }
  }

  public static class Serialize implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      Object object = argumentValues.get(0);
      String filename = (String) argumentValues.get(1);
      IoUtils.serializeObjectToFile(object, filename);
      return ConstantValue.UNDEFINED;
    }
  }

  public static class Deserialize implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      String filename = (String) argumentValues.get(0);
      return IoUtils.readSerializedObject(filename, Object.class);
    }
  }
  
  public static class ParametersToString implements AmbFunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context, ParametricBfgBuilder builder) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      LispUtil.checkArgument(argumentValues.get(0) instanceof SpecAndParameters);
      
      TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) ((SpecAndParameters)
          argumentValues.get(0)).getParameters();
      
      Tensor tensor = tensorStats.get();
      StringBuilder sb = new StringBuilder();
      for (long keyNum = 0; keyNum < tensor.getMaxKeyNum(); keyNum++) {
        sb.append(tensor.get(keyNum));
        
        if (keyNum < tensor.getMaxKeyNum() - 1) {
          sb.append(" ");
        }
      }

      return sb.toString();
    }
  }
}
