package com.jayantkrish.jklol.ccg.enumeratelf;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionExecutor;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;
import com.jayantkrish.jklol.util.IndexedList;

/**
 * Enumerates logical forms by applying a collection of
 * rules to an initial starting set.
 * 
 * @author jayantk
 *
 */
public class LogicalFormEnumerator {

  private final List<UnaryEnumerationRule> unaryRules;
  private final List<BinaryEnumerationRule> binaryRules;

  private final ExpressionSimplifier simplifier;
  private final TypeDeclaration typeDeclaration;
  private final ExpressionExecutor executor;

  public LogicalFormEnumerator(List<UnaryEnumerationRule> unaryRules,
      List<BinaryEnumerationRule> binaryRules, ExpressionSimplifier simplifier,
      TypeDeclaration typeDeclaration, ExpressionExecutor executor) {
    this.unaryRules = ImmutableList.copyOf(unaryRules);
    this.binaryRules = ImmutableList.copyOf(binaryRules);
    this.simplifier = Preconditions.checkNotNull(simplifier);
    this.typeDeclaration = Preconditions.checkNotNull(typeDeclaration);
    this.executor = Preconditions.checkNotNull(executor);
  }

  public static LogicalFormEnumerator fromRuleStrings(String[][] unaryRules,
      String[][] binaryRules, ExpressionSimplifier simplifier, TypeDeclaration types,
      ExpressionExecutor executor) {
    ExpressionParser<Type> typeParser = ExpressionParser.typeParser();
    ExpressionParser<Expression2> lfParser = ExpressionParser.expression2();

    List<UnaryEnumerationRule> unaryRuleList = Lists.newArrayList();
    for (int i = 0; i < unaryRules.length; i++) {
      Type inputType = typeParser.parse(unaryRules[i][0]);
      Expression2 lf = lfParser.parse(unaryRules[i][1]);
      
      Type ruleFuncType = Type.createFunctional(inputType, TypeDeclaration.TOP, false);
      ruleFuncType = StaticAnalysis.inferType(lf, ruleFuncType, types);
      Type outputType = ruleFuncType.getReturnType();

      unaryRuleList.add(new UnaryEnumerationRule(inputType, outputType, lf));
    }

    List<BinaryEnumerationRule> binaryRuleList = Lists.newArrayList();
    for (int i = 0; i < binaryRules.length; i++) {
      Type type1 = typeParser.parse(binaryRules[i][0]);
      Type type2 = typeParser.parse(binaryRules[i][1]);
      Expression2 lf = lfParser.parse(binaryRules[i][2]);
      
      Type ruleFuncType = Type.createFunctional(type1,
          Type.createFunctional(type2, TypeDeclaration.TOP, false), false);
      ruleFuncType = StaticAnalysis.inferType(lf, ruleFuncType, types);
      Type outputType = ruleFuncType.getReturnType().getReturnType();

      binaryRuleList.add(new BinaryEnumerationRule(type1, type2, outputType, lf));
    }

    return new LogicalFormEnumerator(unaryRuleList, binaryRuleList, simplifier, types, executor);
  }

  public List<Expression2> enumerate(Set<Expression2> startNodes,
      Object context, int max) {
    return enumerate(startNodes, context, max, Collections.emptyList());
  }

  public List<Expression2> enumerate(Set<Expression2> startNodeSet,
      Object context, int max, List<EnumerationRuleFilter> filters) {

    List<Expression2> startNodes = Lists.newArrayList(startNodeSet);
    Queue<LfNode> queue = new LinkedList<LfNode>();
    for (int i = 0; i < startNodes.size(); i++) {
      Expression2 startNode = startNodes.get(i);
      Type type = StaticAnalysis.inferType(startNode, typeDeclaration);
      int[] mentionCounts = new int[startNodes.size()];
      mentionCounts[i] = 1;
      Optional<Object> denotation = executor.evaluateSilent(startNode, context);
      if (denotation.isPresent()) {
        queue.add(new LfNode(startNode, type, mentionCounts, denotation.get()));
      }
    }

    Set<LfNode> queuedNodes = Sets.newHashSet(queue);
    Set<LfNode> exploredNodes = Sets.newHashSet();
    while (queue.size() > 0 && queuedNodes.size() < max) {
      LfNode node = queue.poll();
      
      for (UnaryEnumerationRule rule : unaryRules) {
        if (rule.isTypeConsistent(node.getType())) {
          LfNode result = applyRule(rule, node, context);
          
          if (passesFilters(result, node, filters)) {
            enqueue(result, queue, queuedNodes);
          }
        }
      }

      for (BinaryEnumerationRule rule : binaryRules) {
        for (LfNode exploredNode : exploredNodes) {
          if (rule.isTypeConsistent(node.getType(), exploredNode.getType())) {
            LfNode result = applyRule(rule, node, exploredNode, context);

            if (passesFilters(result, node, filters) && passesFilters(result, exploredNode, filters)) {
              enqueue(result, queue, queuedNodes);
            }
          }
          
          if (rule.isTypeConsistent(exploredNode.getType(), node.getType())) {
            LfNode result = applyRule(rule, exploredNode, node, context);

            if (passesFilters(result, node, filters) && passesFilters(result, exploredNode, filters)) {
              enqueue(result, queue, queuedNodes);
            }
          }
        }
      }
      
      exploredNodes.add(node);
    }

    List<Expression2> expressions = Lists.newArrayList();
    for (LfNode node : queuedNodes) {
      expressions.add(node.getLf());
    }
    return expressions;
  }

