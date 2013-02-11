package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.loglinear.DenseIndicatorLogLinearFactor;
import com.jayantkrish.jklol.models.loglinear.DiscreteLogLinearFactor;
import com.jayantkrish.jklol.models.parametric.CombiningParametricFactor;
import com.jayantkrish.jklol.models.parametric.ParametricFactor;
import com.jayantkrish.jklol.util.StringUtils;

public class CcgFileFeatureFactory implements CcgFeatureFactory {
  
  private final List<String> dependencyFeatures;

  public static final String INPUT_DEPENDENCY_PARAMETERS = "inputFeatures";
  public static final String INDICATOR_DEPENDENCY_PARAMETERS = "indicatorFeatures";

  public CcgFileFeatureFactory(List<String> dependencyFeatures) {
    this.dependencyFeatures = Preconditions.checkNotNull(dependencyFeatures);
  }

  @Override
  public ParametricFactor getDependencyFeatures(VariableNumMap semanticHeadVar, VariableNumMap semanticArgNumVar, 
      VariableNumMap semanticArgVar) {
    VariableNumMap vars = VariableNumMap.unionAll(semanticHeadVar, semanticArgNumVar, semanticArgVar);
    DiscreteVariable dependencyFeatureVarType = new DiscreteVariable("dependencyFeatures",
        StringUtils.readColumnFromDelimitedLines(dependencyFeatures, 3, ","));
    System.out.println(dependencyFeatureVarType.getValues());
    VariableNumMap dependencyFeatureVar = VariableNumMap.singleton(3,
        "dependencyFeatures", dependencyFeatureVarType);
    VariableNumMap featureFactorVars = vars.union(dependencyFeatureVar);

    List<Function<String, ?>> converters = Lists.newArrayList();
    converters.add(Functions.<String> identity());
    converters.add(new Function<String, Integer>() {
      public Integer apply(String input) {
        return Integer.parseInt(input);
      }
    });
    converters.add(Functions.<String> identity());
    converters.add(Functions.<String> identity());
    TableFactor dependencyFeatureTable = TableFactor.fromDelimitedFile(featureFactorVars,
        converters, dependencyFeatures, ",", true);
    DiscreteLogLinearFactor dependencyFeatureFactor = new DiscreteLogLinearFactor(vars,
        dependencyFeatureVar, dependencyFeatureTable);

    
    ParametricFactor dependencyParametricFactor = new CombiningParametricFactor(vars,
        Arrays.asList(INPUT_DEPENDENCY_PARAMETERS, INDICATOR_DEPENDENCY_PARAMETERS),
        Arrays.asList(dependencyFeatureFactor, new DenseIndicatorLogLinearFactor(vars)), false);
    return dependencyParametricFactor;
  }
}
