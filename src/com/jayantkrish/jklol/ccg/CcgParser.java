package com.jayantkrish.jklol.ccg;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.jayantkrish.jklol.ccg.CcgCategory.Argument;
import com.jayantkrish.jklol.ccg.CcgChart.ChartEntry;
import com.jayantkrish.jklol.ccg.CcgChart.IndexedPredicate;
import com.jayantkrish.jklol.ccg.SyntacticCategory.Direction;
import com.jayantkrish.jklol.models.DiscreteFactor;
import com.jayantkrish.jklol.models.DiscreteFactor.Outcome;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Variable;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.tensor.SparseTensor;
import com.jayantkrish.jklol.tensor.Tensor;
import com.jayantkrish.jklol.training.LogFunction;
import com.jayantkrish.jklol.training.NullLogFunction;
import com.jayantkrish.jklol.util.Assignment;

/**
 * A chart parser for Combinatory Categorial Grammar (CCG).
 * 
 * @author jayantk
 */
public class CcgParser implements Serializable {

  private static final long serialVersionUID = 1L;
  
  // Parameters for encoding (filled and unfilled) dependency structures
  // in longs. These are the size of each field, in bits.
  private static final int PREDICATE_BITS = 20;
  private static final long PREDICATE_MASK = ~(-1L << PREDICATE_BITS);
  private static final int ARG_NUM_BITS = 4;
  private static final long ARG_NUM_MASK = ~(-1L << ARG_NUM_BITS);
  private static final int WORD_IND_BITS = 8;
  private static final long WORD_IND_MASK = ~(-1L << WORD_IND_BITS);
  // The largest possible argument number.
  private static final int MAX_ARG_NUM = 2 << ARG_NUM_BITS;
  // These are the locations of each field within the number. The layout
  // within the number is:
  // | sbj word ind | obj word ind | arg num | subj word | obj word |
  // 63                                                             0
  private static final int OBJECT_OFFSET = 0;
  private static final int ARG_NUM_OFFSET = OBJECT_OFFSET + PREDICATE_BITS;
  private static final int SUBJECT_OFFSET = ARG_NUM_OFFSET + ARG_NUM_BITS;
  private static final int OBJECT_WORD_IND_OFFSET = SUBJECT_OFFSET + PREDICATE_BITS;
  private static final int SUBJECT_WORD_IND_OFFSET = OBJECT_WORD_IND_OFFSET + WORD_IND_BITS;

  // Member variables ////////////////////////////////////
  
  private final VariableNumMap terminalVar;
  private final VariableNumMap ccgCategoryVar;
  private final DiscreteFactor terminalDistribution;
  
  // Pull out the weights and variable types from the dependency 
  // structure distribution for efficiency.
  private final DiscreteVariable dependencyHeadType; // type of head and argument.
  private final DiscreteVariable dependencyArgNumType;
  private final Tensor dependencyTensor;
  
  public CcgParser(VariableNumMap terminalVar, VariableNumMap ccgCategoryVar,
      DiscreteFactor terminalDistribution, VariableNumMap dependencyHeadVar, VariableNumMap dependencyArgNumVar,
      VariableNumMap dependencyArgVar, DiscreteFactor dependencyDistribution) {
    this.terminalVar = Preconditions.checkNotNull(terminalVar);
    this.ccgCategoryVar = Preconditions.checkNotNull(ccgCategoryVar);
    this.terminalDistribution = Preconditions.checkNotNull(terminalDistribution);

    Preconditions.checkArgument(dependencyDistribution.getVars().equals(
        VariableNumMap.unionAll(dependencyHeadVar, dependencyArgNumVar, dependencyArgVar)));
    Preconditions.checkArgument(dependencyHeadVar.getOnlyVariableNum() < dependencyArgNumVar.getOnlyVariableNum());
    Preconditions.checkArgument(dependencyArgNumVar.getOnlyVariableNum() < dependencyArgVar.getOnlyVariableNum());
    this.dependencyHeadType = dependencyHeadVar.getDiscreteVariables().get(0);
    this.dependencyArgNumType = dependencyArgNumVar.getDiscreteVariables().get(0);
    DiscreteVariable dependencyArgType = dependencyArgVar.getDiscreteVariables().get(0);
    Preconditions.checkArgument(dependencyHeadType.equals(dependencyArgType));
    this.dependencyTensor = dependencyDistribution.getWeights();
  }

