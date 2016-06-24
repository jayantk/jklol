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

  public List<Expression2> enumerate(Set<Expression2> startNodes, int max) {
    return enumerate(startNodes, Collections.emptyList(), max);
  }

  public List<Expression2> enumerate(Set<Expression2> startNodeSet,
      List<EnumerationRuleFilter> addedFilters, int max) {
    List<EnumerationRuleFilter> allFilters = addedFilters;
    
    List<Expression2> startNodes = Lists.newArrayList(startNodeSet);
    Queue<LfNode> queue = new LinkedList<LfNode>();
    for (int i = 0; i < startNodes.size(); i++) {
      Expression2 startNode = startNodes.get(i);
      Type type = StaticAnalysis.inferType(startNode, typeDeclaration);
      boolean[] usedStartNodes = new boolean[startNodes.size()];
      usedStartNodes[i] = true;
      queue.add(new LfNode(startNode, type, usedStartNodes));
    }
    
    Set<LfNode> queuedNodes = Sets.newHashSet(queue);
    Set<LfNode> exploredNodes = Sets.newHashSet();
    while (queue.size() > 0 && queuedNodes.size() < max) {
      LfNode node = queue.poll();
      
      for (UnaryEnumerationRule rule : unaryRules) {
        if (rule.isApplicable(node)) {
          LfNode result = rule.apply(node);
          
          if (passesFilters(result, node, allFilters)) {
            enqueue(result, queue, queuedNodes);
          }
        }
      }
      
      for (BinaryEnumerationRule rule : binaryRules) {
        for (LfNode exploredNode : exploredNodes) {
          if (rule.isApplicable(node, exploredNode)) {
            LfNode result = rule.apply(node, exploredNode);
          
            if (passesFilters(result, node, allFilters) && passesFilters(result, exploredNode, allFilters)) {
              enqueue(result, queue, queuedNodes);
            }
          }
          
          if (rule.isApplicable(exploredNode, node)) {
            LfNode result = rule.apply(exploredNode, node);

            if (passesFilters(result, node, allFilters) && passesFilters(result, exploredNode, allFilters)) {
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

  public Chart enumerateDp(Set<Expression2> startNodes, Object context, int maxSize) {
    Chart chart = new Chart();
    for (Expression2 startNode : startNodes) {
      Optional<Object> denotation = executor.evaluateSilent(startNode, context);
      if (denotation.isPresent()) {
        Type type = StaticAnalysis.inferType(startNode, typeDeclaration);
        CellKey cell = new CellKey(type, 1, denotation.get());
        chart.addCellInitial(cell, startNode);
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
    private final Multimap<Integer, Expression2> initialLfs;
    private final Multimap<Integer, CellKey> cellsBySize;
    private final Multimap<Integer, UnaryChartEntry> unaryBackpointers;
    private final Multimap<Integer, BinaryChartEntry> binaryBackpointers;

    public Chart() {
      cells = IndexedList.create();
      initialLfs = HashMultimap.create();
      cellsBySize = HashMultimap.create();
      unaryBackpointers = HashMultimap.create();
      binaryBackpointers = HashMultimap.create();
    }
    
    private void addCell(CellKey cell) {
      cells.add(cell);
      cellsBySize.put(cell.size, cell);
    }
    
    public Collection<CellKey> getCellsBySize(int size) {
      return cellsBySize.get(size);
    }
    
    public void addCellInitial(CellKey cell, Expression2 lf) {
      addCell(cell);
      initialLfs.put(cells.getIndex(cell), lf);
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

    public Set<Expression2> getLogicalFormsFromDenotation(Object denotation) {
      return getLogicalFormsFromPredicate(Predicates.equalTo(denotation));
    }

    public Set<Expression2> getLogicalFormsFromPredicate(Predicate<Object> predicate) {
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
      
      Multimap<Integer, Expression2> cellLfs = HashMultimap.create();
      for (Integer size : sizes) {
        for (CellKey cell : cellsBySize.get(size)) {
          int cellInd = cells.getIndex(cell);
          if (queued.contains(cellInd)) {
            cellLfs.putAll(cellInd, initialLfs.get(cellInd));
            
            for (UnaryChartEntry entry : unaryBackpointers.get(cellInd)) {
              int startInd = cells.getIndex(entry.start);
              for (Expression2 startLf : cellLfs.get(startInd)) {
                cellLfs.put(cellInd, entry.unaryRule.apply(startLf));
              }
            }
            
            for (BinaryChartEntry entry : binaryBackpointers.get(cellInd)) {
              int arg1Ind = cells.getIndex(entry.startArg1);
              int arg2Ind = cells.getIndex(entry.startArg2);
              for (Expression2 arg1Lf : cellLfs.get(arg1Ind)) {
                for (Expression2 arg2Lf : cellLfs.get(arg2Ind)) {
                  cellLfs.put(cellInd, entry.binaryRule.apply(arg1Lf, arg2Lf));
                }
              }
            }
          }
        }
      }
      
      Set<Expression2> expressions = Sets.newHashSet();
      for (Integer denotationCellInd : denotationCellInds) {
        expressions.addAll(cellLfs.get(denotationCellInd));
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
