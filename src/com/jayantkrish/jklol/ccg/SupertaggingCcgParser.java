package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.chart.ChartFilter;
import com.jayantkrish.jklol.ccg.chart.ConjunctionChartFilter;
import com.jayantkrish.jklol.ccg.supertag.SupertagChartFilter;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.Supertagger;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.training.NullLogFunction;

public class SupertaggingCcgParser {
  private final CcgParser parser;
  private final CcgInference inference;

  // May be null, in which case supertagging is not used.
  private final Supertagger supertagger;
  private final double[] multitagThresholds;

  public SupertaggingCcgParser(CcgParser parser, CcgInference inference,
      Supertagger supertagger, double[] multitagThresholds) {
    this.parser = Preconditions.checkNotNull(parser);
    this.inference = Preconditions.checkNotNull(inference);

    Preconditions.checkArgument(supertagger == null || multitagThresholds.length > 0);
    this.supertagger = supertagger;
    this.multitagThresholds = Arrays.copyOf(multitagThresholds, multitagThresholds.length);
  }

  public CcgParseResult parse(List<String> terminals, List<String> posTags, ChartFilter inputFilter) {
    SupertaggedSentence supertaggedSentence = null;
    if (supertagger != null) {
      for (int i = 0; i < multitagThresholds.length; i++) {
        // Try parsing at each multitag threshold. If parsing succeeds,
        // immediately return the parse. Otherwise, continue to further
        // thresholds.
        List<WordAndPos> supertaggerInput = WordAndPos.createExample(terminals, posTags);
        supertaggedSentence = supertagger.multitag(supertaggerInput, multitagThresholds[i]);
        
        ChartFilter filter = ConjunctionChartFilter.create(inputFilter,
            new SupertagChartFilter(supertaggedSentence.getLabels()));

        CcgParse parse = inference.getBestParse(parser, supertaggedSentence, filter, new NullLogFunction());
        if (parse != null) {
          return new CcgParseResult(parse, supertaggedSentence, multitagThresholds[i]);
        }
      }
      // Parsing was unsuccessful at all thresholds
      return null;
    } else {
      supertaggedSentence = SupertaggedSentence.createWithUnobservedSupertags(terminals, posTags);
      CcgParse parse = inference.getBestParse(parser, supertaggedSentence, inputFilter, new NullLogFunction());
      return new CcgParseResult(parse, supertaggedSentence, 0.0);
    }
  }

  public CcgParseResult parse(List<String> terminals, List<String> posTags) {
    return parse(terminals, posTags, null);
  }
  
  public CcgParser getParser() {
    return parser;
  }
  
  public static class CcgParseResult {
    private final CcgParse parse;
    private final SupertaggedSentence sentence;
    private final double tagThreshold;

    public CcgParseResult(CcgParse parse, SupertaggedSentence sentence, double tagThreshold) {
      this.parse = Preconditions.checkNotNull(parse);
      this.sentence = Preconditions.checkNotNull(sentence);
      this.tagThreshold = tagThreshold;
    }

    public CcgParse getParse() {
      return parse;
    }

    public SupertaggedSentence getSentence() {
      return sentence;
    }

    public double getTagThreshold() {
      return tagThreshold;
    }
  }
}