  public List<CcgParse> beamSearch(List<String> terminals, int beamSize) {
    return beamSearch(terminals, beamSize, new NullLogFunction());
  }

  /**
   * Performs a beam search to find the best CCG parses of {@code terminals}.
   * Note that this is an approximate inference strategy, and the returned
   * parses may not be the best parses if at any point during the search
   * more than {@code beamSize} parse trees exist for a span of the sentence.
   * 
   * @param terminals
   * @param beamSize
   * @param log
   * @return {@code beamSize} best parses for {@code terminals}.
   */
  public List<CcgParse> beamSearch(List<String> terminals, int beamSize, LogFunction log) {
    CcgChart chart = new CcgChart(terminals, beamSize);

    log.startTimer("ccg_parse/initialize_chart");
    initializeChart(terminals, chart);
    log.stopTimer("ccg_parse/initialize_chart");
    
    // Construct a tree from the nonterminals.
    log.startTimer("ccg_parse/calculate_inside_beam");
    int chartSize = chart.size();
    for (int spanSize = 1; spanSize < chartSize; spanSize++) {
      for (int spanStart = 0; spanStart + spanSize < chartSize; spanStart++) {
        int spanEnd = spanStart + spanSize;
        calculateInsideBeam(spanStart, spanEnd, chart, log);
      }
    }
    log.stopTimer("ccg_parse/calculate_inside_beam");

    int numParses = Math.min(beamSize, chart.getNumChartEntriesForSpan(0, chart.size() - 1));

    return chart.decodeBestParsesForSpan(0, chart.size() - 1, numParses, this);
  }
  
  /**
   * Initializes the parse chart with entries from the CCG lexicon for {@code terminals}.
   * 
   * @param terminals
   * @param chart
   */
  private void initializeChart(List<String> terminals, CcgChart chart) {
    Variable terminalListValue = Iterables.getOnlyElement(terminalVar.getVariables());

    // Identify all possible assignments to the dependency head and argument
    // variables, so that we can look up probabilities in a sparser tensor.
    Set<Long> possiblePredicates = Sets.newHashSet();
    
    int ccgCategoryVarNum = ccgCategoryVar.getOnlyVariableNum();
    for (int i = 0; i < terminals.size(); i++) {
      for (int j = i; j < terminals.size(); j++) {
        if (terminalListValue.canTakeValue(terminals.subList(i, j + 1))) {
          Assignment assignment = terminalVar.outcomeArrayToAssignment(terminals.subList(i, j + 1));
          Iterator<Outcome> iterator = terminalDistribution.outcomePrefixIterator(assignment);
          while (iterator.hasNext()) {
            Outcome bestOutcome = iterator.next();
            CcgCategory category = (CcgCategory) bestOutcome.getAssignment().getValue(ccgCategoryVarNum);

            ChartEntry entry = ccgCategoryToChartEntry(category, i, j);

            for (int headWordNum : entry.getHeadWordNums()) {
              possiblePredicates.add((long) headWordNum);
            }
            
            for (long dep : entry.getUnfilledDependencies()) {
              int subject = getSubjectPredicateFromDep(dep);
              int object = getObjectPredicateFromDep(dep);
              
              if (subject != -1) {
                possiblePredicates.add((long) subject);
              }
              
              if (object != -1) {
                possiblePredicates.add((long) object);
              }
            }

            chart.addChartEntryForSpan(entry, bestOutcome.getProbability(), i, j);
          }
        }
      }
    }

    // Sparsify the dependency tensor for faster parsing.
    long[] keyNums = Longs.toArray(possiblePredicates);
    double[] values = new double[keyNums.length];
    Arrays.fill(values, 1.0);
    
    int headVarNum = dependencyTensor.getDimensionNumbers()[0];
    int argVarNum = dependencyTensor.getDimensionNumbers()[2];
    int predVarSize = dependencyTensor.getDimensionSizes()[0];

    SparseTensor keyIndicator = SparseTensor.fromUnorderedKeyValues(new int[] {headVarNum},
        new int[] {predVarSize}, keyNums, values);
    
    Tensor smallDependencyTensor = dependencyTensor.retainKeys(keyIndicator)
        .retainKeys(keyIndicator.relabelDimensions(new int[] {argVarNum}));
    
    chart.setDependencyTensor(smallDependencyTensor);
  }
  
