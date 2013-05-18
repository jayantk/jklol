package com.jayantkrish.jklol.cvsm.ccg;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.SyntacticCategory;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.ccg.lambda.ApplicationExpression;
import com.jayantkrish.jklol.ccg.lambda.ConstantExpression;
import com.jayantkrish.jklol.ccg.lambda.Expression;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.LambdaExpression;
import com.jayantkrish.jklol.cvsm.ccg.ConvertCncToCvsm.Span;
import com.jayantkrish.jklol.util.CsvParser;
import com.jayantkrish.jklol.util.Pair;

public class CcgLfReader {

  private final List<CategoryPattern> patterns;
  private final List<TypeChangePattern> typeChangePatterns;
  private final Expression conjProcedure;

  private static final Set<String> twoArgumentFunctions = Sets.newHashSet("fa", "ba", "rp", "lp", "bc", "fc", "gbx", "bx", "conj", "funny", "ltc", "rtc");
  private static final Set<String> oneArgumentFunctions = Sets.newHashSet("lex", "tr");

  public CcgLfReader(List<CategoryPattern> patterns,
      List<TypeChangePattern> typeChangePatterns, Expression conjProcedure) {
    this.patterns = ImmutableList.copyOf(patterns);
    this.typeChangePatterns = ImmutableList.copyOf(typeChangePatterns);
    this.conjProcedure = conjProcedure;
  }

  public static CcgLfReader parseFrom(Iterable<String> patternStrings) {
    List<CategoryPattern> patterns = Lists.newArrayList();
    List<TypeChangePattern> typeChangePatterns = Lists.newArrayList();
    Expression conjProcedure = null;
    for (String line : patternStrings) {
      if (line.startsWith("\"conj\"")) {
        Preconditions.checkState(conjProcedure == null);
        String[] parts = CsvParser.noEscapeParser().parseLine(line);
        conjProcedure = (new ExpressionParser()).parseSingleExpression(parts[1]);
      } if (line.startsWith("\"lex\"")) {
        typeChangePatterns.add(TypeChangePattern.parseFrom(line));
      } else {
        patterns.add(SemTypePattern.parseFrom(line));
      }
    }

    return new CcgLfReader(patterns, typeChangePatterns, conjProcedure);
  }

  /**
   * Finds the lowest parse tree node in {@code ccgExpression} that
   * spans {@code spanStart} (inclusive) to {@code spanEnd}
   * (exclusive). The parse tree node must have an atomic type.
   * 
   * @param ccgExpression
   * @param spanStart
   * @param spanEnd
   * @return
   */
  public Expression findSpanningExpression(Expression ccgExpression, int spanStart, int spanEnd) {
    List<Expression> spanningExpression = Lists.newArrayList();
    spanningExpression.add(null);
    findSpanningExpressionHelper(ccgExpression, spanStart, spanEnd, spanningExpression);
    return spanningExpression.get(0);
  }

  private static Pair<Integer, Integer> findSpanningExpressionHelper(Expression ccgExpression,
      int spanStart, int spanEnd, List<Expression> spanningExpression) {
    Preconditions.checkState(ccgExpression instanceof ApplicationExpression,
        "Illegal expression type: " + ccgExpression);
    ApplicationExpression app = (ApplicationExpression) ccgExpression;

    Pair<Integer, Integer> currentSpan = null;

    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    if (name.equals("lf")) {
      int wordInd = Integer.parseInt(((ConstantExpression) arguments.get(1)).getName()) - 1;
      currentSpan = Pair.of(wordInd, wordInd + 1);
    } else if (twoArgumentFunctions.contains(name)) {
      Pair<Integer, Integer> leftSpan = findSpanningExpressionHelper(arguments.get(lastArg - 1),
          spanStart, spanEnd, spanningExpression);
      Pair<Integer, Integer> rightSpan = findSpanningExpressionHelper(arguments.get(lastArg),
          spanStart, spanEnd, spanningExpression);
      currentSpan = Pair.of(leftSpan.getLeft(), rightSpan.getRight());
    } else if (oneArgumentFunctions.contains(name)) {
      currentSpan = findSpanningExpressionHelper(arguments.get(lastArg),
          spanStart, spanEnd, spanningExpression);
    } else {
      throw new IllegalArgumentException("Unknown function type: " + name);
    }
    SyntacticCategory syntax = getSyntacticCategory(ccgExpression);

    if (currentSpan.getLeft() <= spanStart && currentSpan.getRight() >= spanEnd &&
        spanningExpression.get(0) == null) { // && syntax.isAtomic()
      spanningExpression.set(0, ccgExpression);
    }
    return currentSpan;
  }

