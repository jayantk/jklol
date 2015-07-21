package com.jayantkrish.jklol.ccg;

import java.util.Arrays;

import junit.framework.TestCase;

public class LexiconEntryTest extends TestCase {

  public void testParseFromJson() {
    String json = "{\"words\": [\"the\", \"man\"], \"ccgCategory\": {\"syntax\" : \"N{0}\", \"logicalForm\" : \"foo\"}}"; 
    LexiconEntry entry = LexiconEntry.parseFromJson(json);
    
    assertEquals(Arrays.asList("the", "man"), entry.getWords());
    assertEquals("N{0}", entry.getCategory().getSyntax().toString());
  }
}