  private ChartEntry ccgCategoryToChartEntry(CcgCategory result, int spanStart, int spanEnd) {
    // Assign each predicate in this category a unique word index.
    List<Integer> headWordNums = Lists.newArrayList();
    List<Integer> headIndexes = Lists.newArrayList();
    List<Integer> headArgumentNumbers = Lists.newArrayList();
    for (Argument head : result.getHeads()) {
      if (head.hasPredicate()) {
        headWordNums.add(dependencyHeadType.getValueIndex(head.getPredicate()));
        headIndexes.add(spanEnd);
      } else {
        headArgumentNumbers.add(head.getArgumentNumber());
      }
    }

    List<UnfilledDependency> deps = Lists.newArrayList();
    List<UnfilledDependency> unfilledDeps = result.createUnfilledDependencies(spanEnd, deps);
    
    long[] unfilledDepArray = unfilledDependencyArrayToLongArray(unfilledDeps);
    long[] depArray = unfilledDependencyArrayToLongArray(deps);

    return new ChartEntry(result, Ints.toArray(headWordNums), Ints.toArray(headIndexes), 
        Ints.toArray(headArgumentNumbers), unfilledDepArray, depArray, spanStart, spanEnd);
  }

  private void calculateInsideBeam(int spanStart, int spanEnd, CcgChart chart, LogFunction log) {
    for (int i = 0; i < spanEnd - spanStart; i++) {
      // Index j is for forward compatibility for skipping terminal symbols.
      for (int j = i + 1; j < i + 2; j++) {
        ChartEntry[] leftTrees = chart.getChartEntriesForSpan(spanStart, spanStart + i);
        double[] leftProbs = chart.getChartEntryProbsForSpan(spanStart, spanStart + i);
        int numLeftTrees = chart.getNumChartEntriesForSpan(spanStart, spanStart + i);
        Multimap<SyntacticCategory, Integer> leftTypes = aggregateBySyntacticType(leftTrees, numLeftTrees);
        Multimap<SyntacticCategory, Integer> leftArguments = aggregateByArgumentType(leftTrees, numLeftTrees, Direction.RIGHT);

        ChartEntry[] rightTrees = chart.getChartEntriesForSpan(spanStart + j, spanEnd);
        double[] rightProbs = chart.getChartEntryProbsForSpan(spanStart + j, spanEnd);
        int numRightTrees = chart.getNumChartEntriesForSpan(spanStart + j, spanEnd);
        Multimap<SyntacticCategory, Integer> rightTypes = aggregateBySyntacticType(rightTrees, numRightTrees);
        Multimap<SyntacticCategory, Integer> rightArguments = aggregateByArgumentType(rightTrees, numRightTrees, Direction.LEFT);
        
        long[] filledDeps = new long[20];
        long[] newUnfilledDependencies = new long[20];

        // Do CCG right application. (The category on the left is a function.)
        // log.startTimer("ccg_parse/calculate_inside_beam/apply");
        for (SyntacticCategory leftArgument : leftArguments.keySet()) {
          for (SyntacticCategory rightType : rightTypes.keySet()) {
            if (leftArgument.isUnifiableWith(rightType)) {
              for (Integer leftIndex : leftArguments.get(leftArgument)) {
                ChartEntry leftRoot = leftTrees[leftIndex];
                double leftProb = leftProbs[leftIndex];
                for (Integer rightIndex : rightTypes.get(rightType)) {
                  ChartEntry rightRoot = rightTrees[rightIndex];
                  double rightProb = rightProbs[rightIndex];

                  ChartEntry result = apply(leftRoot, rightRoot, Direction.RIGHT, spanStart, 
                      spanStart + i, leftIndex, spanStart + j, spanEnd, rightIndex, filledDeps,
                      newUnfilledDependencies);
                  if (result != null) {
                    addChartEntry(result, chart, leftProb * rightProb, spanStart, spanEnd);
                  }
                }
              }
            }
          }
        }

        // Do CCG left application. (The category on the right is a function.)
        for (SyntacticCategory rightArgument : rightArguments.keySet()) {
          for (SyntacticCategory leftType : leftTypes.keySet()) {
            if (rightArgument.isUnifiableWith(leftType)) {
              for (Integer leftIndex : leftTypes.get(leftType)) {
                ChartEntry leftRoot = leftTrees[leftIndex];
                double leftProb = leftProbs[leftIndex];
                for (Integer rightIndex : rightArguments.get(rightArgument)) {
                  ChartEntry rightRoot = rightTrees[rightIndex];
                  double rightProb = rightProbs[rightIndex];

                  ChartEntry result = apply(rightRoot, leftRoot, Direction.LEFT, spanStart,
                      spanStart + i, leftIndex, spanStart + j, spanEnd, rightIndex, filledDeps,
                      newUnfilledDependencies);
                  if (result != null) {
                    addChartEntry(result, chart, leftProb * rightProb, spanStart, spanEnd);
                  }
                }
              }
            }
          }
        }
        
        for (SyntacticCategory rightType : rightTypes.keySet()) {
          for (SyntacticCategory leftType : leftTypes.keySet()) {
            
          }
        }
        // log.stopTimer("ccg_parse/calculate_inside_beam/apply");
      }
    }
  }