  public Pair<Integer, Integer> getExpressionSpan(Expression ccgExpression) {
    List<Expression> spanningExpression = Lists.newArrayList();
    spanningExpression.add(null);
    return findSpanningExpressionHelper(ccgExpression, -1, -1, spanningExpression);
  }

  public Expression pruneModifiers(Expression ccgExpression, List<Span> mentionSpans) {
    ApplicationExpression app = (ApplicationExpression) ccgExpression;
    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    if (name.equals("lf")) {
      return ccgExpression;
    } else if (oneArgumentFunctions.contains(name)) {
      List<Expression> newArguments = Lists.newArrayList(arguments);
      newArguments.set(lastArg, pruneModifiers(arguments.get(lastArg), mentionSpans));
      return new ApplicationExpression(app.getFunction(), newArguments);
    } else if (name.equals("fa") || name.equals("ba") || name.equals("fc") || name.equals("bc") 
	       || name.equals("bx") || name.equals("gbx")) {
      Expression left = arguments.get(lastArg - 1);
      Expression right = arguments.get(lastArg);

      Expression functionExpression = null;
      Expression argumentExpression = null;
      if (name.equals("fa") || name.equals("fc")) {
	functionExpression = left;
        argumentExpression = right;
      } else {
	functionExpression = right;
        argumentExpression = left;
      }

      SyntacticCategory function = getSyntacticCategory(functionExpression);
      SyntacticCategory argument = getSyntacticCategory(argumentExpression);
      Pair<Integer, Integer> functionSpan = getExpressionSpan(functionExpression);
      Pair<Integer, Integer> argumentSpan = getExpressionSpan(argumentExpression);

      if (!function.isAtomic() && (function.getArgument().isUnifiableWith(function.getReturn()) ||
          function.isUnifiableWith(SyntacticCategory.parseFrom("NP[1]/N")))) {
        boolean noMentionInSpan = true;
        for (Span mentionSpan : mentionSpans) {
          if (!(mentionSpan.getEnd() <= functionSpan.getLeft() || mentionSpan.getStart() >= functionSpan.getRight())) {
            noMentionInSpan = false;
          }
        }
        if (noMentionInSpan) {
          return pruneModifiers(argumentExpression, mentionSpans);
        }
      }

      boolean noMentionInArgumentSpan = true;
      for (Span mentionSpan : mentionSpans) {
	  if (!(mentionSpan.getEnd() <= argumentSpan.getLeft() || 
		mentionSpan.getStart() >= argumentSpan.getRight())) {
	      noMentionInArgumentSpan = false;
	  }
      }
      

      if (noMentionInArgumentSpan) {
	  String functionName = ((ConstantExpression) ((ApplicationExpression) functionExpression).getFunction()).getName();
	  if (functionName.equals("conj")) {
	      Expression returnValue = functionExpression;
	      String returnName = functionName;
	      do {
		  List<Expression> returnArguments = ((ApplicationExpression) returnValue).getArguments();
		  returnValue = returnArguments.get(4);
		  returnName = ((ConstantExpression) ((ApplicationExpression) returnValue).getFunction()).getName();
	      } while (returnName.equals("conj"));
	      return pruneModifiers(returnValue, mentionSpans);
	  } 
	  else if (argument.isAtomic() && function.getFinalReturnCategory().getValue().equals("S")) {
	      Expression eliminatedArgumentExpression = eliminateArgument(pruneModifiers(functionExpression, mentionSpans), new int[] {0});
	      if (eliminatedArgumentExpression != null) {
		  return pruneModifiers(eliminatedArgumentExpression, mentionSpans);
	      }
	  }
      }

    } else if (name.equals("lp")) {
      return pruneModifiers(arguments.get(lastArg), mentionSpans);
    } else if (name.equals("rp")) {
      return pruneModifiers(arguments.get(lastArg - 1), mentionSpans);
    }

    if (twoArgumentFunctions.contains(name)) {
      Expression left = arguments.get(lastArg - 1);
      Expression right = arguments.get(lastArg);

      List<Expression> newArguments = Lists.newArrayList(arguments);
      newArguments.set(lastArg - 1, pruneModifiers(left, mentionSpans));
      newArguments.set(lastArg, pruneModifiers(right, mentionSpans));
      return new ApplicationExpression(app.getFunction(), newArguments);
    } else {
      throw new IllegalArgumentException("Unknown function type: " + name);
    }
  }

