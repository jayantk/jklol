package com.jayantkrish.jklol.pos;

import java.util.List;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.evaluation.Example;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.models.dynamic.DynamicAssignment;
import com.jayantkrish.jklol.models.dynamic.DynamicVariableSet;
import com.jayantkrish.jklol.models.parametric.ParametricFactorGraph;
import com.jayantkrish.jklol.pos.PosTaggedSentence.LocalContext;
import com.jayantkrish.jklol.preprocessing.FeatureVectorGenerator;
import com.jayantkrish.jklol.util.Assignment;
import com.jayantkrish.jklol.util.IoUtils;

public class PosTaggerUtils {

  public static final String PLATE_NAME = "plate";
  public static final String INPUT_NAME = "wordVector";
  public static final String OUTPUT_NAME = "pos";

  public static final String WORD_LABEL_FACTOR = "wordLabelFactor";
  public static final String TRANSITION_FACTOR = "transition";

    
  public static List<PosTaggedSentence> readTrainingData(String trainingFilename) {
    List<PosTaggedSentence> sentences = Lists.newArrayList();
    for (String line : IoUtils.readLines(trainingFilename)) {
      sentences.add(PosTaggedSentence.parseFrom(line));
    }
    return sentences;
  }
  
  public static List<LocalContext> extractContextsFromData(List<PosTaggedSentence> sentences) {
    List<LocalContext> contexts = Lists.newArrayList();
    for (PosTaggedSentence sentence : sentences) {
      contexts.addAll(sentence.getLocalContexts());
    }
    return contexts;
  }

  public static Example<DynamicAssignment, DynamicAssignment> reformatTrainingData(
      PosTaggedSentence sentence, FeatureVectorGenerator<LocalContext> featureGen,
      ParametricFactorGraph model) {
    List<PosTaggedSentence> sentences = Lists.newArrayList(sentence);
    List<Example<DynamicAssignment, DynamicAssignment>> examples = reformatTrainingData(
        sentences, featureGen, model);
    return examples.get(0);
  }
  
  /**
   * Converts training data as sentences into assignments that can be used 
   * for parameter estimation. 
   * 
   * @param sentences
   * @param featureGen
   * @param model
   * @return
   */
  public static List<Example<DynamicAssignment, DynamicAssignment>> reformatTrainingData(
      List<PosTaggedSentence> sentences, FeatureVectorGenerator<LocalContext> featureGen,
      ParametricFactorGraph model) {
    DynamicVariableSet plate = model.getVariables().getPlate(PLATE_NAME);
    VariableNumMap x = plate.getFixedVariables().getVariablesByName(INPUT_NAME);
    VariableNumMap y = plate.getFixedVariables().getVariablesByName(OUTPUT_NAME);

    List<Example<DynamicAssignment, DynamicAssignment>> examples = Lists.newArrayList();
    for (PosTaggedSentence sentence : sentences) { 
      List<Assignment> inputs = Lists.newArrayList();
      List<Assignment> outputs = Lists.newArrayList();

      List<LocalContext> contexts = sentence.getLocalContexts();
      List<String> posTags = sentence.getPos();
      for (int i = 0; i < contexts.size(); i ++) {
        inputs.add(x.outcomeArrayToAssignment(featureGen.apply(contexts.get(i))));
        outputs.add(y.outcomeArrayToAssignment(posTags.get(i)));
      }
      DynamicAssignment input = DynamicAssignment.createPlateAssignment(PLATE_NAME, inputs);
      DynamicAssignment output = DynamicAssignment.createPlateAssignment(PLATE_NAME, outputs);
      examples.add(Example.create(input, output));
    }

    return examples;
  }
}