  private Multimap<SyntacticCategory, Integer> aggregateBySyntacticType(
      ChartEntry[] entries, int numEntries) {
    Multimap<SyntacticCategory, Integer> map = HashMultimap.create();
    for (int i = 0; i < numEntries; i++) {
      map.put(entries[i].getSyntax(), i);
    }
    return map;
  }

  /**
   * Identifies all elements of {@code entries} that accept an argument on
   * {@code direction}, and returns a map from the argument type to the 
   * indexes of chart entries that accept that type. 
   *   
   * @param entries
   * @param numEntries
   * @param direction
   * @return
   */
  private Multimap<SyntacticCategory, Integer> aggregateByArgumentType(
      ChartEntry[] entries, int numEntries, Direction direction) {
    Multimap<SyntacticCategory, Integer> map = HashMultimap.create();
    for (int i = 0; i < numEntries; i++) {
      SyntacticCategory syntax = entries[i].getSyntax();
      if (!syntax.isAtomic() && syntax.acceptsArgumentOn(direction)) {
        map.put(syntax.getArgument(), i);
      }
    }
    return map;
  }
  /**
   * Calculates the probability of any new dependencies in {@code result}, then inserts it
   * into {@code chart}.
   * @param result
   * @param chart
   * @param leftRightProb
   * @param spanStart
   * @param spanEnd
   */
  private void addChartEntry(ChartEntry result, CcgChart chart, double leftRightProb, 
      int spanStart, int spanEnd) {
    // Get the probabilities of the generated dependencies.
    double depProb = 1.0;
    Tensor currentParseTensor = chart.getDependencyTensor();
    for (long dep : result.getDependencies()) {
      long depNum = dependencyLongToTensorKeyNum(dep);
      depProb *= currentParseTensor.get(depNum);
    }

    chart.addChartEntryForSpan(result, leftRightProb * depProb, spanStart, spanEnd); 
    // System.out.println(rootSpanStart + "." + rootSpanEnd + " " + result.getCategory() + " " + result.getDependencies() + " " + (depProb * leftRightProb)); 
  }
  