    public SyntacticCategory eliminateArgument(SyntacticCategory category, int[] argIndexFromEnd) {
	SyntacticCategory returnCat = category.getFinalReturnCategory();
	List<SyntacticCategory> catArguments = category.getArgumentList();
	List<Direction> catDirections = category.getArgumentDirectionList();
	Preconditions.checkArgument(catArguments.size() > argIndexFromEnd[0]);

	int index = catArguments.size() - (1 + argIndexFromEnd[0]);
	if (argIndexFromEnd.length == 1) {
	    catArguments.remove(index);
	    catDirections.remove(index);
	} else {
	    catArguments.set(index, 
			     eliminateArgument(catArguments.get(index), Arrays.copyOfRange(argIndexFromEnd, 1, argIndexFromEnd.length)));
	}
	return SyntacticCategory.createFunctional(returnCat, catDirections, catArguments);
    }

    public Expression eliminateArgument(Expression ccgExpression, int[] argIndexFromEnd) {
	ApplicationExpression app = (ApplicationExpression) ccgExpression;
	String name = ((ConstantExpression) app.getFunction()).getName();
	List<Expression> arguments = app.getArguments();
	int lastArg = arguments.size() - 1;

	if (name.equals("lf")) {
	    SyntacticCategory input = getSyntacticCategory(ccgExpression);
	    SyntacticCategory newCategory = eliminateArgument(input, argIndexFromEnd);
	    // System.err.println("eliminateArgument " + Arrays.toString(argIndexFromEnd) + " " + input + " " + newCategory);

	    List<Expression> newArguments = Lists.newArrayList(arguments);
	    newArguments.set(lastArg, new ConstantExpression("\"" + newCategory + "\""));
	    return new ApplicationExpression(app.getFunction(), newArguments);
	} else if (oneArgumentFunctions.contains(name)) {
	    /*
	    List<Expression> newArguments = Lists.newArrayList(arguments);
	    newArguments.set(lastArg, eliminateArgument(arguments.get(lastArg), argIndexFromEnd));
	    return new ApplicationExpression(app.getFunction(), newArguments);
	    */
	    return null;
	} else if (name.equals("fa")) {
	    Expression left = arguments.get(lastArg - 1);
	    Expression right = arguments.get(lastArg);

	    List<Expression> newArguments = Lists.newArrayList(arguments);

	    SyntacticCategory functionCat = getSyntacticCategory(left).getWithoutFeatures();
	    if (functionCat.getArgument().isUnifiableWith(functionCat.getReturn()) && 
		!functionCat.getReturn().isAtomic() && argIndexFromEnd.length == 1) {
		// Modifier category. Need to eliminate an argument from both the argument
		// and return type.
		
		Expression result = eliminateArgument(left, new int[] {argIndexFromEnd[0] + 1});
		if (result == null) {return null;}
		result = eliminateArgument(result, new int[] {0, argIndexFromEnd[0]});
		newArguments.set(lastArg - 1, result);
		newArguments.set(lastArg, eliminateArgument(right, argIndexFromEnd));
	    }  else {
		int[] newArgs = Arrays.copyOf(argIndexFromEnd, argIndexFromEnd.length);
		newArgs[0] += 1;
		newArguments.set(lastArg - 1, eliminateArgument(left, newArgs));
	    }

	    for (Expression newArgument : newArguments) {
		if (newArgument == null) {
		    return null;
		}
	    }
	    return new ApplicationExpression(app.getFunction(), newArguments);
	} else if (name.equals("ba")) {
	    Expression left = arguments.get(lastArg - 1);
	    Expression right = arguments.get(lastArg);
	    List<Expression> newArguments = Lists.newArrayList(arguments);

	    SyntacticCategory functionCat = getSyntacticCategory(right).getWithoutFeatures();
	    if (functionCat.getArgument().isUnifiableWith(functionCat.getReturn()) && 
		!functionCat.getReturn().isAtomic()  && argIndexFromEnd.length == 1) {
		// Modifier category. Need to eliminate an argument from both the argument
		// and return type.
		
		Expression result = eliminateArgument(right, new int[] {argIndexFromEnd[0] + 1});
		if (result == null) {return null;}
		result = eliminateArgument(result, new int[] {0, argIndexFromEnd[0]});
		newArguments.set(lastArg, result);
		newArguments.set(lastArg - 1, eliminateArgument(left, argIndexFromEnd));
	    }  else {
		int[] newArgs = Arrays.copyOf(argIndexFromEnd, argIndexFromEnd.length);
		newArgs[0] += 1;
		newArguments.set(lastArg, eliminateArgument(right, newArgs));
	    }

	    for (Expression newArgument : newArguments) {
		if (newArgument == null) {
		    return null;
		}
	    }
	    return new ApplicationExpression(app.getFunction(), newArguments);
	} else if (twoArgumentFunctions.contains(name)) {
	    return null;
	} else {
	    throw new IllegalArgumentException("Unknown function type: " + name);
	}
    }

