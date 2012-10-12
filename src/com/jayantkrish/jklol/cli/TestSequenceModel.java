package com.jayantkrish.jklol.cli;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.inference.JunctionTree;
import com.jayantkrish.jklol.inference.MaxMarginalSet;
import com.jayantkrish.jklol.models.FactorGraph;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicFactorGraph;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Tests a sequence model, which is serialized to disk as a {@code DynamicFactorGraph}.
 *
 * @author jayantk
 */
public class TestSequenceModel {

  public static void main(String[] args) {
    OptionParser parser = new OptionParser();
    // Required arguments.
    OptionSpec<String> model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    OptionSet options = parser.parse(args);

    // Read in the serialized model.
    DynamicFactorGraph sequenceModel = null;
    FileInputStream fis = null;
    ObjectInputStream in = null;
    try {
      fis = new FileInputStream(options.valueOf(model));
      in = new ObjectInputStream(fis);
      sequenceModel = (DynamicFactorGraph) in.readObject();
      in.close();
    } catch(IOException ex) {
      ex.printStackTrace();
      System.exit(1);
    } catch(ClassNotFoundException ex) {
      ex.printStackTrace();
      System.exit(1);
    }

    // Read in the words to tag.
    List<String> wordsToTag = options.nonOptionArguments();

    // Construct an assignment from the input words.
    List<Assignment> inputs = Lists.newArrayList();
    VariableNumMap plateVars = sequenceModel.getVariables().getPlate(TrainSequenceModel.PLATE_NAME)
        .getFixedVariables();
    VariableNumMap x = plateVars.getVariablesByName(TrainSequenceModel.INPUT_NAME);
    VariableNumMap y = plateVars.getVariablesByName(TrainSequenceModel.OUTPUT_NAME);
    for (String word : wordsToTag) {
      Assignment input = x.outcomeArrayToAssignment(word);
      inputs.add(input);
    }
    DynamicAssignment dynamicInput = DynamicAssignment
        .createPlateAssignment(TrainSequenceModel.PLATE_NAME, inputs);

    // Compute the best assignment of label variables.
    FactorGraph fg = sequenceModel.conditional(dynamicInput);    
    JunctionTree jt = new JunctionTree();
    MaxMarginalSet maxMarginals = jt.computeMaxMarginals(fg);
    Assignment bestAssignment = maxMarginals.getNthBestAssignment(0);

    // Map the assignment back to plate indexes, then print out the labels.
    DynamicAssignment prediction = sequenceModel.getVariables()
        .toDynamicAssignment(bestAssignment, fg.getAllVariables());
    StringBuilder sb = new StringBuilder();
    for (Assignment plateAssignment : prediction
             .getPlateFixedAssignments(TrainSequenceModel.PLATE_NAME)) {
      List<Object> values = plateAssignment.getValues();
      sb.append(values.get(0) + "/" + values.get(1) + " ");
    }

    // Print the predicted labels.
    System.out.println(sb);
  }
}