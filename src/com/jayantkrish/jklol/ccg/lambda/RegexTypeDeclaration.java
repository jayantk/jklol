package com.jayantkrish.jklol.ccg.lambda;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jayantkrish.jklol.util.CsvParser;

/**
 * Type declaration that determines type information
 * by matching each constant against a list of regular
 * expression rules. 
 * 
 * @author jayantk
 *
 */
public class RegexTypeDeclaration extends AbstractTypeDeclaration {
  private static final long serialVersionUID = 1L;

  private List<Pattern> patterns;
  private List<Type> types;
  
  public RegexTypeDeclaration(Map<String, String> supertypeMap,
      List<Pattern> patterns, List<Type> types) {
    super(supertypeMap);
    this.patterns = ImmutableList.copyOf(patterns);
    this.types = ImmutableList.copyOf(types);
  }
  
  public static RegexTypeDeclaration fromCsv(List<String> lines) {
    CsvParser parser = CsvParser.defaultParser();
    ExpressionParser<Type> typeParser = ExpressionParser.typeParser();
    List<Pattern> patterns = Lists.newArrayList();
    List<Type> types = Lists.newArrayList();
    for (String line : lines) {
      String[] parts = parser.parseLine(line);
      
      patterns.add(Pattern.compile(parts[0]));
      types.add(typeParser.parse(parts[1]));
    }
    return new RegexTypeDeclaration(Maps.newHashMap(), patterns, types);
  }
  
  public static RegexTypeDeclaration fromStringArrays(String[][] lines) {
    ExpressionParser<Type> typeParser = ExpressionParser.typeParser();
    List<Pattern> patterns = Lists.newArrayList();
    List<Type> types = Lists.newArrayList();
    for (String[] parts : lines) {
      patterns.add(Pattern.compile(parts[0]));
      types.add(typeParser.parse(parts[1]));
    }
    return new RegexTypeDeclaration(Maps.newHashMap(), patterns, types);
  }

  @Override
  public Type getType(String constant) {
    for (int i = 0; i < patterns.size(); i++) {
      if (patterns.get(i).matcher(constant).matches()) {
        return types.get(i);
      }
    }
    return TypeDeclaration.TOP;
  }
}