  public List<String> getWordsInCcgParse(Expression ccgParse, List<Expression> wordExpressions) {
    List<String> words = Lists.newArrayList();
    getWordsInCcgParseHelper(ccgParse, wordExpressions, words);
    return words;
  }

  public void getWordsInCcgParseHelper(Expression ccgExpression, List<Expression> wordExpressions, List<String> words) {
    ApplicationExpression app = (ApplicationExpression) ccgExpression;
    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    if (name.equals("lf")) {
      int wordInd = Integer.parseInt(((ConstantExpression) arguments.get(1)).getName()) - 1;
      ApplicationExpression wordExpression = (ApplicationExpression) wordExpressions.get(wordInd);
      words.add(((ConstantExpression) wordExpression.getArguments().get(2)).getName().replaceAll("^\"(.*)\"", "$1"));
    } else if (oneArgumentFunctions.contains(name)) {
      getWordsInCcgParseHelper(arguments.get(lastArg), wordExpressions, words);
    } else if (twoArgumentFunctions.contains(name)) {
      getWordsInCcgParseHelper(arguments.get(lastArg - 1), wordExpressions, words);
      getWordsInCcgParseHelper(arguments.get(lastArg), wordExpressions, words);
    }
  }

    public Expression buildNpExpression(List<String> words) {
	Preconditions.checkArgument(words.size() > 0);
	SyntacticCategory noun = SyntacticCategory.parseFrom("N");
	SyntacticCategory adj = SyntacticCategory.parseFrom("N/N");

	Expression result = getExpressionForWord(words.get(words.size() - 1), noun);
	for (int i = words.size() - 2; i >= 0; i++) {
	    Expression adjExpr = getExpressionForWord(words.get(i), adj);
	    result = new ApplicationExpression(adjExpr, Arrays.asList(result));
	}
	return result.simplify();
    }

