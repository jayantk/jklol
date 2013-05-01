package com.jayantkrish.jklol.cvsm;

import java.util.Arrays;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Joiner;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.cvsm.tree.CvsmKlLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmSquareLossTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmTree;
import com.jayantkrish.jklol.cvsm.tree.CvsmZeroOneLossTree;
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
  
  private OptionSpec<String> relationDictionary;
  
  private OptionSpec<Void> squareLoss;
  private OptionSpec<Void> klLoss;
  
  public TestCvsm() {
    super();
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    testFilename = parser.accepts("testFilename").withRequiredArg().ofType(String.class);

    relationDictionary = parser.accepts("testFilename").withRequiredArg().ofType(String.class);

    squareLoss = parser.accepts("squareLoss");
    klLoss = parser.accepts("klLoss");
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
          tree = new CvsmZeroOneLossTree(example.getTargets(),
              trainedModel.getInterpretationTree(example.getLogicalForm()));
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
          
          backpointers = new Backpointers();
          predictedTensor.maxOutDimensions(predictedTensor.getDimensionNumbers(), backpointers);
          int predictedRel = (int) backpointers.getBackpointer(0);
          
          System.out.println(exampleLoss + " " + relDict.get(targetRel) 
              + " " + relDict.get(predictedRel) + " " + example.getLogicalForm());
        }
      }
      System.out.println("AVERAGE LOSS: " + (loss / examples.size()) + " (" + loss + " / " + examples.size() + ")");
    } else {
      Expression lf = (new ExpressionParser()).parseSingleExpression(
          Joiner.on(" ").join(options.nonOptionArguments()));
      
      Tensor tensor = trainedModel.getInterpretationTree(lf).getValue().getTensor();
      if (relDict == null || tensor.getDimensionNumbers().length > 1 || tensor.getDimensionSizes()[0] != relDict.size()) {
        System.out.println(tensor);
      } else {
        DiscreteVariable varType = new DiscreteVariable("var", relDict.items());
        VariableNumMap var = VariableNumMap.singleton(0, "var", varType);
        DiscreteFactor factor = new TableFactor(var, tensor); 
        System.out.println(factor.getParameterDescription());
      }
    }
  }
  
  public static void main(String[] args) {
    new TestCvsm().run(args);
  }
}
