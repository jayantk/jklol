package com.jayantkrish.jklol.util;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

public class CsvParserTest extends TestCase {
  
  CsvParser parser;
  
  public void setUp() {
    parser = new CsvParser(',', '"', '\\');
  }
  
  public void testNoQuotes() {
    List<String> parts = Arrays.asList(parser.parseLine("abcd,e,f,g"));
    assertEquals(Arrays.asList("abcd", "e", "f", "g"), parts);
  }
  
  public void testQuotes() {
    List<String> parts = Arrays.asList(parser.parseLine("\"abcd\",\"e\",\"f\",\"g\""));
    assertEquals(Arrays.asList("abcd", "e", "f", "g"), parts);
  }

  public void testEscapeNoQuotes() {
    List<String> parts = Arrays.asList(parser.parseLine("\"abcd\",\\,,\"\\\"f\",\\\",\"g\""));
    assertEquals(Arrays.asList("abcd", ",", "\"f", "\"", "g"), parts);
  }
  
  public void testQuotedComma() {
    List<String> parts = Arrays.asList(parser.parseLine("\"abcd\",\",\",\"f\",\"g\""));
    assertEquals(Arrays.asList("abcd", ",", "f", "g"), parts);
  }
  
  public void testEscapedQuote() {
    List<String> parts = Arrays.asList(parser.parseLine("\"abcd\",\"\\\"\",\"\\\\\",\"f\",\"g\""));
    assertEquals(Arrays.asList("abcd", "\"", "\\", "f", "g"), parts);
  }
}
