package com.jayantkrish.jklol.ccg.gi;

import com.jayantkrish.jklol.ccg.CcgCkyInference;

public class GroundedParserPipelinedInferenceTest extends GroundedParserTest {

  public GroundedParserPipelinedInferenceTest() {
    super(new GroundedParserPipelinedInference(CcgCkyInference.getDefault(100),
        10, 100, 1));
  }
}
