package com.jayantkrish.jklol.ccg.inference;

import java.util.List;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.CcgParserTest;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class CcgLeftToRightInferenceTest extends CcgParserTest {

  @Override
  public List<CcgParse> beamSearch(CcgParser parser, List<String> words, List<String> posTags, int beamSize) {
    CcgLeftToRightInference inference = new CcgLeftToRightInference(beamSize);
    return inference.parse(parser, new AnnotatedSentence(words, posTags));
  }
}
