package com.jayantkrish.jklol.ccg.lexinduct.vote;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.lambda.ExplicitTypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.nlpannotation.AnnotatedSentence;

public class TemplateGenlex implements Genlex {
  
  private final int maxTokens;
  
  private final Set<LexicalTemplate> templates;

  private final List<String> predicates;
  private final List<Type> predicateTypes;
  
  public TemplateGenlex(int maxTokens, Iterable<LexicalTemplate> templates, List<String> predicates,
      List<Type> predicateTypes) {
    this.maxTokens = maxTokens;
    this.templates = Sets.newHashSet(templates);
    this.predicates = ImmutableList.copyOf(predicates);
    this.predicateTypes = ImmutableList.copyOf(predicateTypes);
  }
  
  public static void extractPredicatesFromExamples(Collection<CcgExample> examples,
      List<String> predicates, List<Type> predicateTypes) {
    Set<String> predicateSet = Sets.newHashSet();
    for (CcgExample example : examples) {
      predicateSet.addAll(StaticAnalysis.getFreeVariables(example.getLogicalForm()));
    }

    TypeDeclaration typeDeclaration = ExplicitTypeDeclaration.getDefault();
    for (String predicate : predicateSet) {
      predicates.add(predicate);
      predicateTypes.add(StaticAnalysis.inferType(Expression2.constant(predicate),
          TypeDeclaration.TOP, typeDeclaration));
    }
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
