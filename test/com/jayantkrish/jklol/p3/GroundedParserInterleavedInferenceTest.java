package com.jayantkrish.jklol.p3;

import com.jayantkrish.jklol.p3.P3InterleavedInference;

public class GroundedParserInterleavedInferenceTest extends GroundedParserTest {

  public GroundedParserInterleavedInferenceTest() {
    super(new P3InterleavedInference(10, -1));
  }
}