  private ChartEntry apply(ChartEntry first, ChartEntry other, Direction direction,
      int leftSpanStart, int leftSpanEnd, int leftIndex, int rightSpanStart, int rightSpanEnd,
      int rightIndex, long[] filledDeps, long[] newUnfilledDependencies) {
    SyntacticCategory syntax = first.getSyntax();

    int[] newHeadNums = (syntax.getHead() == SyntacticCategory.HeadValue.ARGUMENT) 
        ? other.getHeadWordNums() : first.getHeadWordNums();
    int[] newHeadIndexes = (syntax.getHead() == SyntacticCategory.HeadValue.ARGUMENT) 
        ? other.getHeadIndexes() : first.getHeadIndexes();
                        
    int[] newHeadArgumentNums = (syntax.getHead() == SyntacticCategory.HeadValue.ARGUMENT) 
        ? other.getUnfilledHeads() : first.getUnfilledHeads(); 

    // Resolve semantic dependencies. Fill all dependency slots which require this argument.
    // Return any fully-filled dependencies, while saving partially-filled dependencies for later.
    int argNum = syntax.getArgumentList().size(); 
    long[] unfilledDependencies = first.getUnfilledDependencies();
    long[] otherUnfilledDeps = other.getUnfilledDependencies();

    int filledDepsSize = 0;
    int newUnfilledDepsSize = 0;
    for (int i = 0 ; i < unfilledDependencies.length; i++) {
      long unfilled = unfilledDependencies[i];
      // Check if the argument currently being filled matches the argument
      // expected by this dependency.
      if (getObjectArgNumFromDep(unfilled) == argNum) {
        int[] objectHeadNums = other.getHeadWordNums();
        int[] objectHeadIndexes = other.getHeadIndexes();
        if (getSubjectArgNumFromDep(unfilled) == -1) {
          for (int j = 0; j < objectHeadNums.length; j++) {
            // Create a new filled dependency by substituting in the current object.
            long filledDep = unfilled - (argNum << OBJECT_OFFSET);
            filledDep += ((long) objectHeadNums[j] + MAX_ARG_NUM) << OBJECT_OFFSET;
            filledDep += ((long) objectHeadIndexes[j]) << OBJECT_WORD_IND_OFFSET;
            
            filledDeps[filledDepsSize] = filledDep;
            filledDepsSize++;
          }
        } else {
          // The subject of this dependency has not been filled.
          int subjectIndex = getSubjectArgNumFromDep(unfilled);
          int argIndex = getArgNumFromDep(unfilled);
          
          for (int j = 0; j < objectHeadNums.length; j++) {
            newUnfilledDependencies[newUnfilledDepsSize] = marshalUnfilledDependency(
                objectHeadNums[j] + MAX_ARG_NUM, argIndex, subjectIndex, objectHeadIndexes[j], 0);
            newUnfilledDepsSize++;
          }
        }
      } else if (getSubjectArgNumFromDep(unfilled) == argNum) {
        UnfilledDependency unfilledAsDep = longToUnfilledDependency(unfilled);
        int otherArgNum = unfilledAsDep.getArgumentIndex();
        
        if (unfilledAsDep.hasObject()) {
          IndexedPredicate object = unfilledAsDep.getObject();
          for (long otherDep : otherUnfilledDeps) {
            UnfilledDependency otherDepAsDep = longToUnfilledDependency(otherDep);
            if (otherDepAsDep.getObjectIndex() == otherArgNum || otherDepAsDep.getSubjectIndex() == otherArgNum) {
              filledDepsSize += substituteDependencyVariable(otherArgNum, otherDepAsDep, object, filledDeps, filledDepsSize);
            }
          }
        } else {
          // Part of the dependency remains unresolved. Fill what's possible, then propagate
          // the unfilled portions.
          int replacementIndex = unfilledAsDep.getObjectIndex();

          for (long otherDep : otherUnfilledDeps) {
            UnfilledDependency otherDepAsDep = longToUnfilledDependency(otherDep);
            if (otherDepAsDep.getObjectIndex() == otherArgNum || otherDepAsDep.getSubjectIndex() == otherArgNum) {
              newUnfilledDepsSize += substituteDependencyVariable(otherArgNum, otherDepAsDep, replacementIndex, 
                  newUnfilledDependencies, newUnfilledDepsSize);
            }
          }
        }

      } else {
        newUnfilledDependencies[newUnfilledDepsSize] = unfilled;
        newUnfilledDepsSize++;
      }
    }
    
    if (syntax.getHead() == SyntacticCategory.HeadValue.ARGUMENT) {
      long[] otherUnfilledDepArray = other.getUnfilledDependencies();
      for (int i = 0; i < otherUnfilledDepArray.length; i++) {
        newUnfilledDependencies[newUnfilledDepsSize] = otherUnfilledDepArray[i];
        newUnfilledDepsSize++;
      }
    }
    
    // Handle any unfilled head arguments.
    if (Ints.contains(newHeadArgumentNums, argNum)) {
      int[] otherHeadNums = other.getHeadWordNums();
      int[] otherHeadIndexes = other.getHeadIndexes();
      
      newHeadNums = Ints.concat(newHeadNums, otherHeadNums);
      newHeadIndexes = Ints.concat(newHeadIndexes, otherHeadIndexes);
    }
    
    long[] filledDepArray = Arrays.copyOf(filledDeps, filledDepsSize);
    long[] unfilledDepArray = Arrays.copyOf(newUnfilledDependencies, newUnfilledDepsSize);
    
    return new ChartEntry(syntax.getReturn(), newHeadNums, newHeadIndexes, newHeadArgumentNums, 
        unfilledDepArray, filledDepArray, leftSpanStart, leftSpanEnd, leftIndex, rightSpanStart,
        rightSpanEnd, rightIndex);
  }
  
