package com.jayantkrish.jklol.ccg.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgSyntaxTree;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.ParametricCcgParser;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.supertag.SupertaggedSentence;
import com.jayantkrish.jklol.data.DataFormat;
import com.jayantkrish.jklol.data.LineDataFormat;
import com.jayantkrish.jklol.util.CsvParser;

public class CcgExampleFormat extends LineDataFormat<CcgExample> {
  
  private final DataFormat<CcgSyntaxTree> syntaxTreeReader;
  private final boolean ignoreSemantics;
  
  public CcgExampleFormat(DataFormat<CcgSyntaxTree> syntaxTreeReader, boolean ignoreSemantics) {
    this.syntaxTreeReader = Preconditions.checkNotNull(syntaxTreeReader);
    this.ignoreSemantics = ignoreSemantics;
  }

  /**
   * Expected format is (space-separated words)###(,-separated
   * dependency structures)###(@@@-separated lexicon entries
   * (optional)).
   * 
   * @param exampleString
   * @return
   */
  @Override
  public CcgExample parseFrom(String exampleString) {
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
      Preconditions.checkState(dep.length >= 6, "Illegal dependency string: " + dependencyParts[i]);

      dependencies.add(new DependencyStructure(dep[0], Integer.parseInt(dep[2]), 
          HeadedSyntacticCategory.parseFrom(dep[1]).getCanonicalForm(), dep[4],
          Integer.parseInt(dep[5]), Integer.parseInt(dep[3])));
    }

    // Parse out a CCG syntactic tree, if one is provided.
    CcgSyntaxTree tree = null;
    List<String> posTags = null;
    if (parts.length >= 3 && parts[2].length() > 0) {
      tree = syntaxTreeReader.parseFrom(parts[2]);
      posTags = tree.getAllSpannedPosTags();
    } else {
      posTags = Collections.nCopies(words.size(), ParametricCcgParser.DEFAULT_POS_TAG);
    }

    // Parse out a logical form, if one is provided.
    Expression logicalForm = null;
    if (parts.length >= 4 && parts[3].length() > 0) {
      logicalForm = ExpressionParser.lambdaCalculus().parseSingleExpression(parts[3]);
    }

    if (!ignoreSemantics) {
      return new CcgExample(SupertaggedSentence.createWithUnobservedSupertags(words, posTags),
          dependencies, tree, logicalForm);
    } else {
      return new CcgExample(SupertaggedSentence.createWithUnobservedSupertags(words, posTags),
          null, tree, logicalForm);
    }
  }
}
