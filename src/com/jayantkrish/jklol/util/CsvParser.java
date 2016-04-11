package com.jayantkrish.jklol.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  public static CsvParser tsvParser() {
    return new CsvParser('\t', NULL_ESCAPE, NULL_ESCAPE);
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

  public String toCsv(List<String> parts) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0 ; i < parts.size(); i++) {
      if (i > 0) {
        sb.append(separator);
      }
      sb.append(quote);
      sb.append(escape(parts.get(i)));
      sb.append(quote);
    }
    return sb.toString();
  }
  
  public String escape(String input) {
    String escapeStr = Character.toString(escape);
    String quoteStr = Character.toString(quote);
    String separatorStr = Character.toString(separator);

    // This requires regex escaping.
    input = input.replaceAll(Pattern.quote(escapeStr), Matcher.quoteReplacement(escapeStr + escapeStr));
    input = input.replaceAll(Pattern.quote(quoteStr), Matcher.quoteReplacement(escapeStr + quoteStr));
    // input = input.replaceAll(Pattern.quote(separatorStr), Matcher.quoteReplacement(escapeStr + separatorStr));

    return input;
  }
}