  public static SyntacticCategory getSyntacticCategory(Expression ccgExpression) {
    Preconditions.checkState(ccgExpression instanceof ApplicationExpression,
        "Illegal expression type: " + ccgExpression);
    ApplicationExpression app = (ApplicationExpression) ccgExpression;
    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    String syntaxString = null;
    if (name.equals("lf")) {
      syntaxString = ((ConstantExpression) arguments.get(lastArg)).getName();
    } else if (twoArgumentFunctions.contains(name)) {
      syntaxString = ((ConstantExpression) arguments.get(lastArg - 2)).getName();
    } else if (oneArgumentFunctions.contains(name)) {
      syntaxString = ((ConstantExpression) arguments.get(lastArg - 1)).getName();
    } else {
      throw new IllegalArgumentException("Unknown function type: " + name);
    }

    syntaxString = syntaxString.replaceAll("^\"(.*)\"$", "$1");
    return SyntacticCategory.parseFrom(syntaxString);
  }

  public List<Expression> findAtomicSubexpressions(Expression ccgExpression) {
    List<Expression> subexpressions = Lists.newArrayList();
    findAtomicSubexpressionsHelper(ccgExpression, subexpressions);
    return subexpressions;
  }

  private void findAtomicSubexpressionsHelper(Expression ccgExpression, List<Expression> accumulator) {
    SyntacticCategory syntax = getSyntacticCategory(ccgExpression);
    if (syntax.isAtomic()) {
      accumulator.add(ccgExpression);
    }

    ApplicationExpression app = (ApplicationExpression) ccgExpression;
    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    int lastArg = arguments.size() - 1;
    if (name.equals("lf")) {
      // Base case: do nothing.
    } else if (twoArgumentFunctions.contains(name)) {
      findAtomicSubexpressionsHelper(arguments.get(lastArg - 1), accumulator);
      findAtomicSubexpressionsHelper(arguments.get(lastArg), accumulator);
    } else if (oneArgumentFunctions.contains(name)) {
      findAtomicSubexpressionsHelper(arguments.get(lastArg), accumulator);
    } else {
      throw new IllegalArgumentException("Unknown function type: " + name);
    }
  }

  public Expression parse(Expression ccgExpression, List<Expression> words) {
    return recursivelyTransformCcgParse(ccgExpression, words);
  }

    private Expression getExpressionForWord(List<Expression> wordExpressions, int wordIndex, SyntacticCategory syntax) {
	ApplicationExpression app = (ApplicationExpression) wordExpressions.get(wordIndex);
      String functionName = ((ConstantExpression) app.getFunction()).getName();
      Preconditions.checkArgument(functionName.equals("w"));
      int wordNum = Integer.parseInt(((ConstantExpression) app.getArguments().get(1)).getName());
      String word = ((ConstantExpression) app.getArguments().get(2)).getName().toLowerCase().replaceAll("\"", "");

      return getExpressionForWord(word, syntax);
    }

    private Expression getExpressionForWord(String word, SyntacticCategory syntax) {
	for (CategoryPattern pattern : patterns) {
	    if (pattern.matches(Arrays.<String> asList(word), syntax)) {
		return pattern.getLogicalForm(Arrays.<String> asList(word), syntax);
	    }
	}
	return null;
    }

