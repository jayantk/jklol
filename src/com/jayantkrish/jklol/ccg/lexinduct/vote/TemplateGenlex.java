package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class TemplateGenlex implements Genlex {
  
  private final int maxTokens;
  
  private final Set<LexicalTemplate> templates;

  private final List<String> predicates;
  private final List<Type> predicateTypes;
  
  public TemplateGenlex(int maxTokens, Set<LexicalTemplate> templates, List<String> predicates,
      List<Type> predicateTypes) {
    this.maxTokens = maxTokens;
    this.templates = Sets.newHashSet(templates);
    this.predicates = ImmutableList.copyOf(predicates);
    this.predicateTypes = ImmutableList.copyOf(predicateTypes);
  }

  @Override
  public Collection<LexiconEntry> genlex(CcgExample example) {
    AnnotatedSentence sentence = example.getSentence();
    List<String> tokens = sentence.getWords();
    
    Set<LexiconEntry> entries = Sets.newHashSet();
    for (int numTokens = 1; numTokens <= maxTokens; numTokens++) {
      for (int i = 0; i <= tokens.size() - numTokens; i++) {
        List<String> curTokens = tokens.subList(i, i + numTokens);
        
        for (LexicalTemplate template : templates) {
          entries.addAll(template.instantiate(curTokens, predicates, predicateTypes));
        }
      }
    }

    return entries;
  }
}
