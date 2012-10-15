package com.jayantkrish.jklol.ccg;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import au.com.bytecode.opencsv.CSVParser;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * A training example for {@code CcgLoglikelihoodOracle}. Stores an input word
 * sequence and its expected set of dependencies. May optionally contain the
 * expected lexicon entries.
 * 
 * @author jayant
 */
public class CcgExample {

  private final List<String> words;
  private final Set<DependencyStructure> dependencies;

  // These may be null, in which case the lexical entries are
  // not observed as part of the training example.
  private final List<LexiconEntry> lexiconEntries;

  /**
   * Create a new training example for a CCG parser.
   * 
   * @param words The input language to CCG parse.
   * @param dependencies The dependencies in the correct CCG parse of
   * {@code words}.
   * @param lexiconEntries The lexicon entries in the correct CCG parse of
   * {@code words}. May be {@code null}, in which case the correct lexicon
   * entries are treated as unobserved.
   */
  public CcgExample(List<String> words, Set<DependencyStructure> dependencies,
      List<LexiconEntry> lexiconEntries) {
    this.words = Preconditions.checkNotNull(words);
    this.dependencies = Preconditions.checkNotNull(dependencies);

    this.lexiconEntries = lexiconEntries;
  }

  /**
   * Expected format is (space-separated words)###(#-separated dependency
   * structures)###(@@@-separated lexicon entries (optional)).
   * 
   * @param exampleString
   * @return
   */
  public static CcgExample parseFromString(String exampleString) {
    try {
      String[] parts = exampleString.split("###");
      List<String> words = Arrays.asList(parts[0].split("\\s+"));
      
      Set<DependencyStructure> dependencies = Sets.newHashSet();
      String[] dependencyParts = new CSVParser('#').parseLine(parts[1]);
      for (int i = 0; i < dependencyParts.length; i++) {
        if (dependencyParts[i].trim().length() == 0) {
          continue;
        }
        String[] dep = dependencyParts[i].split("\\s+");
        dependencies.add(new DependencyStructure(dep[0], Integer.parseInt(dep[1]), dep[3],
            Integer.parseInt(dep[4]), Integer.parseInt(dep[2])));
      }

      List<LexiconEntry> lexiconEntries = null;
      if (parts.length >= 3) {
        // Parse out observed lexicon entries, if they are given.
        String[] lexiconLabels = parts[2].split("@@@");
        lexiconEntries = Lists.newArrayList();
        for (int i = 0; i < lexiconLabels.length; i++) {
          lexiconEntries.add(LexiconEntry.parseLexiconEntry(lexiconLabels[i]));
        }
      }

      return new CcgExample(words, dependencies, lexiconEntries);
    } catch (IOException e) {
      throw new IllegalArgumentException("Illegal example string: " + exampleString, e);
    }
  }

  public List<String> getWords() {
    return words;
  }

  /**
   * Returns {@code true} if the lexicon entries in the correct parse are
   * observed.
   * 
   * @return
   */
  public boolean hasLexiconEntries() {
    return lexiconEntries != null;
  }

  /**
   * Gets the lexicon entries used in the correct CCG parse of
   * {@code getWords()}.
   * 
   * @return
   */
  public List<LexiconEntry> getLexiconEntries() {
    return lexiconEntries;
  }

  public Set<DependencyStructure> getDependencies() {
    return dependencies;
  }

  @Override
  public String toString() {
    return words + " " + dependencies;
  }
}
