package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.parametric.SufficientStatistics;
import com.jayantkrish.jklol.training.GradientOracle;
import com.jayantkrish.jklol.training.StochasticGradientTrainer;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class TrainCvsm extends AbstractCli {

  private OptionSpec<String> trainingFilename;
  private OptionSpec<String> modelOutput;

  private static final String VECTOR_PREFIX = "t1:";
  private static final String MATRIX_PREFIX = "t2:";
  private static final String TENSOR_PREFIX = "t3:";

  public TrainCvsm() {
    super(CommonOptions.STOCHASTIC_GRADIENT, CommonOptions.MAP_REDUCE);
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    trainingFilename = parser.accepts("training").withRequiredArg()
        .ofType(String.class).required();
    modelOutput = parser.accepts("output").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    List<CvsmExample> examples = CvsmUtils.readTrainingData(options.valueOf(trainingFilename));
    int vectorSize = examples.get(0).getTargetDistribution().getDimensionSizes()[0];
    List<String> parameterNames = getParameterNames(examples);

    CvsmFamily family = buildCvsmModel(vectorSize, parameterNames);
    SufficientStatistics trainedParameters = estimateParameters(family, examples);
    Cvsm trainedModel = family.getModelFromParameters(trainedParameters);

    IoUtils.serializeObjectToFile(trainedModel, options.valueOf(modelOutput));
  }
  
  private SufficientStatistics estimateParameters(CvsmFamily family, List<CvsmExample> examples) {
    GradientOracle<Cvsm, CvsmExample> oracle = new CvsmLoglikelihoodOracle(family);
    SufficientStatistics initialParameters = family.getNewSufficientStatistics();
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