  private Expression recursivelyTransformCcgParse(Expression ccgExpression,
						  List<Expression> words) {
    Preconditions.checkState(ccgExpression instanceof ApplicationExpression,
        "Illegal expression type: " + ccgExpression);
    ApplicationExpression app = (ApplicationExpression) ccgExpression;

    String name = ((ConstantExpression) app.getFunction()).getName();
    List<Expression> arguments = app.getArguments();
    if (name.equals("lf")) {
      int wordInd = Integer.parseInt(((ConstantExpression) arguments.get(1)).getName());
      Expression wordExpression = getExpressionForWord(words, wordInd - 1, getSyntacticCategory(ccgExpression));
      if (wordExpression == null) {
	  throw new LogicalFormConversionError("No lexicon template for word: " + words.get(wordInd - 1) + " " + getSyntacticCategory(ccgExpression));
      }
      return wordExpression;
    } else if (name.equals("lex")) {
      // Lex is a type-changing rule. Only rules which maintain the
      // same semantic type specification are supported.
      SyntacticCategory origCategory = SyntacticCategory.parseFrom(((ConstantExpression) arguments.get(0)).getName().replaceAll("\"", ""));
      SyntacticCategory newCategory = SyntacticCategory.parseFrom(((ConstantExpression) arguments.get(1)).getName().replaceAll("\"", ""));
      Expression origExpression = recursivelyTransformCcgParse(arguments.get(2), words);
      if (SemTypePattern.hasSameSemanticType(origCategory, newCategory, false)) {
        return origExpression;
      } else {
        for (TypeChangePattern pattern : typeChangePatterns) {
          if (pattern.matches(origCategory, newCategory)) {
            return new ApplicationExpression(pattern.getLogicalForm(), Arrays.asList(origExpression));
          }
        }
      }
      throw new LogicalFormConversionError("Used type changing rule: " + origCategory + " to " + newCategory);
    } else if (name.equals("tr")) {
	Expression origExpression = recursivelyTransformCcgParse(arguments.get(1), words);
	SyntacticCategory origCategory = getSyntacticCategory(arguments.get(1));
	SyntacticCategory myCategory = getSyntacticCategory(ccgExpression);

        for (TypeChangePattern pattern : typeChangePatterns) {
	    if (pattern.matches(origCategory, myCategory)) {
		return new ApplicationExpression(pattern.getLogicalForm(), Arrays.asList(origExpression));
	    }
        }

	throw new LogicalFormConversionError("Used type raising rule: " + origCategory + " to " + myCategory);
    } else if (name.equals("fa")) {
      Expression left = recursivelyTransformCcgParse(arguments.get(1), words);
      Expression right = recursivelyTransformCcgParse(arguments.get(2), words);
      return new ApplicationExpression(left, Arrays.asList(right));
    } else if (name.equals("ba")) {
      Expression left = recursivelyTransformCcgParse(arguments.get(1), words);
      Expression right = recursivelyTransformCcgParse(arguments.get(2), words);
      return new ApplicationExpression(right, Arrays.asList(left));
    } else if (name.equals("rp")) {
      Preconditions.checkState(arguments.size() == 3, "rp arguments: " + arguments);
      return recursivelyTransformCcgParse(arguments.get(1), words);
    } else if (name.equals("lp")) {
      Preconditions.checkState(arguments.size() == 3);
      return recursivelyTransformCcgParse(arguments.get(2), words);
    } else if (name.equals("bx") || name.equals("bc") || name.equals("gbx")) {
      Expression left = recursivelyTransformCcgParse(arguments.get(1), words);
      Expression right = recursivelyTransformCcgParse(arguments.get(2), words);

      SyntacticCategory function = getSyntacticCategory(arguments.get(2));
      SyntacticCategory argument = getSyntacticCategory(arguments.get(1));

      int depth = getCompositionDepth(function, argument);
      return buildCompositionExpression(right, left, depth);
    } else if (name.equals("fc")) {
      Expression left = recursivelyTransformCcgParse(arguments.get(1), words);
      Expression right = recursivelyTransformCcgParse(arguments.get(2), words);

      SyntacticCategory function = getSyntacticCategory(arguments.get(1));
      SyntacticCategory argument = getSyntacticCategory(arguments.get(2));

      int depth = getCompositionDepth(function, argument);
      return buildCompositionExpression(left, right, depth);
    } else if (name.equals("conj") && conjProcedure != null) {
      Expression nonConjArgument = recursivelyTransformCcgParse(arguments.get(4), words).simplify();

      SyntacticCategory mySyntax = getSyntacticCategory(ccgExpression);
      SyntacticCategory childSyntax = getSyntacticCategory(arguments.get(4));
      if (mySyntax.isUnifiableWith(childSyntax)) {
        // Sometimes the conj category just absorbs punctuation...
        return nonConjArgument;
      }

      // Expression conjArgument = recursivelyTransformCcgParse(arguments.get(3), words);

      List<ConstantExpression> newVars = Lists.newArrayList();
      ConstantExpression remainingArgumentName = ConstantExpression.generateUniqueVariable();
      Expression remainingArgument = remainingArgumentName;
      Expression appliedArgument = nonConjArgument;
      if (nonConjArgument instanceof LambdaExpression) {
        LambdaExpression nonConjLambda = (LambdaExpression) nonConjArgument.simplify();
        newVars = ConstantExpression.generateUniqueVariables(nonConjLambda.getArguments().size());
        appliedArgument = new ApplicationExpression(nonConjLambda, newVars);
        remainingArgument = new ApplicationExpression(remainingArgumentName, newVars);
      }
      Expression result = conjProcedure.substitute(new ConstantExpression("t1:<right>"), appliedArgument);
      result = result.substitute(new ConstantExpression("t1:<left>"), remainingArgument);

      if (newVars.size() > 0) {
        result = new LambdaExpression(newVars, result);
      }
      result = new LambdaExpression(Arrays.asList(remainingArgumentName), result);

      return result;
    } else if (name.equals("funny")) {
      // funny is the type changing rule that combines a conj with a noun 
      // to produce a noun.
      return recursivelyTransformCcgParse(arguments.get(2), words);
    }

    throw new LogicalFormConversionError("Unknown function type: " + name);
  }

