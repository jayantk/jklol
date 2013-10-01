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
  private final int beamSize;
  private final long maxParseTimeMillis;

  // May be null, in which case supertagging is not used.
  private final Supertagger supertagger;
  private final double multitagThreshold;

  public SupertaggingCcgParser(CcgParser parser, int beamSize, long maxParseTimeMillis,
      Supertagger supertagger, double multitagThreshold) {
    this.parser = Preconditions.checkNotNull(parser);
    Preconditions.checkArgument(beamSize > 0);
    this.beamSize = beamSize;
    this.maxParseTimeMillis = maxParseTimeMillis;
    
    Preconditions.checkArgument(supertagger == null || multitagThreshold >= 0.0);
    this.supertagger = supertagger;
    this.multitagThreshold = multitagThreshold;
  }

  public List<CcgParse> beamSearch(List<String> terminals, List<String> posTags, ChartFilter inputFilter) {
    ChartFilter filter = inputFilter;
    if (supertagger != null) {
      List<WordAndPos> supertaggerInput = WordAndPos.createExample(terminals, posTags);
      SupertaggedSentence supertaggedSentence = supertagger.multitag(supertaggerInput, multitagThreshold);
      
      filter = ConjunctionChartFilter.create(filter,
          new SupertagChartFilter(supertaggedSentence.getLabels()));
    }
    return parser.beamSearch(terminals, posTags, beamSize, filter, new NullLogFunction(), maxParseTimeMillis);
  }
  
  public List<CcgParse> beamSearch(List<String> terminals, List<String> posTags) {
    return beamSearch(terminals, posTags, null);
  }
}
