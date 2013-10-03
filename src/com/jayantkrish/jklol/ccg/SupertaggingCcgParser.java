package com.jayantkrish.jklol.ccg;

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
  private final double multitagThreshold;

  public SupertaggingCcgParser(CcgParser parser, CcgInference inference,
      Supertagger supertagger, double multitagThreshold) {
    this.parser = Preconditions.checkNotNull(parser);
    this.inference = Preconditions.checkNotNull(inference);
    
    Preconditions.checkArgument(supertagger == null || multitagThreshold >= 0.0);
    this.supertagger = supertagger;
    this.multitagThreshold = multitagThreshold;
  }

  public CcgParse parse(List<String> terminals, List<String> posTags, ChartFilter inputFilter) {
    ChartFilter filter = inputFilter;
    SupertaggedSentence supertaggedSentence = null;
    if (supertagger != null) {
      List<WordAndPos> supertaggerInput = WordAndPos.createExample(terminals, posTags);
      supertaggedSentence = supertagger.multitag(supertaggerInput, multitagThreshold);
      
      filter = ConjunctionChartFilter.create(filter,
          new SupertagChartFilter(supertaggedSentence.getLabels()));
    } else {
      supertaggedSentence = SupertaggedSentence.createWithUnobservedSupertags(terminals, posTags);
    }
    return inference.getBestParse(parser, supertaggedSentence, filter, new NullLogFunction());
  }

  public CcgParse parse(List<String> terminals, List<String> posTags) {
    return parse(terminals, posTags, null);
  }
}
