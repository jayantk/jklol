package com.jayantkrish.jklol.preprocessing;

import java.util.Arrays;
import java.util.Iterator;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.TableFactorBuilder;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensorBuilder;
import com.jayantkrish.jklol.util.AllAssignmentIterator;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link FeatureStandardizer}.
 * 
 * @author jayantk
 */
public class FeatureStandardizerTest extends TestCase {

  DiscreteFactor featureFactor, featureFactor2;
  VariableNumMap var1;
  VariableNumMap var2;
  VariableNumMap features;
  
  String[] featureFactor2Values = new String[] {
      "A,T,f3,1.0",
      "A,T,f1,1.0"
  };
  
  private static final double TOLERANCE = 0.0000001;

  public void setUp() {
    var1 = VariableNumMap.singleton(1, "1", new DiscreteVariable("1", Arrays.asList("A", "B", "C")));
    var2 = VariableNumMap.singleton(2, "2", new DiscreteVariable("2", Arrays.asList("T", "F")));
    features = VariableNumMap.singleton(3, "features", new DiscreteVariable("features",
        Arrays.asList("f1", "f2", "f3", "bias")));
    
    VariableNumMap vars = VariableNumMap.unionAll(var1, var2, features);
    
    TableFactorBuilder featureFactorBuilder = new TableFactorBuilder(vars, SparseTensorBuilder.getFactory());
    // f1 has mean 0 and unit variance. Should be unchanged by the mapping.
    featureFactorBuilder.setWeight(1.0, "A", "T", "f1");
    featureFactorBuilder.setWeight(-1.0, "A", "F", "f1");
    featureFactorBuilder.setWeight(-1.0, "B", "T", "f1");
    featureFactorBuilder.setWeight(-1.0, "B", "F", "f1");
    featureFactorBuilder.setWeight(1.0, "C", "T", "f1");
    featureFactorBuilder.setWeight(1.0, "C", "F", "f1");
    
    // f2 has mean 1/3 and variance ~1.5555. Inverse std. dev. is ~0.8017
    featureFactorBuilder.setWeight(2.0, "A", "F", "f2");
    featureFactorBuilder.setWeight(2.0, "A", "T", "f2");
    featureFactorBuilder.setWeight(-1.0, "B", "T", "f2");
    featureFactorBuilder.setWeight(-1.0, "C", "F", "f2");
    
    // Bias features.
    Iterator<Assignment> iter = new AllAssignmentIterator(var1.union(var2));
    while (iter.hasNext()) {
      featureFactorBuilder.setWeight(iter.next().union(features.outcomeArrayToAssignment("bias")), 1.0);
    }    
    featureFactor = featureFactorBuilder.build();
    featureFactor2 = TableFactor.fromDelimitedFile(Arrays.asList(var1, var2, features), 
        Arrays.asList(featureFactor2Values), ",", false);
  }
  
  public void testEstimateFrom1() {
    FeatureStandardizer standardizer = FeatureStandardizer.estimateFrom(featureFactor, features.getOnlyVariableNum(), 
        features.outcomeArrayToAssignment("bias"));
    
    DiscreteFactor means = standardizer.getMeans();
    assertEquals(0.0, means.getUnnormalizedProbability("f1"));
    assertEquals(2.0 / 6.0, means.getUnnormalizedProbability("f2"));
    assertEquals(1.0, means.getUnnormalizedProbability("bias"));
    
    DiscreteFactor inverseStdDevs = standardizer.getInverseStdDevs();
    assertEquals(1.0, inverseStdDevs.getUnnormalizedProbability("f1"));
    assertEquals(0.801783726, inverseStdDevs.getUnnormalizedProbability("f2"), TOLERANCE);
    assertEquals(0.0, inverseStdDevs.getUnnormalizedProbability("bias"));
    
    DiscreteFactor offsets = standardizer.getFinalOffset();
    assertEquals(0.0, offsets.getUnnormalizedProbability("f1"));
    assertEquals(0.0, offsets.getUnnormalizedProbability("f2"));
    assertEquals(1.0, offsets.getUnnormalizedProbability("bias"));
  }
  
  public void testEstimateFrom2() {
    FeatureStandardizer standardizer = FeatureStandardizer.estimateFrom(
        Arrays.asList(featureFactor, featureFactor2), features.getOnlyVariableNum(), 
        features.outcomeArrayToAssignment("bias"));
    
    DiscreteFactor means = standardizer.getMeans();
    assertEquals(1.0 / 12.0, means.getUnnormalizedProbability("f1"), TOLERANCE);
    assertEquals(2.0 / 12.0, means.getUnnormalizedProbability("f2"), TOLERANCE);
    assertEquals(1.0 / 12.0, means.getUnnormalizedProbability("f3"), TOLERANCE);
    assertEquals(1.0 / 2.0, means.getUnnormalizedProbability("bias"), TOLERANCE);

    DiscreteFactor inverseStdDevs = standardizer.getInverseStdDevs();
    assertEquals(1.114172029, inverseStdDevs.getUnnormalizedProbability("f2"), TOLERANCE);

    DiscreteFactor offsets = standardizer.getFinalOffset();
    assertEquals(0.0, offsets.getUnnormalizedProbability("f1"));
    assertEquals(0.0, offsets.getUnnormalizedProbability("f2"));
    assertEquals(1.0, offsets.getUnnormalizedProbability("bias"));
  }
  
  public void testApply() {
    FeatureStandardizer standardizer = FeatureStandardizer.estimateFrom(featureFactor, features.getOnlyVariableNum(), 
        features.outcomeArrayToAssignment("bias"));
    
    DiscreteFactor standardized = standardizer.apply(featureFactor);
    
    assertEquals(1.0, standardized.getUnnormalizedProbability("A", "T", "f1"));
    assertEquals(-1.0, standardized.getUnnormalizedProbability("B", "T", "f1"));
    assertEquals(1.0, standardized.getUnnormalizedProbability("A", "F", "bias"));
    assertEquals(1.0, standardized.getUnnormalizedProbability("B", "T", "bias"));
    
    assertEquals((2.0 - (1.0 / 3.0)) * 0.801783726, standardized.getUnnormalizedProbability("A", "F", "f2"), 0.00001);
    assertEquals((-1.0 - (1.0 / 3.0)) * 0.801783726, standardized.getUnnormalizedProbability("B", "T", "f2"), 0.00001);
    assertEquals((-1.0 / 3.0) * 0.801783726, standardized.getUnnormalizedProbability("C", "T", "f2"), 0.00001);
  }

  /*
  public void testEstimateFromNoBias() {
    FeatureStandardizer standardizer = FeatureStandardizer.estimateFrom(featureFactor, features.getOnlyVariableNum(), 
        Assignment.EMPTY);
  }
   */
}
