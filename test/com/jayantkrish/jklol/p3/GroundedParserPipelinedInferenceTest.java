package com.jayantkrish.jklol.p3;

import com.jayantkrish.jklol.ccg.CcgCkyInference;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.p3.P3BeamInference;

public class GroundedParserPipelinedInferenceTest extends GroundedParserTest {

  public GroundedParserPipelinedInferenceTest() {
    super(new P3BeamInference(CcgCkyInference.getDefault(100),
        ExpressionSimplifier.lambdaCalculus(), 10, 100, false));
  }
}