  private Expression buildCompositionExpression(Expression functionLogicalForm,
      Expression argumentLogicalForm, int numArgsToKeep) {
    // Composition.
    LambdaExpression functionAsLambda = (LambdaExpression) (functionLogicalForm.simplify());
    LambdaExpression argumentAsLambda = (LambdaExpression) (argumentLogicalForm.simplify());
    // System.out.println("function: " + functionAsLambda);
    // System.out.println("argument: " + argumentAsLambda);

    List<ConstantExpression> remainingArgs = argumentAsLambda.getArguments().subList(0, numArgsToKeep);
    List<ConstantExpression> remainingArgsRenamed = ConstantExpression.generateUniqueVariables(remainingArgs.size());

    List<Expression> functionArguments = Lists.newArrayList();
    functionArguments.add(new ApplicationExpression(argumentAsLambda, remainingArgsRenamed));
    List<ConstantExpression> newFunctionArgs = ConstantExpression.generateUniqueVariables(functionAsLambda.getArguments().size() - 1);
    functionArguments.addAll(newFunctionArgs);

    Expression result = new ApplicationExpression(functionAsLambda, functionArguments);
    if (newFunctionArgs.size() > 0) {
      result = new LambdaExpression(newFunctionArgs, result);
    }
    result = new LambdaExpression(remainingArgsRenamed, result).simplify();
    // System.out.println("result: " + result);
    return result;
  }

  private int getCompositionDepth(SyntacticCategory function, SyntacticCategory argument) {
      function = function.getWithoutFeatures();
      argument = argument.getWithoutFeatures();
    SyntacticCategory functionArgument = function.getArgument();
    // Depth is the number of arguments of argument which must be passed in before
    // function can be applied to argument.
    int depth = 0;
    while (!functionArgument.isUnifiableWith(argument)) {
      depth++;
      argument = argument.getReturn();
    }
    return depth;
  }

  public class LogicalFormConversionError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LogicalFormConversionError(String cause) {
      super(cause);
    }
  }
}
