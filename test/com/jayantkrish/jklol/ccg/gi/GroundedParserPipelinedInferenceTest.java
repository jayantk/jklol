package com.jayantkrish.jklol.ccg.gi;

import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;

public class GroundedParserPipelinedInferenceTest extends GroundedParserTest {

  public GroundedParserPipelinedInferenceTest() {
    super(new GroundedParserPipelinedInference(CcgCkyInference.getDefault(100),
        ExpressionSimplifier.lambdaCalculus(), 10, 100));
  }
}