  private static void enqueue(LfNode node, Queue<LfNode> queue, Set<LfNode> queuedNodes) {
    if (!queuedNodes.contains(node)) {
      queuedNodes.add(node);
      queue.add(node);
    }
  }
  
  private LfNode applyRule(UnaryEnumerationRule rule, LfNode node, Object context) {
    Expression2 ruleLf = rule.getLogicalForm();
    Expression2 result = simplifier.apply(Expression2.nested(ruleLf, node.getLf()));
    Type resultType = StaticAnalysis.inferType(result, typeDeclaration);
    Object denotation = executor.apply(ruleLf, context, Lists.newArrayList(node.getDenotation()));
    return new LfNode(result, resultType, node.getMentionCounts(), denotation);
  }

  private LfNode applyRule(BinaryEnumerationRule rule, LfNode arg1, LfNode arg2,
      Object context) {
    Expression2 ruleLf = rule.getLogicalForm();
    Expression2 result = simplifier.apply(Expression2.nested(ruleLf, arg1.getLf(), arg2.getLf()));
    Type resultType = StaticAnalysis.inferType(result, typeDeclaration);
    Object denotation = executor.apply(ruleLf, context,
        Lists.newArrayList(arg1.getDenotation(), arg2.getDenotation()));

    int[] mentionCounts = new int[arg1.getMentionCounts().length];
    for (int i = 0; i < mentionCounts.length; i++) {
      mentionCounts[i] = arg1.getMentionCounts()[i] + arg2.getMentionCounts()[i];
    }

    return new LfNode(result, resultType, mentionCounts, denotation);
  }

  private static boolean passesFilters(LfNode start, LfNode result, List<EnumerationRuleFilter> filters) {
    for (EnumerationRuleFilter filter : filters) {
      if (!filter.apply(start, result)) {
        return false;
      }
    }
    return true;
  }

  public Chart enumerateDp(Set<Expression2> startNodeSet, Object context, int maxSize) {
    Chart chart = new Chart(simplifier);
    List<Expression2> startNodes = Lists.newArrayList(startNodeSet);
    for (int i = 0; i < startNodes.size(); i++) {
      Expression2 startNode = startNodes.get(i);
      Optional<Object> denotation = executor.evaluateSilent(startNode, context);
      if (denotation.isPresent()) {
        Type type = StaticAnalysis.inferType(startNode, typeDeclaration);
        CellKey cell = new CellKey(type, 1, denotation.get());
        int[] mentionCounts = new int[startNodes.size()];
        mentionCounts[i] = 1;
        chart.addCellInitial(cell, startNode, mentionCounts);
      }
    }

    for (int size = 1; size < maxSize; size++) {
      // System.out.println("cells " + size + ": "  + chart.getCellsBySize(size));
      for (CellKey cell : chart.getCellsBySize(size)) {
        // Try applying every unary rule.
        for (UnaryEnumerationRule rule : unaryRules) {
          if (rule.isTypeConsistent(cell.type)) {
            Expression2 ruleLf = rule.getLogicalForm();
            Optional<Object> denotation = executor.applySilent(ruleLf, context,
                Arrays.asList(cell.denotation));
            if (denotation.isPresent()) {
              CellKey next = new CellKey(rule.getOutputType(), cell.size + 1, denotation.get());
              chart.addCellUnary(cell, next, rule);
            }
          }
        }

        // Try applying every binary rule with both argument permutations.
        for (int j = 1; j <= size; j++) {
          for (CellKey otherCell : chart.getCellsBySize(j)) {
            for (BinaryEnumerationRule rule : binaryRules) {
              
              List<CellKey> argIndexes = Lists.newArrayList(cell, otherCell);
              for (int k = 0; k < 2; k++) {
                // Generate the argument permutation.
                CellKey arg1 = argIndexes.get(k);
                CellKey arg2 = argIndexes.get((k + 1) % 2);
                
                if (rule.isTypeConsistent(arg1.type, arg2.type)) {
                  Expression2 ruleLf = rule.getLogicalForm();
                  Optional<Object> denotation = executor.applySilent(ruleLf, context,
                      Arrays.asList(arg1.denotation, arg2.denotation));
                  if (denotation.isPresent()) {
                    CellKey next = new CellKey(rule.getOutputType(), arg1.size + arg2.size + 1, denotation.get());
                    chart.addCellBinary(arg1, arg2, next, rule); 
                  }
                }
              }
            }
          }
        }
      }
    }
    return chart;
  }
  
