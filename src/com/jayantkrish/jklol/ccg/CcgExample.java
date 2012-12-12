package com.jayantkrish.jklol.ccg;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.util.CsvParser;

/**
 * A training example for {@code CcgLoglikelihoodOracle}. Stores an input word
 * sequence and its expected set of dependencies. May optionally contain the
 * expected syntactic structure.
 * 
 * @author jayant
 */
public class CcgExample {

  private final List<String> words;
  private final Set<DependencyStructure> dependencies;
  // May be null, in which case the true syntactic structure is unobserved.
  private final CcgSyntaxTree syntacticParse;
  
  /**
   * Create a new training example for a CCG parser.
   * 
   * @param words The input language to CCG parse.
   * @param dependencies The dependencies in the correct CCG parse of
   * {@code words}.
   * @param syntacticParse The syntactic structure of the correct CCG parse of
   * {@code words}. May be {@code null}, in which case the correct parse is 
   * treated as unobserved.
   */
  public CcgExample(List<String> words, Set<DependencyStructure> dependencies,
      CcgSyntaxTree syntacticParse) {
    this.words = Preconditions.checkNotNull(words);
    this.dependencies = Preconditions.checkNotNull(dependencies);

    this.syntacticParse = syntacticParse;
  }

  /**
   * Expected format is (space-separated words)###(,-separated dependency
   * structures)###(@@@-separated lexicon entries (optional)).
   * 
   * @param exampleString
   * @return
   */
  public static CcgExample parseFromString(String exampleString) {
    String[] parts = exampleString.split("###");
    List<String> words = Arrays.asList(parts[0].split("\\s+"));

    Set<DependencyStructure> dependencies = Sets.newHashSet();
    String[] dependencyParts = new CsvParser(CsvParser.DEFAULT_SEPARATOR, 
        CsvParser.DEFAULT_QUOTE, CsvParser.NULL_ESCAPE).parseLine(parts[1]);
    for (int i = 0; i < dependencyParts.length; i++) {
      if (dependencyParts[i].trim().length() == 0) {
        continue;
      }
      String[] dep = dependencyParts[i].split("\\s+");
      Preconditions.checkState(dep.length >= 5, "Illegal dependency string: " + dependencyParts[i]);

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
  }

  public List<String> getWords() {
    return words;
  }

  /**
   * Returns {@code true} if the syntactic structure of the correct parse is
   * observed.
   * 
   * @return
   */
  public boolean hasSyntacticParse() {
    return syntacticParse != null;
  }
  
  public CcgSyntaxTree getSyntacticParse() {
    return syntacticParse;
  }

  public Set<DependencyStructure> getDependencies() {
    return dependencies;
  }

  @Override
  public String toString() {
    return words + " " + dependencies;
  }
}
