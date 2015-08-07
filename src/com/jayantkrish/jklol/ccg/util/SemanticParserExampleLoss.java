package com.jayantkrish.jklol.ccg.util;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Data structure for storing the loss function evaluation on
 * a single example. Can be output in JSON format for post-hoc
 * data analysis.
 * 
 * @author jayantk
 *
 */
public class SemanticParserExampleLoss {
  private final CcgExample example;
  private final Expression2 predictedLf;
  private final List<DependencyStructure> predictedDeps;

  private final List<Integer> lexiconIndexes; 
  private final List<Object> lexiconTriggers;
  private final List<CcgCategory> lexiconEntries;

  private final Expression2 correctLf;
  private final boolean parsable;
  private final boolean correct;
  private final boolean correctLfPossible;

  public SemanticParserExampleLoss(CcgExample example, Expression2 predictedLf,
      List<DependencyStructure> predictedDeps, List<Integer> lexiconIndexes,
      List<Object> lexiconTriggers, List<CcgCategory> lexiconEntries, Expression2 correctLf,
      boolean parsable, boolean correct, boolean correctLfPossible) {
    this.example = Preconditions.checkNotNull(example);

    this.predictedLf = predictedLf;
    this.predictedDeps = Preconditions.checkNotNull(predictedDeps);

    this.lexiconIndexes = Preconditions.checkNotNull(lexiconIndexes);
    this.lexiconTriggers = Preconditions.checkNotNull(lexiconTriggers);
    this.lexiconEntries = Preconditions.checkNotNull(lexiconEntries);

    this.correctLf = Preconditions.checkNotNull(correctLf);
    this.parsable = parsable;
    this.correct = correct;
    this.correctLfPossible = correctLfPossible;
  }

  /**
   * Writes a collection of losses to {@code filename} in JSON
   * format.
   * 
   * @param filename
   * @param losses
   */
  public static void writeJsonToFile(String filename, List<SemanticParserExampleLoss> losses) {
    List<String> lines = Lists.newArrayList();
    for (SemanticParserExampleLoss loss : losses) {
      lines.add(loss.toJson());
    }
    IoUtils.writeLines(filename, lines);
  }

  public CcgExample getExample() {
    return example;
  }

  public Expression2 getPredictedLf() {
    return predictedLf;
  }

  public List<DependencyStructure> getPredictedDeps() {
    return predictedDeps;
  }

  public List<Integer> getLexiconIndexes() {
    return lexiconIndexes;
  }

  public List<Object> getLexiconTriggers() {
    return lexiconTriggers;
  }

  public List<CcgCategory> getLexiconEntries() {
    return lexiconEntries;
  }

  public Expression2 getCorrectLf() {
    return correctLf;
  }

  public boolean isParsable() {
    return parsable;
  }

  public boolean isCorrect() {
    return correct;
  }

  public boolean isCorrectLfPossible() {
    return correctLfPossible;
  }

  public String toJson() {
    Map<String, Object> jsonDict = Maps.newHashMap();
    jsonDict.put("sentence", Joiner.on(" ").join(example.getSentence().getWords()));
    jsonDict.put("pos", Joiner.on(" ").join(example.getSentence().getPosTags()));
    jsonDict.put("predicted_lf", predictedLf);

    List<String> depStrings = Lists.newArrayList();
    for (DependencyStructure dep : predictedDeps) {
      depStrings.add(dep.toString());
    }
    jsonDict.put("predicted_deps", depStrings);

    List<Map<String, Object>> lexiconDicts = Lists.newArrayList();
    for (int i = 0; i < lexiconIndexes.size(); i++) {
      Map<String, Object> lexiconDict = Maps.newHashMap();
      lexiconDict.put("index", lexiconIndexes.get(i));
      lexiconDict.put("trigger", lexiconTriggers.get(i));
      // TODO: this needs a toJson method.
      lexiconDict.put("entry", lexiconEntries.get(i).toCsvString());

      lexiconDicts.add(lexiconDict);
    }
    jsonDict.put("lexicon_entries", lexiconDicts);

    jsonDict.put("correct_lf", correctLf);
    jsonDict.put("parsable", parsable);
    jsonDict.put("correct", correct);
    jsonDict.put("correct_lf_possible", correctLfPossible);

    ObjectMapper mapper = new ObjectMapper();
    String s = null;
    try {
      s = mapper.writeValueAsString(jsonDict);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
    return s;
  }
}
