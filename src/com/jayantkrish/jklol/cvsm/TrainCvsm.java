package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.models.parametric.TensorSufficientStatistics;
import com.jayantkrish.jklol.tensor.DenseTensor;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainCvsm extends AbstractCli {

  private OptionSpec<String> trainingFilename;
  private OptionSpec<String> modelOutput;
  private OptionSpec<String> initialVectors;
  private OptionSpec<String> fixedVectors;
  
  private OptionSpec<Void> squareLoss;

  private static final String VECTOR_PREFIX = "t1:";
  private static final String MATRIX_PREFIX = "t2:";
  private static final String TENSOR_PREFIX = "t3:";
  private static final String TENSOR4_PREFIX = "t4:";

  public TrainCvsm() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
    
    initialVectors = parser.accepts("initialVectors").withRequiredArg().ofType(String.class);
    fixedVectors = parser.accepts("fixedVectors").withRequiredArg().ofType(String.class);
    
    squareLoss = parser.accepts("squareLoss");
  }

  @Override
  public void run(OptionSet options) {
    List<CvsmExample> examples = CvsmUtils.readTrainingData(options.valueOf(trainingFilename));
    int vectorSize = examples.get(0).getTargetDistribution().getDimensionSizes()[0];
    List<String> parameterNames = getParameterNames(examples);
    
    Map<String, double[]> vectors = Maps.newHashMap();
    if (options.has(initialVectors)) {
      vectors = readVectors(options.valueOf(initialVectors));
    }

    CvsmFamily family = buildCvsmModel(vectorSize, parameterNames);
    SufficientStatistics trainedParameters = estimateParameters(family, examples, vectors,
        options.has(squareLoss));
    Cvsm trainedModel = family.getModelFromParameters(trainedParameters);

    IoUtils.serializeObjectToFile(trainedModel, options.valueOf(modelOutput));
  }
  
  private static Map<String, double[]> readVectors(String filename) {
    Map<String, double[]> vectors = Maps.newHashMap();
    for (String line : IoUtils.readLines(filename)) {
      String[] parts = line.split("#");
      String[] entries = parts[1].split(",");
      double[] values = new double[entries.length];
      for (int i = 0; i < entries.length; i++) {
        values[i] = Double.parseDouble(entries[i]);
      }
      vectors.put(parts[0], values);
    }
    return vectors;
  }
  
  private SufficientStatistics estimateParameters(CvsmFamily family, 
      List<CvsmExample> examples, Map<String, double[]> initialParameterMap,
      boolean useSquareLoss) {
    GradientOracle<Cvsm, CvsmExample> oracle = new CvsmLoglikelihoodOracle(family, useSquareLoss);
    SufficientStatistics initialParameters = family.getNewSufficientStatistics();

    List<String> names = initialParameters.coerceToList().getStatisticNames().items();
    List<SufficientStatistics> list = initialParameters.coerceToList().getStatistics();
    for (int i = 0; i < names.size(); i++) {
      String name = names.get(i);
      
      if (initialParameterMap.containsKey(name)) {
        TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) list.get(i);
        Tensor tensor = tensorStats.get();
        Tensor increment = new DenseTensor(tensor.getDimensionNumbers(),
            tensor.getDimensionSizes(), initialParameterMap.get(name));
        tensorStats.increment(increment, 1.0);
      } else if (name.startsWith(MATRIX_PREFIX) || name.startsWith(TENSOR_PREFIX) || 
          name.startsWith(TENSOR4_PREFIX)) {
        // Initialize matrix and tensor parameters to the identity
        TensorSufficientStatistics tensorStats = (TensorSufficientStatistics) list.get(i);
        Tensor tensor = tensorStats.get();
        Tensor diag = SparseTensor.diagonal(tensor.getDimensionNumbers(),
            tensor.getDimensionSizes(), 1.0);
        tensorStats.increment(diag, 1.0);
      }
    }
    
    initialParameters.perturb(0.0001);

    StochasticGradientTrainer trainer = createStochasticGradientTrainer(examples.size());
    return trainer.train(oracle, initialParameters, examples);
  }

  /**
   * Searches {@code examples} for all terms referring to vectors,
   * matrices and tensors and returns them. 
   * 
   * @param examples
   * @return
   */
  private static List<String> getParameterNames(List<CvsmExample> examples) {
    Set<String> parameterNames = Sets.newHashSet();
    Pattern pattern = Pattern.compile("t[0-9]:.*");
    for (CvsmExample example : examples) {
      for (ConstantExpression constant : example.getLogicalForm().getFreeVariables()) {
        String name = constant.getName();
        if (pattern.matcher(name).matches()) {
          parameterNames.add(name);
        }
      }
    }

    List<String> list = Lists.newArrayList(parameterNames);
    Collections.sort(list);
    return list;
  }

  private static CvsmFamily buildCvsmModel(int vectorSize, List<String> parameterNames) {
    DiscreteVariable dimType = DiscreteVariable.sequence("seq", vectorSize);
    VariableNumMap vectorVars = VariableNumMap.singleton(0, "dim-0", dimType);
    VariableNumMap matrixVars = new VariableNumMap(Ints.asList(0, 1),
        Arrays.asList("dim-0", "dim-1"), Arrays.asList(dimType, dimType));
    VariableNumMap t3Vars = new VariableNumMap(Ints.asList(0, 1, 2),
        Arrays.asList("dim-0", "dim-1", "dim-2"), Arrays.asList(dimType, dimType, dimType));
    VariableNumMap t4Vars = new VariableNumMap(Ints.asList(0, 1, 2, 3),
        Arrays.asList("dim-0", "dim-1", "dim-2", "dim-3"), 
        Arrays.asList(dimType, dimType, dimType, dimType));

    IndexedList<String> tensorNames = IndexedList.create();
    List<VariableNumMap> tensorDims = Lists.newArrayList();
    for (String parameterName : parameterNames) {
      tensorNames.add(parameterName);

      if (parameterName.startsWith(VECTOR_PREFIX)) {
        tensorDims.add(vectorVars);
      } else if (parameterName.startsWith(MATRIX_PREFIX)) {
        tensorDims.add(matrixVars);
      } else if (parameterName.startsWith(TENSOR_PREFIX)) {
        tensorDims.add(t3Vars);
      } else if (parameterName.startsWith(TENSOR4_PREFIX)) {
        tensorDims.add(t4Vars);
      } else {
        throw new IllegalArgumentException("Unknown type prefix: " + parameterName);
      }
    }

    return new CvsmFamily(tensorNames, tensorDims);
  }

  public static void main(String[] args) {
    new TrainCvsm().run(args);
  }
}
