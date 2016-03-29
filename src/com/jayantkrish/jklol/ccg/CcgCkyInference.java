package com.jayantkrish.jklol.ccg;

import java.util.List;

import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.chart.SumChartCost;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.LogFunction;

/**
 * CKY-style chart parsing inference algorithm for CCG parsing.  
 * This class holds configuration parameters for the parser, such
 * as timeouts, and supports both exact and approximate inference.
 *
 * @author jayantk
 */
public class CcgCkyInference implements CcgInference {

  // Optional constraint to use during inference. Null if
  // no constraints are imposed on the search.
  private final ChartCost searchFilter;
  
  // Size of the beam used during inference (which uses beam search).
  private final int beamSize;
  
  // Maximum number of milliseconds to spend parsing a single sentence.
  private final long maxParseTimeMillis;
  
  // Maximum number of chart entries for a single sentence.
  private final int maxChartSize;
  
  // Number of threads to use while parsing.
  private final int numThreads;

  public CcgCkyInference(ChartCost searchFilter, int beamSize, long maxParseTimeMillis,
      int maxChartSize, int numThreads) {
    this.searchFilter = searchFilter;
    this.beamSize = beamSize;
    this.maxParseTimeMillis = maxParseTimeMillis;
    this.maxChartSize = maxChartSize;
    this.numThreads = numThreads;
  }

  /**
   * Get a CKY inference algorithm with sane default parameters.
   * 
   * @param beamSize
   * @return
   */
  public static CcgCkyInference getDefault(int beamSize) {
    return new CcgCkyInference(null, beamSize, -1, Integer.MAX_VALUE, 1);
  }

  @Override
  public CcgParse getBestParse(CcgParser parser, AnnotatedSentence sentence,
      ChartCost chartFilter, LogFunction log) {
    ChartCost filter = SumChartCost.create(searchFilter, chartFilter);
    
    return parser.parse(sentence, filter, log, maxParseTimeMillis,
        maxChartSize, numThreads);
  }

  @Override
  public List<CcgParse> beamSearch(CcgParser parser, AnnotatedSentence sentence,
      ChartCost chartFilter, LogFunction log) {
    ChartCost filter = SumChartCost.create(searchFilter, chartFilter);

    return parser.beamSearch(sentence, beamSize, filter, log,
        maxParseTimeMillis, maxChartSize, numThreads);
  }
}