  private ChartEntry compose(ChartEntry first, ChartEntry second, Direction direction) {
    SyntacticCategory firstSyntax = first.getSyntax();
    SyntacticCategory secondSyntax = second.getSyntax();
    if (firstSyntax.isAtomic() || !firstSyntax.acceptsArgumentOn(direction) ||
        secondSyntax.isAtomic()) {
      return null;
    }
    
    SyntacticCategory firstArgumentType = firstSyntax.getArgument();
    SyntacticCategory returnType = secondSyntax.getReturn();
    while (returnType != null) {
      if (firstArgumentType.isUnifiableWith(returnType)) {
        return composeHelper(first, second, direction);
      }
    }
    return null;
  }
  
  private ChartEntry composeHelper(ChartEntry first, ChartEntry second, Direction direction) {
    
    
  }
  
  /**
   * Replaces all instances of {@code dependencyVariableNum} in {@code dep} 
   * with the variable given by {@code replacementVariableNum}. 
   * 
   * @param dependencyVariableNum
   * @param dep
   * @param replacementVariableNum
   * @param unfilledDepsAccumulator
   */
  private int substituteDependencyVariable(int dependencyVariableNum, UnfilledDependency dep,
      int replacementVariableNum, long[] unfilledDepsAccumulator, int unfilledDepsSize) {
    UnfilledDependency newDep = dep;
    if (dep.getSubjectIndex() == dependencyVariableNum) {
      newDep = newDep.replaceSubject(replacementVariableNum);
    } else {
      Preconditions.checkState(dep.hasSubject());
    }
    
    if (dep.getObjectIndex() == dependencyVariableNum) {
      newDep = newDep.replaceObject(replacementVariableNum);
    } else {
      Preconditions.checkState(dep.hasObject());
    }
    
    unfilledDepsAccumulator[unfilledDepsSize] = unfilledDependencyToLong(newDep);
    return 1;
  }
  
