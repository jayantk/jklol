package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cvsm.tree.CvsmKlLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmSquareLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmZeroOneLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmValueLossTree;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.Backpointers;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.util.IndexedList;
import com.jayantkrish.jklol.util.IoUtils;

public class TestCvsm extends AbstractCli {

  private OptionSpec<String> model;
  private OptionSpec<String> testFilename;
  private OptionSpec<String> vectorDumpFilename;

  private OptionSpec<String> relationDictionary;

  private OptionSpec<Void> squareLoss;
  private OptionSpec<Void> klLoss;

  private OptionSpec<Double> minValueToPrint;

  public TestCvsm() {
    super();
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    testFilename = parser.accepts("testFilename").withRequiredArg().ofType(String.class);
    vectorDumpFilename = parser.accepts("vectorDumpFilename").withRequiredArg().ofType(String.class);

    relationDictionary = parser.accepts("relationDictionary").withRequiredArg().ofType(String.class);

    squareLoss = parser.accepts("squareLoss");
    klLoss = parser.accepts("klLoss");

    minValueToPrint = parser.accepts("minValueToPrint").withRequiredArg().ofType(Double.class).defaultsTo(0.00);
  }

  @Override
  public void run(OptionSet options) {
    Cvsm trainedModel = IoUtils.readSerializedObject(options.valueOf(model), Cvsm.class);
    IndexedList<String> relDict = null;
    if (options.has(relationDictionary)) {
      relDict = IndexedList.create(IoUtils.readLines(options.valueOf(relationDictionary)));
    }

    if (options.has(testFilename)) {
      List<CvsmExample> examples = CvsmUtils.readTrainingData(options.valueOf(testFilename));

      double loss = 0.0;
      for (CvsmExample example : examples) {
        CvsmTree tree = null;
        if (options.has(squareLoss)) {
          tree = new CvsmSquareLossTree(example.getTargets(),
              trainedModel.getInterpretationTree(example.getLogicalForm()));
        } else if (options.has(klLoss)) {
          tree = new CvsmKlLossTree(example.getTargets(),
              trainedModel.getInterpretationTree(example.getLogicalForm()));
        } else {
          tree = new CvsmValueLossTree(trainedModel.getInterpretationTree(example.getLogicalForm()));
        }
        double exampleLoss = tree.getLoss();
        loss += exampleLoss;

        if (relDict == null) {
          System.out.println(exampleLoss + " " + Arrays.toString(example.getTargets().getValues()) 
              + " " + Arrays.toString(tree.getValue().getTensor().getValues()) + " " + example.getLogicalForm());
        } else { 
          Tensor targetTensor = example.getTargets();
          Tensor predictedTensor = tree.getValue().getTensor();

          Backpointers backpointers = new Backpointers();
          targetTensor.maxOutDimensions(targetTensor.getDimensionNumbers(), backpointers);
          int targetRel = (int) backpointers.getBackpointer(0);
          double targetProb = predictedTensor.get(targetRel);

          backpointers = new Backpointers();
          predictedTensor.maxOutDimensions(predictedTensor.getDimensionNumbers(), backpointers);
          int predictedRel = (int) backpointers.getBackpointer(0);
          double predictedProb = predictedTensor.get(predictedRel);

          System.out.println(exampleLoss + " " + relDict.get(targetRel) + " " + targetProb
              + " " + relDict.get(predictedRel) + " " + predictedProb + " " + example.getLogicalForm());
        }
      }
      System.out.println("AVERAGE LOSS: " + (loss / examples.size()) + " (" + loss + " / " + examples.size() + ")");
    } else if (options.has(vectorDumpFilename)) {
      ExpressionParser parser = new ExpressionParser(); 
      for (String line : IoUtils.readLines(options.valueOf(vectorDumpFilename))) {
        Expression lf = parser.parseSingleExpression(line);
        Tensor tensor = trainedModel.getInterpretationTree(lf).getValue().getTensor();
        Preconditions.checkState(tensor.getDimensionNumbers().length == 1);

        for (long i = 0; i < tensor.getMaxKeyNum(); i++) {
          System.out.print(tensor.get(i));
          System.out.print(" ");
        }
        System.out.print("\n");
      }
    } else {
      Expression lf = (new ExpressionParser()).parseSingleExpression(
          Joiner.on(" ").join(options.nonOptionArguments()));

      Tensor tensor = trainedModel.getInterpretationTree(lf).getValue().getTensor();
      // To make printing concise, remove values whose magnitude 
      // is too small.
      if (options.valueOf(minValueToPrint) > 0) {
	  Tensor positiveKeys = tensor.findKeysLargerThan(options.valueOf(minValueToPrint));
	  Tensor negativeKeys = tensor.elementwiseProduct(-1.0).findKeysLargerThan(options.valueOf(minValueToPrint));
	  Tensor indicators = positiveKeys.elementwiseAddition(negativeKeys);
	  tensor = tensor.elementwiseProduct(indicators);
      }

      if (relDict == null || tensor.getDimensionNumbers().length > 1 || tensor.getDimensionSizes()[0] != relDict.size()) {
	  System.out.println(tensor);
      } else {
        DiscreteVariable varType = new DiscreteVariable("var", relDict.items());
        VariableNumMap var = VariableNumMap.singleton(0, "var", varType);

        DiscreteFactor factor = new TableFactor(var, tensor); 
        System.out.println(factor.describeAssignments(factor.getMostLikelyAssignments(relDict.size())));
      }
    }
  }

  public static void main(String[] args) {
    new TestCvsm().run(args);
  }
}