  private static class CellKey {
    private final Type type;
    private final int size;
    private final Object denotation;

    public CellKey(Type type, int size, Object denotation) {
      this.type = type;
      this.size = size;
      this.denotation = denotation;
    }
    
    @Override
    public String toString() {
      return "[" + type + " " + size + " " + denotation + "]";
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((denotation == null) ? 0 : denotation.hashCode());
      result = prime * result + size;
      result = prime * result + ((type == null) ? 0 : type.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      CellKey other = (CellKey) obj;
      if (denotation == null) {
        if (other.denotation != null)
          return false;
      } else if (!denotation.equals(other.denotation))
        return false;
      if (size != other.size)
        return false;
      if (type == null) {
        if (other.type != null)
          return false;
      } else if (!type.equals(other.type))
        return false;
      return true;
    }
  }

  public static class Chart {
    private final IndexedList<CellKey> cells;
    private final Multimap<Integer, LfNode> initialLfs;
    private final Multimap<Integer, CellKey> cellsBySize;
    private final Multimap<Integer, UnaryChartEntry> unaryBackpointers;
    private final Multimap<Integer, BinaryChartEntry> binaryBackpointers;

    private final ExpressionSimplifier simplifier;
    
    public Chart(ExpressionSimplifier simplifier) {
      cells = IndexedList.create();
      initialLfs = HashMultimap.create();
      cellsBySize = HashMultimap.create();
      unaryBackpointers = HashMultimap.create();
      binaryBackpointers = HashMultimap.create();
      
      this.simplifier = Preconditions.checkNotNull(simplifier);
    }
    
    private void addCell(CellKey cell) {
      cells.add(cell);
      cellsBySize.put(cell.size, cell);
    }
    
    public Collection<CellKey> getCellsBySize(int size) {
      return cellsBySize.get(size);
    }

    public void addCellInitial(CellKey cell, Expression2 lf, int[] mentionCounts) {
      addCell(cell);
      initialLfs.put(cells.getIndex(cell),
          new LfNode(lf, cell.type, mentionCounts, cell.denotation));
    }

    public void addCellUnary(CellKey start, CellKey result, UnaryEnumerationRule rule) {
      Preconditions.checkArgument(cells.contains(start));

      addCell(result);
      int index = cells.getIndex(result);
      unaryBackpointers.put(index, new UnaryChartEntry(start, result, rule));
    }
    
    public void addCellBinary(CellKey startArg1, CellKey startArg2,
        CellKey result, BinaryEnumerationRule rule) {
      Preconditions.checkArgument(cells.contains(startArg1));
      Preconditions.checkArgument(cells.contains(startArg2));

      addCell(result);
      int index = cells.getIndex(result);
      binaryBackpointers.put(index, new BinaryChartEntry(startArg1, startArg2, result, rule));
    }

    public Set<Expression2> getLogicalFormsFromDenotation(Object denotation,
        List<EnumerationRuleFilter> filters) {
      return getLogicalFormsFromPredicate(Predicates.equalTo(denotation), filters);
    }

