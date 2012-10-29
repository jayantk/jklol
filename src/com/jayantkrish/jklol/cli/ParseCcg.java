package com.jayantkrish.jklol.cli;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.CcgExample;
import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;
import com.jayantkrish.jklol.ccg.DependencyStructure;
import com.jayantkrish.jklol.util.IoUtils;

/**
 * Parses input sentences using a trained CCG parser. 
 * 
 * @author jayant
 */
public class ParseCcg {
  
  public static void printCcgParses(List<CcgParse> parses, int numParses) {
    for (int i = 0 ; i < Math.min(parses.size(), numParses); i++) {
      if (i > 0) {
        System.out.println("---");
      }
      System.out.println("HEAD: " + parses.get(i).getSemanticHeads());
      System.out.println("DEPS: " + parses.get(i).getAllDependencies());
      System.out.println("LEX: " + parses.get(i).getSpannedLexiconEntries());
      System.out.println("PROB: " + parses.get(i).getSubtreeProbability());
    }
  }
  
  public static void main(String[] args) {
    OptionParser parser = new OptionParser();
    // Required arguments.
    OptionSpec<String> model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    // Optional arguments
    OptionSpec<Integer> beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    OptionSpec<Integer> numParses = parser.accepts("numParses").withRequiredArg().ofType(Integer.class).defaultsTo(1);
    // If provided, running this program computes test error using the given file.
    // Otherwise, this program parses a string provided on the command line.
    // The format of testFile is the same as expected by TrainCcg to train a CCG parser.
    OptionSpec<String> testFile = parser.accepts("test").withRequiredArg().ofType(String.class);
    OptionSet options = parser.parse(args);

    // Read the parser.
    CcgParser ccgParser = null;
    FileInputStream fis = null;
    ObjectInputStream in = null;
    try {
      fis = new FileInputStream(options.valueOf(model));
      in = new ObjectInputStream(fis);
      ccgParser = (CcgParser)in.readObject();
      in.close();
    } catch(IOException ex) {
      ex.printStackTrace();
      System.exit(1);
    } catch(ClassNotFoundException ex) {
      ex.printStackTrace();
      System.exit(1);
    }
    
    if (options.has(testFile)) {
      List<CcgExample> testExamples = Lists.newArrayList();
      for (String line : IoUtils.readLines(options.valueOf(testFile))) {
        testExamples.add(CcgExample.parseFromString(line));
      }
      
      for (CcgExample example : testExamples) {
        int correct = 0;
        int falsePositive = 0;
        int falseNegative = 0;
        List<CcgParse> parses = ccgParser.beamSearch(example.getWords(), options.valueOf(beamSize));
        System.out.println("SENT: " + example.getWords());
        printCcgParses(parses, options.valueOf(numParses));
        
        if (parses.size() > 0) {
          Set<DependencyStructure> allDeps = Sets.newHashSet(parses.get(0).getAllDependencies());
          Set<DependencyStructure> trueDeps = example.getDependencies();
          
          Set<DependencyStructure> correctDeps = Sets.newHashSet(allDeps);
          correctDeps.retainAll(trueDeps);
          correct += correctDeps.size();
          falsePositive += allDeps.size() - correctDeps.size();
          falseNegative += trueDeps.size() - correctDeps.size();
        }
        
        System.out.println();
        double precision = ((double) correct) / (correct + falsePositive);
        double recall = ((double) correct) / (correct + falseNegative);
        System.out.println("Precision: " + precision);
        System.out.println("Recall: " + recall);
      }
    } else {
      // Parse a string from the command line.
      List<String> sentenceToParse = options.nonOptionArguments();
      List<CcgParse> parses = ccgParser.beamSearch(sentenceToParse, options.valueOf(beamSize));
      printCcgParses(parses, options.valueOf(numParses));
    }

    System.exit(0);
  }
}
