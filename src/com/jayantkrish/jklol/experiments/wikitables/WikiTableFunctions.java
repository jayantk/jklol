package com.jayantkrish.jklol.experiments.wikitables;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.EvalContext;
import com.jayantkrish.jklol.lisp.FunctionValue;
import com.jayantkrish.jklol.lisp.LispUtil;

public class WikiTableFunctions {
  
  private static int ROW_MULTIPLE=1000;
  private static String BAD_CELL_VALUE = "BAD_CELL"; 
  
  public static class GetTable implements FunctionValue {
    private Map<String, Integer> tableIdMap;
    private List<WikiTable> tables;
    
    public GetTable(Map<String, Integer> tableIdMap, List<WikiTable> tables) {
      this.tableIdMap = tableIdMap;
      this.tables = tables;
    }
    
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      String tableName = LispUtil.cast(argumentValues.get(0), String.class);
      return tables.get(tableIdMap.get(tableName));
    }
  }
  
  public static class GetTableCol implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      WikiTable table = LispUtil.cast(argumentValues.get(0), WikiTable.class);
      String colName = LispUtil.cast(argumentValues.get(1), String.class);
      return table.getColumnByHeading(colName);
    }
  }
  
  public static class GetTableCells implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      WikiTable table = LispUtil.cast(argumentValues.get(0), WikiTable.class);
      
      Set<Integer> cells = Sets.newHashSet();
      for (int i = 0; i < table.getRows().length; i++) {
        for (int j = 0; j < table.getHeadings().length; j++) {
          cells.add(i * ROW_MULTIPLE + j);
        }
      }
      return cells;
    }
  }
  
  public static class GetRowCells implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      WikiTable table = LispUtil.cast(argumentValues.get(0), WikiTable.class);
      int rowId = LispUtil.cast(argumentValues.get(1), Integer.class);
      
      if (rowId < 0 || rowId >= table.getNumRows()) {
        return Collections.emptySet();
      }
      
      Set<Integer> cells = Sets.newHashSet();
      for (int j = 0; j < table.getHeadings().length; j++) {
        cells.add(rowId * ROW_MULTIPLE + j);
      }
      return cells;
    }
  }

  public static class GetColCells implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      WikiTable table = LispUtil.cast(argumentValues.get(0), WikiTable.class);
      int colId = LispUtil.cast(argumentValues.get(1), Integer.class);
      
      if (colId < 0 || colId >= table.getNumColumns()) {
        return Collections.emptySet();
      }
      
      Set<Integer> cells = Sets.newHashSet();
      for (int i = 0; i < table.getNumRows(); i++) {
        cells.add(i * ROW_MULTIPLE + colId);
      }
      return cells;
    }
  }
  
  public static class GetCell implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      return ((Integer) argumentValues.get(0)) * ROW_MULTIPLE
          + ((Integer) argumentValues.get(1));
    }
  }

  public static class GetCol implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      return ((Integer) argumentValues.get(0)) % ROW_MULTIPLE;
    }
  }
  
  public static class GetRow implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      return ((Integer) argumentValues.get(0)) / ROW_MULTIPLE;
    }
  }
  
  public static class GetValue implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      WikiTable table = LispUtil.cast(argumentValues.get(0), WikiTable.class);
      Integer cell = LispUtil.cast(argumentValues.get(1), Integer.class);
      int rowId = cell / ROW_MULTIPLE;
      int colId = cell % ROW_MULTIPLE;
      
      if (rowId >= 0 && rowId < table.getNumRows() &&
          colId >= 0 && colId < table.getNumColumns()) {
        return table.getValue(rowId, colId);
      } else {
        return BAD_CELL_VALUE;
      }
    }
  }
  
  public static class SetFilter implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      AmbFunctionValue f = LispUtil.cast(argumentValues.get(0), AmbFunctionValue.class);
      Set<?> objs = LispUtil.cast(argumentValues.get(1), Set.class);
      
      Set<Object> filtered = Sets.newHashSet();
      for (Object o : objs) {
        Object value = f.apply(Arrays.asList(o), context, null);
        if (value.equals(ConstantValue.TRUE)) {
          filtered.add(o);
        }
      }
      return filtered;
    }
  }
  
  public static class SetMap implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      AmbFunctionValue f = LispUtil.cast(argumentValues.get(0), AmbFunctionValue.class);
      Set<?> objs = LispUtil.cast(argumentValues.get(1), Set.class);

      Set<Object> mapped = Sets.newHashSet();
      for (Object o : objs) {
        Object value = f.apply(Arrays.asList(o), context, null);
        mapped.add(value);
      }
      return mapped;
    }
  }
  
  public static class SetMin implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      AmbFunctionValue f = LispUtil.cast(argumentValues.get(0), AmbFunctionValue.class);
      List<?> objs = Lists.newArrayList(LispUtil.cast(argumentValues.get(1), Set.class));

      int min = Integer.MAX_VALUE;
      int minIndex = -1;
      boolean unique = true;
      for (int i = 0; i < objs.size(); i++) {
        int value = LispUtil.cast(f.apply(Arrays.asList(objs.get(i)), context, null), Integer.class);
        
        if (value < min) {
          min = value;
          minIndex = i;
          unique = true;
        } else if (value == min) {
          unique = false;
        }
      }

      LispUtil.checkState(minIndex != -1, "set-min on empty set");
      LispUtil.checkState(unique, "set-min on set not unique");

      return objs.get(minIndex);
    }
  }
  
  public static class SetMax implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      AmbFunctionValue f = LispUtil.cast(argumentValues.get(0), AmbFunctionValue.class);
      List<?> objs = Lists.newArrayList(LispUtil.cast(argumentValues.get(1), Set.class));

      int max = Integer.MIN_VALUE;
      int maxIndex = -1;
      boolean unique = true;
      for (int i = 0; i < objs.size(); i++) {
        int value = LispUtil.cast(f.apply(Arrays.asList(objs.get(i)), context, null), Integer.class);
        
        if (value > max) {
          max = value;
          maxIndex = i;
          unique = true;
        } else if (value == max) {
          unique = false;
        }
      }
      
      LispUtil.checkState(maxIndex != -1, "set-max on empty set");
      LispUtil.checkState(unique, "set-max on set not unique");

      return objs.get(maxIndex);
    }
  }
  
  public static class SetSize implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      Set<?> objs = LispUtil.cast(argumentValues.get(0), Set.class);
      return objs.size();
    }
  }
  
  public static class SetContains implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 2);
      Set<?> objs = LispUtil.cast(argumentValues.get(0), Set.class);
      return objs.contains(argumentValues.get(1)) ? ConstantValue.TRUE : ConstantValue.FALSE;
    }
  }
  
  public static class MakeSet implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      Set<Object> objs = Sets.newHashSet();
      objs.addAll(argumentValues);
      return objs;
    }
  }
  
  public static class SetUnion implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      Set<?> objs = LispUtil.cast(argumentValues.get(0), Set.class);
      Set<Object> result = Sets.newHashSet();
      for (Object obj : objs) {
        result.addAll((Set<?>) obj);
      }
      return result;
    }
  }
  
  public static class IsSet implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, EvalContext context) {
      LispUtil.checkArgument(argumentValues.size() == 1);
      return argumentValues.get(0) instanceof Set ? ConstantValue.TRUE : ConstantValue.FALSE;
    }
  }
}
