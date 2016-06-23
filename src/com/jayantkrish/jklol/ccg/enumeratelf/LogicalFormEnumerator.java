package com.jayantkrish.jklol.ccg.enumeratelf;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executor;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.ccg.lambda.ExpressionParser;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda.TypeDeclaration;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;
import com.jayantkrish.jklol.ccg.lambda2.ExpressionSimplifier;
import com.jayantkrish.jklol.ccg.lambda2.StaticAnalysis;

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
  private final TypeDeclaration typeDeclaration;
  
  private final List<EnumerationRuleFilter> filters;

  public LogicalFormEnumerator(List<UnaryEnumerationRule> unaryRules,
      List<BinaryEnumerationRule> binaryRules, List<EnumerationRuleFilter> filters,
      TypeDeclaration typeDeclaration) {
    this.unaryRules = ImmutableList.copyOf(unaryRules);
    this.binaryRules = ImmutableList.copyOf(binaryRules);
    this.filters = ImmutableList.copyOf(filters);
    this.typeDeclaration = Preconditions.checkNotNull(typeDeclaration);
  }
  
  public static LogicalFormEnumerator fromRuleStrings(String[][] unaryRules,
      String[][] binaryRules, List<EnumerationRuleFilter> filters,
      ExpressionSimplifier simplifier, TypeDeclaration types) {
    ExpressionParser<Type> typeParser = ExpressionParser.typeParser();
    ExpressionParser<Expression2> lfParser = ExpressionParser.expression2();

    List<UnaryEnumerationRule> unaryRuleList = Lists.newArrayList();
    for (int i = 0; i < unaryRules.length; i++) {
      Type type = typeParser.parse(unaryRules[i][0]);
      Expression2 lf = lfParser.parse(unaryRules[i][1]);
      unaryRuleList.add(new UnaryEnumerationRule(type, lf, simplifier, types));
    }

    List<BinaryEnumerationRule> binaryRuleList = Lists.newArrayList();
    for (int i = 0; i < binaryRules.length; i++) {
      Type type1 = typeParser.parse(binaryRules[i][0]);
      Type type2 = typeParser.parse(binaryRules[i][1]);
      Expression2 lf = lfParser.parse(binaryRules[i][2]);

      binaryRuleList.add(new BinaryEnumerationRule(type1, type2, lf, simplifier, types));
    }

    return new LogicalFormEnumerator(unaryRuleList, binaryRuleList, filters, types);
  }
  
  public List<Expression2> enumerate(Set<Expression2> startNodes, int max) {
    return enumerate(startNodes, Collections.emptyList(), max);
  }

  public List<Expression2> enumerate(Set<Expression2> startNodeSet,
      List<EnumerationRuleFilter> addedFilters, int max) {
    List<EnumerationRuleFilter> allFilters = Lists.newArrayList(filters);
    allFilters.addAll(addedFilters);
    
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
  
  private static boolean passesFilters(LfNode result, LfNode from, List<EnumerationRuleFilter> filters) {
    for (EnumerationRuleFilter filter : filters) {
      if (!filter.apply(from, result)) {
        return false;
      }
    }
    return true;
  }

  private static void enqueue(LfNode node, Queue<LfNode> queue, Set<LfNode> queuedNodes) {
    if (!queuedNodes.contains(node)) {
      queuedNodes.add(node);
      queue.add(node);
    }
  }
  
  /*
  public List<Expression2> enumerateDp(Set<Expression2> startNodes,
      List<EnumerationRuleFilter> addedFilters, Object environment, int maxSize) {
    SetMultimap<Integer, CellKey> cellsBySize = HashMultimap.create();
    
    for (Expression2 startNode : startNodes) {
      Object denotation = executor.execute(startNode, environment);
      Type type = StaticAnalysis.inferType(startNode, typeDeclaration);
      CellKey cell = new CellKey(type, 1, denotation);
      cellsBySize.put(cell.getSize(), cell);
    }
    
    for (int size = 1; size < maxSize; size++) {
      for (CellKey cell : cellsBySize.get(size)) {
        for (UnaryEnumerationRule rule : unaryRules) {
          if (rule.isTypeConsistent(cell.type)) {
            Executor.execute()
            CellKey next = rule.apply(cell);
            chart.addCellUnary(cell, next, rule);
          }
        }

        for (int j = 1; j <= size; j++) {
          for (CellKey otherCell : cellsBySize.get(j)) {
            for (BinaryEnumerationRule rule : binaryRules) {
              if (rule.isApplicable(cell, otherCell)) {
                // Etc.
              }

              if (rule.isApplicable(otherCell, cell)) {
                // EtC.
              }
            }
          }
        }
      }
    }
    
    
    // initialize a chart with (type, size, denotation)
    // for each unary rule:
    //   for each entry with max size:
    //     apply the rule to create an entry of size + 1
    // for each binary rule:
    //   for each entry with max size:
    //     for each entry (collapsed by denotation):
    //       apply binary rule to create next entry
    
    
  }
  */
  
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

  /*
  private static class Chart {
    
    private final Map<Integer, CellKey> cellsBySize;
    
    private final Map<Pair<CellKey, CellKey>, UnaryEnumerationRule>;
    private final Map<Triple<CellKey, CellKey, CellKey>, BinaryEnumerationRule>;
    
    public Collection<CellKey> getCellsBySize(int size);
    
    public void addCellUnary(CellKey start, CellKey result, UnaryEnumerationRule rule);
    
    public void addCellBinary(CellKey startLeft, CellKey startRight,
        CellKey result, BinaryEnumerationRule rule);
  }
  */
}
