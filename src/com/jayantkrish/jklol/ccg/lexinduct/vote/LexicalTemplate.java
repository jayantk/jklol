package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.util.CsvParser;

public class LexicalTemplate {

  private final HeadedSyntacticCategory syntax;
  private final Expression2 lf;
  
  private final List<String> args;
  private final List<Type> argTypes;
  
  public LexicalTemplate(HeadedSyntacticCategory syntax, Expression2 lf, 
      List<String> args, List<Type> argTypes) {
    this.syntax = Preconditions.checkNotNull(syntax);
    this.lf = Preconditions.checkNotNull(lf);
    this.args = ImmutableList.copyOf(args);
    this.argTypes = ImmutableList.copyOf(argTypes);
  }
  
  public static LexicalTemplate fromCsv(String csvString) {
    CsvParser parser = LexiconEntry.getCsvParser();
    String[] parts = parser.parseLine(csvString);
    
    HeadedSyntacticCategory cat = HeadedSyntacticCategory.parseFrom(parts[0]);
    Expression2 lf = ExpressionParser.expression2().parse(parts[1]);

    List<String> vars = Lists.newArrayList();
    List<Type> varTypes = Lists.newArrayList();
    for (int j = 2; j < parts.length; j += 2) {
      vars.add(parts[j]);
      varTypes.add(ExpressionParser.typeParser().parse(parts[j + 1]));
    }

    return new LexicalTemplate(cat, lf, vars, varTypes);
  }

  public static List<LexicalTemplate> fromCsv(Iterable<String> csvStrings) {
    List<LexicalTemplate> templates = Lists.newArrayList();
    for (String csvString : csvStrings) {
      templates.add(LexicalTemplate.fromCsv(csvString));
    }
    return templates;
  }

  public Set<LexiconEntry> instantiate(List<String> tokens,
      List<String> predicates, List<Type> predicateTypes) {
    Preconditions.checkArgument(predicates.size() == predicateTypes.size());

    Multimap<Type, String> typePredicateMap = HashMultimap.create();
    for (int i = 0; i < predicates.size(); i++) {
      typePredicateMap.put(predicateTypes.get(i), predicates.get(i));
    }
    
    Set<LexiconEntry> entries = Sets.newHashSet();
    populateTemplate(lf, 0, tokens, typePredicateMap, entries);
    return entries;
  }
  
  private void populateTemplate(Expression2 lf, int argNum, List<String> tokens,
      Multimap<Type, String> typePredicateMap, Set<LexiconEntry> entries) {
    if (argNum >= args.size()) {
      entries.add(new LexiconEntry(tokens, CcgCategory.fromSyntaxLf(syntax, lf)));
    } else {
      for (String possiblePredicate : typePredicateMap.get(argTypes.get(argNum))) {
        Expression2 currentLf = lf.substitute(args.get(argNum), possiblePredicate);
        populateTemplate(currentLf, argNum + 1, tokens, typePredicateMap, entries);
      }
    }
  }
}
