package com.jayantkrish.jklol.experiments.p3;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgCategory;
import com.jayantkrish.jklol.ccg.HeadedSyntacticCategory;
import com.jayantkrish.jklol.ccg.LexiconEntry;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.cli.AbstractCli;
import com.jayantkrish.jklol.util.CsvParser;
import com.jayantkrish.jklol.util.IoUtils;

public class FixLexicon extends AbstractCli {

  private OptionSpec<String> lexicon;
  
  private static final String[][] SYNTAX_HEADS = new String[][] {
        {"N", "N{0}"},
        {"N/N", "(N{0}/N{0}){1}"},
        {"N\\N", "(N{0}\\N{0}){1}"},
        {"N/P", "(N{0}/P{1}){0}"},
        {"N\\P", "(N{0}\\P{1}){0}"},
        {"A/N", "(A{0}/N{1}){0}"},
        {"P/N", "(P{0}/N{1}){0}"},
        {"S/N", "(S{0}/N{1}){0}"},
        {"P/P", "(P{0}/P{0}){1}"},
        {"A\\N", "(A{0}\\N{1}){0}"},
        {"P\\N", "(P{0}\\N{1}){0}"},
        {"P\\P", "(P{0}\\P{0}){1}"},
        {"(N\\N)/N", "((N{0}\\N{0}){1}/N{2}){1}"},
        {"(N\\N)/P", "((N{0}\\N{0}){1}/P{2}){1}"},
        {"(A\\A)/N", "((A{0}\\A{0}){1}/N{2}){1}"},
        {"(A\\A)/A", "((A{0}\\A{0}){1}/A{2}){1}"},
        {"(A\\N)/A", "((A{0}\\N{0}){1}/A{2}){1}"},
        {"(S\\N)/N", "((S{0}\\N{1}){0}/N{2}){0}"},
        {"(S\\N)/P", "((S{0}\\N{1}){0}/P{2}){0}"},
        {"(S\\P)/N", "((S{0}\\P{1}){0}/N{2}){0}"},
        {"(S\\N)/A", "((S{0}\\N{1}){0}/A{2}){0}"},
        {"(S\\A)/N", "((S{0}\\A{1}){0}/N{2}){0}"},
        {"(S/A)/N", "((S{0}/A{1}){0}/N{2}){0}"},
        {"(S/N)/N", "((S{0}/N{1}){0}/N{2}){0}"},
        {"(S/N)/P", "((S{0}/N{1}){0}/P{2}){0}"},
        {"(S/P)/N", "((S{0}/P{1}){0}/N{2}){0}"},
    };
  
  @Override
  public void initializeOptions(OptionParser parser) {
    lexicon = parser.accepts("lexicon").withRequiredArg().ofType(String.class).required();
  }

  @Override
  public void run(OptionSet options) {
    Map<SyntacticCategory, HeadedSyntacticCategory> syntaxMap = Maps.newHashMap();
    for (int i = 0; i < SYNTAX_HEADS.length; i++) {
      syntaxMap.put(SyntacticCategory.parseFrom(SYNTAX_HEADS[i][0]),
          HeadedSyntacticCategory.parseFrom(SYNTAX_HEADS[i][1]));
    }
    
    List<String> lines = IoUtils.readLines(options.valueOf(lexicon));
    CsvParser csv = CsvParser.noEscapeParser();
    ExpressionParser<Expression2> exp = ExpressionParser.expression2();

    for (String line : lines) {
      String[] parts = csv.parseLine(line);
      
      String[] words = parts[0].split(" ");
      String syntaxString = parts[1];
      String[] semantics = parts[2].split(" ");
      String predicate = semantics[0];
      
      int[] argSpec = new int[semantics.length - 1];
      for (int i = 0; i < argSpec.length; i++) {
        argSpec[i] = Integer.parseInt(semantics[i + 1]);
      }

      SyntacticCategory syntax = SyntacticCategory.parseFrom(syntaxString);
      HeadedSyntacticCategory headedSyntax = syntaxMap.get(syntax);
      
      Preconditions.checkState(headedSyntax != null, "No syntax mapping for %s", syntax);

      String[] lcWords = new String[words.length];
      for (int i = 0; i < words.length; i++) {
        lcWords[i] = words[i].toLowerCase();
      }
      
      Expression2 lf = null;
      if (predicate.equals("kb-ignore")) {
        if (syntax.isAtomic()) {
          lf = exp.parse("(lambda (x) #t)");
        } else {
          lf = exp.parse("(lambda (f) (lambda (x) (f x)))");
        }
      } else if (predicate.equals("kb-equal")) {
        if (syntax.getArgumentList().size() == 1) {
          lf = exp.parse("(lambda (f) (lambda (x) (f x)))");
        } else {
          lf = exp.parse("(lambda (f) (lambda (g) (lambda (x) (and:<t*,t> (f x) (g x)))))");
        }
      } else if (predicate.equals("kb-ignore-equal")) {
        lf = exp.parse("(lambda (f) (lambda (g) (lambda (x) (and:<t*,t> (f x) (g x)))))");
      } else if (predicate.endsWith("-rel")) {
        String typedPredicate = predicate +  ":<e,<e,t>>";

        if (Arrays.equals(argSpec, new int[] {0, 1})) {
          lf = exp.parse("(lambda (f) (lambda (x) (exists:<<e,t>,t> (lambda (y) (and:<t*,t> (f y) (" + typedPredicate + " x y))))))");
        } else if (Arrays.equals(argSpec, new int[] {1, 0})) {
          lf = exp.parse("(lambda (f) (lambda (y) (exists:<<e,t>,t> (lambda (x) (and:<t*,t> (f x) (" + typedPredicate + " x y))))))");
        } else if (Arrays.equals(argSpec, new int[] {0, 0, 1})) {
          lf = exp.parse("(lambda (f) (lambda (g) (lambda (x) (exists:<<e,t>,t> (lambda (y) (and:<t*,t> (f y) (g x) (" + typedPredicate +  " x y)))))))");
        } else if (Arrays.equals(argSpec, new int[] {0, 1, 0})) {
          lf = exp.parse("(lambda (f) (lambda (g) (lambda (x) (exists:<<e,t>,t> (lambda (y) (and:<t*,t> (g y) (f x) (" + typedPredicate +  " x y)))))))");
        }
      } else {
        String typedPredicate = predicate +  ":<e,t>";
        if (headedSyntax.isAtomic()) {
          lf = exp.parse(typedPredicate);
        } else {
          lf = exp.parse("(lambda (f) (lambda (x) (and:<t*,t> (f x) (" + typedPredicate +  " x))))");
        }
      }
      
      LexiconEntry entry = new LexiconEntry(Arrays.asList(lcWords), CcgCategory.fromSyntaxLf(headedSyntax, lf));
      System.out.println(entry.toCsvString());
    }
  }
  
  public static void main(String[] args) {
    new FixLexicon().run(args);
  }
}
