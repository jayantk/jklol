package com.jayantkrish.jklol.cli;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import com.jayantkrish.jklol.ccg.CcgParse;
import com.jayantkrish.jklol.ccg.CcgParser;

/**
 * Parses input sentences using a trained CCG parser. 
 * 
 * @author jayant
 */
public class ParseCcg {
  
  public static void main(String[] args) {
    OptionParser parser = new OptionParser();
    // Required arguments.
    OptionSpec<String> model = parser.accepts("model").withRequiredArg().ofType(String.class).required();
    // Optional arguments
    OptionSpec<Integer> beamSize = parser.accepts("beamSize").withRequiredArg().ofType(Integer.class).defaultsTo(100);
    OptionSpec<Integer> numParses = parser.accepts("numParses").withRequiredArg().ofType(Integer.class).defaultsTo(1);
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
    
    List<String> sentenceToParse = options.nonOptionArguments();
    List<CcgParse> parses = ccgParser.beamSearch(sentenceToParse, options.valueOf(beamSize));
    for (int i = 0 ; i < Math.min(parses.size(), options.valueOf(numParses)); i++) {
      if (i > 0) {
        System.out.println("---");
      }
      System.out.println("HEAD: " + parses.get(i).getSemanticHeads());
      System.out.println("DEPS: " + parses.get(i).getAllDependencies());
      System.out.println("LEX: " + parses.get(i).getSpannedLexiconEntries());
      System.out.println("PROB: " + parses.get(i).getSubtreeProbability());
    }

    System.exit(0);
  }
}