    public Set<Expression2> getLogicalFormsFromPredicate(Predicate<Object> predicate,
        List<EnumerationRuleFilter> filters) {
      // Identify cells whose expressions need to be computed.
      // Expressions of cells containing the denotation need to
      // be computed.
      Queue<Integer> queue = new LinkedList<Integer>();
      Set<Integer> denotationCellInds = Sets.newHashSet();
      for (CellKey cell : cells) {
        if (predicate.apply(cell.denotation)) {
          int cellIndex = cells.getIndex(cell);
          queue.add(cellIndex);
          denotationCellInds.add(cellIndex);
        }
      }

      // Compute expressions for all cells that lead to marked cells.
      Set<Integer> queued = Sets.newHashSet();
      queued.addAll(queue);
      while (!queue.isEmpty()) {
        int cellInd = queue.poll();

        for (UnaryChartEntry entry : unaryBackpointers.get(cellInd)) {
          int startInd = cells.getIndex(entry.start);
          if (!queued.contains(startInd)) {
            queue.add(startInd);
            queued.add(startInd);
          }
        }
        
        for (BinaryChartEntry entry : binaryBackpointers.get(cellInd)) {
          int arg1Ind = cells.getIndex(entry.startArg1);
          if (!queued.contains(arg1Ind)) {
            queue.add(arg1Ind);
            queued.add(arg1Ind);
          }

          int arg2Ind = cells.getIndex(entry.startArg2);
          if (!queued.contains(arg2Ind)) {
            queue.add(arg2Ind);
            queued.add(arg2Ind);
          }
        }
      }

      // Traverse cells in size order and generate their logical forms.
      List<Integer> sizes = Lists.newArrayList(cellsBySize.keySet());
      Collections.sort(sizes);
      
      Multimap<Integer, LfNode> cellLfs = HashMultimap.create();
      for (Integer size : sizes) {
        for (CellKey cell : cellsBySize.get(size)) {
          int cellInd = cells.getIndex(cell);
          if (queued.contains(cellInd)) {
            cellLfs.putAll(cellInd, initialLfs.get(cellInd));
            
            for (UnaryChartEntry entry : unaryBackpointers.get(cellInd)) {
              int startInd = cells.getIndex(entry.start);
              for (LfNode startLf : cellLfs.get(startInd)) {
                Expression2 lf = entry.unaryRule.apply(startLf.getLf(), simplifier);
                LfNode node = new LfNode(lf, cell.type, startLf.getMentionCounts(),
                    cell.denotation);
                if (passesFilters(startLf, node, filters)) {
                  cellLfs.put(cellInd, node);
                }
              }
            }
            
            for (BinaryChartEntry entry : binaryBackpointers.get(cellInd)) {
              int arg1Ind = cells.getIndex(entry.startArg1);
              int arg2Ind = cells.getIndex(entry.startArg2);
              for (LfNode arg1Lf : cellLfs.get(arg1Ind)) {
                for (LfNode arg2Lf : cellLfs.get(arg2Ind)) {
                  Expression2 lf = entry.binaryRule.apply(arg1Lf.getLf(), arg2Lf.getLf(), simplifier);
                  
                  int[] counts = new int[arg1Lf.getMentionCounts().length];
                  for (int i = 0; i < counts.length; i++) {
                    counts[i] = arg1Lf.getMentionCounts()[i] + arg2Lf.getMentionCounts()[i];
                  }
                  
                  LfNode node = new LfNode(lf, cell.type, counts, cell.denotation);
                  
                  if (passesFilters(arg1Lf, node, filters) && passesFilters(arg2Lf, node, filters)) {
                    cellLfs.put(cellInd, node);
                  }
                }
              }
            }
          }
        }
      }

      Set<Expression2> expressions = Sets.newHashSet();
      for (Integer denotationCellInd : denotationCellInds) {
        for (LfNode node : cellLfs.get(denotationCellInd)) {
          expressions.add(node.getLf());
        }
      }

      return expressions;
    }
  }

  public static class UnaryChartEntry {
    private final CellKey start;
    private final CellKey result;
    private final UnaryEnumerationRule unaryRule;

    public UnaryChartEntry(CellKey start, CellKey result, UnaryEnumerationRule unaryRule) {
      this.start = Preconditions.checkNotNull(start);
      this.result = Preconditions.checkNotNull(result);
      this.unaryRule = Preconditions.checkNotNull(unaryRule);
    }
  }
  
  public static class BinaryChartEntry {
    private final CellKey startArg1;
    private final CellKey startArg2;
    private final CellKey result;
    private final BinaryEnumerationRule binaryRule;
    
    public BinaryChartEntry(CellKey startArg1, CellKey startArg2, CellKey result,
        BinaryEnumerationRule binaryRule) {
      this.startArg1 = Preconditions.checkNotNull(startArg1);
      this.startArg2 = Preconditions.checkNotNull(startArg2);
      this.result = Preconditions.checkNotNull(result);
      this.binaryRule = Preconditions.checkNotNull(binaryRule);
    }
  }
}
