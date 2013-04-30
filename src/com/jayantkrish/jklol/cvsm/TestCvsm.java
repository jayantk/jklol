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
import com.jayantkrish.jklol.util.IoUtils;

public class TestCvsm extends AbstractCli {
  
  private OptionSpec<String> model;
  private OptionSpec<String> testFilename;
  
  private OptionSpec<Void> squareLoss;
  private OptionSpec<Void> klLoss;
  
  public TestCvsm() {
    super();
  }

  @Override
  public void initializeOptions(OptionParser parser) {
    model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    testFilename = parser.accepts("testFilename").withRequiredArg().ofType(String.class);
    
    squareLoss = parser.accepts("squareLoss");
    klLoss = parser.accepts("klLoss");
  }

  @Override
  public void run(OptionSet options) {
    Cvsm trainedModel = IoUtils.readSerializedObject(options.valueOf(model), Cvsm.class);
    
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

        System.out.println(exampleLoss + " " + Arrays.toString(example.getTargets().getValues()) 
			   + " " + Arrays.toString(tree.getValue().getTensor().getValues()) + " " + example.getLogicalForm());
      }
      System.out.println("AVERAGE LOSS: " + (loss / examples.size()) + " (" + loss + " / " + examples.size() + ")");
    } else {
      Expression lf = (new ExpressionParser()).parseSingleExpression(
          Joiner.on(" ").join(options.nonOptionArguments()));
      System.out.println(trainedModel.getInterpretationTree(lf).getValue().getTensor());
    }
  }
  
  public static void main(String[] args) {
    new TestCvsm().run(args);
  }
}