  /**
   * Replaces all instances of {@code dependencyVariableNum} in {@code dep} 
   * with {@code value}, which is a defined predicate. 
   *  
   * @param dependencyVariableNum
   * @param dep
   * @param value
   * @param filledDepsAccumulator
   */
  private int substituteDependencyVariable(int dependencyVariableNum, UnfilledDependency dep,
      IndexedPredicate value, long[] filledDepsAccumulator, int filledDepsSize) {
    UnfilledDependency newDep = dep;
    if (dep.getSubjectIndex() == dependencyVariableNum) {
      newDep = newDep.replaceSubject(value);
    }
    
    if (dep.getObjectIndex() == dependencyVariableNum) {
      newDep = newDep.replaceObject(value);
    }
    
    Preconditions.checkState(newDep.isFilledDependency());
    filledDepsAccumulator[filledDepsSize] = unfilledDependencyToLong(newDep);
    return 1;
  }

  // Methods for efficiently encoding dependencies as longs //////////////////////////////
  
  /**
   * Computes the {@code keyNum} containing the weight for {@code dep} 
   * in {@code dependencyTensor}.
   * 
   * @param dep
   * @return
   */
  private long dependencyLongToTensorKeyNum(long depLong) {
    int headNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK) - MAX_ARG_NUM;
    int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK) - MAX_ARG_NUM;

    int argNumNum = dependencyArgNumType.getValueIndex(
        (int) ((depLong >> ARG_NUM_OFFSET) & ARG_NUM_MASK));

    return dependencyTensor.dimKeyToKeyNum(new int[] {headNum, argNumNum, objectNum});
  }
  
  private long unfilledDependencyToLong(UnfilledDependency dep) {
    long argNum = dep.getArgumentIndex();
    long objectNum, objectWordInd, subjectNum, subjectWordInd;
    
    if (dep.hasObject()) { 
      IndexedPredicate obj = dep.getObject();
      objectNum = dependencyHeadType.getValueIndex(obj.getHead()) + MAX_ARG_NUM;
      objectWordInd = obj.getHeadIndex();
    } else {
      objectNum = dep.getObjectIndex();
      objectWordInd = 0L;
    }
    
    if (dep.hasSubject()) {
      IndexedPredicate sbj = dep.getSubject();
      subjectNum = dependencyHeadType.getValueIndex(sbj.getHead()) + MAX_ARG_NUM;
      subjectWordInd = sbj.getHeadIndex();
    } else {
      subjectNum = dep.getSubjectIndex();
      subjectWordInd = 0L;
    }
    
    return marshalUnfilledDependency(objectNum, argNum, subjectNum, objectWordInd, subjectWordInd);
  }
  
  private long marshalUnfilledDependency(long objectNum, long argNum, long subjectNum,
      long objectWordInd, long subjectWordInd) {
    long value = 0L;
    value += objectNum << OBJECT_OFFSET;
    value += argNum << ARG_NUM_OFFSET;
    value += subjectNum << SUBJECT_OFFSET;
    value += objectWordInd << OBJECT_WORD_IND_OFFSET;
    value += subjectWordInd << SUBJECT_WORD_IND_OFFSET;
    return value;
  }
  
  private int getArgNumFromDep(long depLong) { 
    return (int) ((depLong >> ARG_NUM_OFFSET) & ARG_NUM_MASK);
  }
  
  private int getObjectArgNumFromDep(long depLong) {
    int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK);
    if (objectNum >= MAX_ARG_NUM) {
      return -1;
    } else {
      return objectNum;
    }
  }
  
  private int getObjectPredicateFromDep(long depLong) {
    int objectNum = (int) ((depLong >> OBJECT_OFFSET) & PREDICATE_MASK);
    if (objectNum >= MAX_ARG_NUM) {
      return objectNum - MAX_ARG_NUM;
    } else {
      return -1;
    }
  }
  
  private int getSubjectArgNumFromDep(long depLong) {
    int subjectNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK);
    if (subjectNum >= MAX_ARG_NUM) {
      return -1;
    } else {
      return subjectNum;
    }
  }
  
  private int getSubjectPredicateFromDep(long depLong) {
    int subjectNum = (int) ((depLong >> SUBJECT_OFFSET) & PREDICATE_MASK);
    if (subjectNum >= MAX_ARG_NUM) {
      return subjectNum - MAX_ARG_NUM;
    } else {
      return -1;
    }
  }
  
  private UnfilledDependency longToUnfilledDependency(long value) {
    int argNum, objectNum, objectWordInd, subjectNum, subjectWordInd;
    
    objectNum = (int) ((value >> OBJECT_OFFSET) & PREDICATE_MASK);  
    argNum = (int) ((value >> ARG_NUM_OFFSET) & ARG_NUM_MASK);
    subjectNum = (int) ((value >> SUBJECT_OFFSET) & PREDICATE_MASK);
    objectWordInd = (int) ((value >> OBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);
    subjectWordInd = (int) ((value >> SUBJECT_WORD_IND_OFFSET) & WORD_IND_MASK);
    
    IndexedPredicate sbj = null, obj = null;
    int objectArgIndex = -1, subjectArgIndex = -1;
    if (objectNum >= MAX_ARG_NUM) {
      String objectHead = (String) dependencyHeadType.getValue(objectNum - MAX_ARG_NUM);
      obj = new IndexedPredicate(objectHead, objectWordInd);
    } else {
      objectArgIndex = objectNum;
    }
    
    if (subjectNum >= MAX_ARG_NUM) {
      String subjectHead = (String) dependencyHeadType.getValue(subjectNum - MAX_ARG_NUM);
      sbj = new IndexedPredicate(subjectHead, subjectWordInd);
    } else {
      subjectArgIndex = subjectNum;
    }
    
    return new UnfilledDependency(sbj, subjectArgIndex, argNum, obj, objectArgIndex);
  }
  
  private UnfilledDependency[] longArrayToUnfilledDependencyArray(long[] values) {
    UnfilledDependency[] unfilled = new UnfilledDependency[values.length];
    for (int i = 0; i < values.length; i++) {
      unfilled[i] = longToUnfilledDependency(values[i]);
    }
    return unfilled;
  }

  private long[] unfilledDependencyArrayToLongArray(List<UnfilledDependency> deps) {
    long[] values = new long[deps.size()];
    for (int i = 0; i < deps.size(); i++) {
      values[i] = unfilledDependencyToLong(deps.get(i));
    }
    return values;
  }
  
  public DependencyStructure[] longArrayToFilledDependencyArray(long[] values) {
    DependencyStructure[] deps = new DependencyStructure[values.length];
    for (int i = 0; i < values.length; i++) {
      UnfilledDependency unfilled = longToUnfilledDependency(values[i]);
      deps[i] = unfilled.toDependencyStructure(); 
    }
    return deps;
  }
  
  public Set<IndexedPredicate> headArrayToIndexedPredicateArray(int[] headWordNums, int[] headIndexes) {
    Set<IndexedPredicate> predicates = Sets.newHashSet();
    for (int i = 0; i < headWordNums.length; i++) {
      predicates.add(new IndexedPredicate((String) dependencyHeadType.getValue(headWordNums[i]), headIndexes[i]));
    }
    return predicates;
  }
}