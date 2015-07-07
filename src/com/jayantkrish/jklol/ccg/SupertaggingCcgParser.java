package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.chart.ChartCost;
import com.jayantkrish.jklol.ccg.supertag.ListSupertaggedSentence;
import com.jayantkrish.jklol.ccg.supertag.Supertagger;
import com.jayantkrish.jklol.ccg.supertag.WordAndPos;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;
import com.jayantkrish.jklol.training.NullLogFunction;

/**
 * A combined CCG parser and supertagger. This parser accepts 
 * POS-tagged sentences, supertags them, then parses them.
 * The parser can be configured to use a backoff strategy that
 * adjusts the supertagger's multitag threshold (which affects
 * the number of supertags assigned to each token) when CCG
 * parsing fails.
 *
 * @author jayantk
 */
public class SupertaggingCcgParser {
  private final CcgParser parser;
  private final CcgInference inference;

  // May be null, in which case supertagging is not used.
  private final Supertagger supertagger;
  private final double[] multitagThresholds;
  private final String supertaggerAnnotationName;


  public SupertaggingCcgParser(CcgParser parser, CcgInference inference,
      Supertagger supertagger, double[] multitagThresholds, String supertaggerAnnotationName) {
    this.parser = Preconditions.checkNotNull(parser);
    this.inference = Preconditions.checkNotNull(inference);

    Preconditions.checkArgument(supertagger == null || multitagThresholds.length > 0);
    this.supertagger = supertagger;
    this.multitagThresholds = Arrays.copyOf(multitagThresholds, multitagThresholds.length);
    this.supertaggerAnnotationName = supertaggerAnnotationName;
  }

  /**
   * CCG parses {@code sentence}. Any supertags in {@code sentence}
   * are ignored; instead, this method uses the supertags from 
   * the parser's internal supertagger. {@code inputFilter} is an
   * additional cost to use during parsing. {@code inputFilter} may be
   * {@code null}, in which case no additional cost is used. 
   *  
   * @param sentence
   * @param inputFilter
   * @return
   */
  public CcgParseResult parse(AnnotatedSentence sentence, ChartCost inputFilter) {
    AnnotatedSentence annotatedSentence = null;
    if (supertagger != null) {
      for (int i = 0; i < multitagThresholds.length; i++) {
        // Try parsing at each multitag threshold. If parsing succeeds,
        // immediately return the parse. Otherwise, continue to further
        // thresholds.
        List<WordAndPos> supertaggerInput = sentence.getWordsAndPosTags();
        ListSupertaggedSentence supertaggedSentence = supertagger
            .multitag(supertaggerInput, multitagThresholds[i]);
        
        annotatedSentence = sentence.addAnnotation(supertaggerAnnotationName,
            supertaggedSentence.getAnnotation());

        CcgParse parse = inference.getBestParse(parser, annotatedSentence, inputFilter,
            new NullLogFunction());
        if (parse != null) {
          return new CcgParseResult(parse, annotatedSentence, multitagThresholds[i]);
        }
      }
      // Parsing was unsuccessful at all thresholds
      return null;
    } else {
      CcgParse parse = inference.getBestParse(parser, sentence, inputFilter, new NullLogFunction());
      if (parse != null) {
        return new CcgParseResult(parse, sentence, 0.0);
      } else {
        return null;
      }
    }
  }

  /**
   * Same as {@link #parse(AnnotatedSentence, ChartCost)},
   * without a {@code ChartCost}.
   *  
   * @param sentence
   * @return
   */
  public CcgParseResult parse(AnnotatedSentence sentence) {
    return parse(sentence, null);
  }

  public CcgParser getParser() {
    return parser;
  }

  public static class CcgParseResult {
    private final CcgParse parse;
    private final AnnotatedSentence sentence;
    private final double tagThreshold;

    public CcgParseResult(CcgParse parse, AnnotatedSentence sentence, double tagThreshold) {
      this.parse = Preconditions.checkNotNull(parse);
      this.sentence = Preconditions.checkNotNull(sentence);
      this.tagThreshold = tagThreshold;
    }

    public CcgParse getParse() {
      return parse;
    }

    public AnnotatedSentence getSentence() {
      return sentence;
    }

    public double getTagThreshold() {
      return tagThreshold;
    }
  }
}
