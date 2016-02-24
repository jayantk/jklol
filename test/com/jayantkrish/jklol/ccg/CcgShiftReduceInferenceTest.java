package com.jayantkrish.jklol.ccg;

import java.util.List;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgShiftReduceInference;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class CcgShiftReduceInferenceTest extends CcgParserTest {

  @Override
  public List<CcgParse> beamSearch(CcgParser parser, List<String> words, List<String> posTags, int beamSize) {
    CcgShiftReduceInference inference = new CcgShiftReduceInference(beamSize, -1);
    return inference.beamSearch(parser, new AnnotatedSentence(words, posTags), null, null);
  }
}
