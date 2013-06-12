package com.jayantkrish.jklol.util;

import java.util.List;

import com.google.common.collect.Lists;

/**
 * Parser for partitioning comma-separated strings.
 * 
 * @author jayant
 */
public class CsvParser {
  private final char separator;
  private final char quote;
  private final char escape;
  
  public static char DEFAULT_SEPARATOR = ',';
  public static char DEFAULT_QUOTE = '\"';
  public static char DEFAULT_ESCAPE = '\\';
  public static char NULL_ESCAPE = Character.MIN_VALUE;

  public CsvParser(char separator, char quote, char escape) {
    super();
    this.separator = separator;
    this.quote = quote;
    this.escape = escape;
  }
  
  public static CsvParser defaultParser() {
    return new CsvParser(',', '\"', '\\');
  }
  
  public static CsvParser noEscapeParser() {
    return new CsvParser(',', '\"', NULL_ESCAPE);
  }

  public String[] parseLine(String line) {
    List<String> parts = Lists.newArrayList();
    
    boolean inQuotes = false;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < line.length(); i++) {
      char curChar = line.charAt(i);
      if (curChar == separator) {
        if (inQuotes) {
          sb.append(curChar);
        } else {
          parts.add(sb.toString());
          sb.delete(0, sb.length());
        }
      } else if (curChar == quote) {
        inQuotes = !inQuotes;
      } else if (curChar == escape) {
        sb.append(line.charAt(i + 1));
        i = i+1;
      } else {
        sb.append(curChar);
      }
    }
    parts.add(sb.toString());

    return parts.toArray(new String[0]);
  }
}
