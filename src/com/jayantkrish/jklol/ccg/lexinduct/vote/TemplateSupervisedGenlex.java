package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class TemplateSupervisedGenlex implements Genlex {

  private final int maxTokens;

  private final List<LexicalTemplate> templates;

  public TemplateSupervisedGenlex(int maxTokens, List<LexicalTemplate> templates) {
    this.maxTokens = maxTokens;
    this.templates = Lists.newArrayList(templates);
  }

  @Override
  public Collection<LexiconEntry> genlex(CcgExample example) {
    AnnotatedSentence sentence = example.getSentence();
    Expression2 lf = example.getLogicalForm();

    Set<String> freeVars = StaticAnalysis.getFreeVariables(lf);
    List<String> predicates = Lists.newArrayList();
    List<Type> predicateTypes = Lists.newArrayList();
    Map<String, String> typeReplacementMap = Maps.newHashMap();
    for (String freeVar : freeVars) {
      predicates.add(freeVar);
      predicateTypes.add(StaticAnalysis.inferType(Expression2.constant(freeVar), StaticAnalysis.TOP,
          typeReplacementMap));
    }

    List<String> tokens = sentence.getWords();
    Set<LexiconEntry> entries = Sets.newHashSet();
    for (int numTokens = 1; numTokens <= maxTokens; numTokens++) {
      for (int i = 0; i <= tokens.size() - numTokens; i++) {
        List<String> curTokens = tokens.subList(i, i + numTokens);
        
        for (int j = 0; j < templates.size(); j++) {
          entries.addAll(templates.get(j).instantiate(curTokens, predicates, predicateTypes));
        }
      }
    }

    return entries;
  }
}
