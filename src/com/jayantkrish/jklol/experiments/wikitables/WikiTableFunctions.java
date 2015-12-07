package com.jayantkrish.jklol.experiments.wikitables;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jayantkrish.jklol.lisp.AmbEval.AmbFunctionValue;
import com.jayantkrish.jklol.lisp.ConstantValue;
import com.jayantkrish.jklol.lisp.Environment;
import com.jayantkrish.jklol.lisp.EvalError;
import com.jayantkrish.jklol.lisp.FunctionValue;

public class WikiTableFunctions {
  
  private static int ROW_MULTIPLE=1000;
  
  public static class GetTable implements FunctionValue {
    private Map<String, Integer> tableIdMap;
    private List<WikiTable> tables;
    
    public GetTable(Map<String, Integer> tableIdMap, List<WikiTable> tables) {
      this.tableIdMap = tableIdMap;
      this.tables = tables;
    }
    
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      String tableName = cast(argumentValues.get(0), String.class);
      return tables.get(tableIdMap.get(tableName));
    }
  }
  
  public static class GetTableCol implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      WikiTable table = cast(argumentValues.get(0), WikiTable.class);
      String colName = cast(argumentValues.get(1), String.class);
      return table.getColumnByHeading(colName);
    }
  }
  
  public static class GetTableCells implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      WikiTable table = cast(argumentValues.get(0), WikiTable.class);
      
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
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      WikiTable table = cast(argumentValues.get(0), WikiTable.class);
      int rowId = cast(argumentValues.get(1), Integer.class);
      
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
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      WikiTable table = cast(argumentValues.get(0), WikiTable.class);
      int colId = cast(argumentValues.get(1), Integer.class);
      
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

  public static class GetCol implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return ((Integer) argumentValues.get(0)) % ROW_MULTIPLE;
    }
  }
  
  public static class GetRow implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return ((Integer) argumentValues.get(0)) / ROW_MULTIPLE;
    }
  }
  
  public static class GetValue implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      WikiTable table = cast(argumentValues.get(0), WikiTable.class);
      Integer cell = cast(argumentValues.get(1), Integer.class);
      int rowId = cell / ROW_MULTIPLE;
      int colId = cell % ROW_MULTIPLE;
      String value = table.getValue(rowId, colId);
      return value;
    }
  }
  
  public static class SetFilter implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      AmbFunctionValue f = cast(argumentValues.get(0), AmbFunctionValue.class);
      Set<?> objs = cast(argumentValues.get(1), Set.class);
      
      Set<Object> filtered = Sets.newHashSet();
      for (Object o : objs) {
        Object value = f.apply(Arrays.asList(o), env, null);
        if (value.equals(ConstantValue.TRUE)) {
          filtered.add(o);
        }
      }
      return filtered;
    }
  }
  
  public static class SetMap implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      AmbFunctionValue f = cast(argumentValues.get(0), AmbFunctionValue.class);
      Set<?> objs = cast(argumentValues.get(1), Set.class);

      Set<Object> mapped = Sets.newHashSet();
      for (Object o : objs) {
        Object value = f.apply(Arrays.asList(o), env, null);
        mapped.add(value);
      }
      return mapped;
    }
  }
  
  public static class SetMin implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      AmbFunctionValue f = cast(argumentValues.get(0), AmbFunctionValue.class);
      List<?> objs = Lists.newArrayList(cast(argumentValues.get(1), Set.class));

      int min = Integer.MAX_VALUE;
      int minIndex = -1;
      boolean unique = true;
      for (int i = 0; i < objs.size(); i++) {
        int value = cast(f.apply(Arrays.asList(objs.get(i)), env, null), Integer.class);
        
        if (value < min) {
          min = value;
          minIndex = i;
          unique = true;
        } else if (value == min) {
          unique = false;
        }
      }
      
      if (minIndex == -1) {
        throw new EvalError("set-min on empty set");
      } else if (!unique) {
        throw new EvalError("set-min on set not unique");
      }

      return objs.get(minIndex);
    }
  }
  
  public static class SetMax implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      AmbFunctionValue f = cast(argumentValues.get(0), AmbFunctionValue.class);
      List<?> objs = Lists.newArrayList(cast(argumentValues.get(1), Set.class));

      int max = Integer.MIN_VALUE;
      int maxIndex = -1;
      boolean unique = true;
      for (int i = 0; i < objs.size(); i++) {
        int value = cast(f.apply(Arrays.asList(objs.get(i)), env, null), Integer.class);
        
        if (value > max) {
          max = value;
          maxIndex = i;
          unique = true;
        } else if (value == max) {
          unique = false;
        }
      }
      
      if (maxIndex == -1) {
        throw new EvalError("set-max on empty set");
      } else if (!unique) {
        throw new EvalError("set-max on set not unique");
      }

      return objs.get(maxIndex);
    }
  }
  
  public static class SetSize implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      Set<?> objs = cast(argumentValues.get(0), Set.class);
      return objs.size();
    }
  }
  
  public static class SetContains implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 2);
      Set<?> objs = cast(argumentValues.get(0), Set.class);
      return objs.contains(argumentValues.get(1)) ? ConstantValue.TRUE : ConstantValue.FALSE;
    }
  }
  
  public static class MakeSet implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Set<Object> objs = Sets.newHashSet();
      objs.addAll(argumentValues);
      return objs;
    }
  }
  
  public static class SetUnion implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      Set<?> objs = cast(argumentValues.get(0), Set.class);
      Set<Object> result = Sets.newHashSet();
      for (Object obj : objs) {
        result.addAll((Set<?>) obj);
      }
      return result;
    }
  }
  
  public static class IsSet implements FunctionValue {
    @Override
    public Object apply(List<Object> argumentValues, Environment env) {
      Preconditions.checkArgument(argumentValues.size() == 1);
      return argumentValues.get(0) instanceof Set ? ConstantValue.TRUE : ConstantValue.FALSE;
    }
  }

  
  public static <T> T cast(Object o, Class<T> clazz) {
    if (clazz.isInstance(o)) {
      return clazz.cast(o);
    } else {
      throw new EvalError("Casting error: " + o);
    }
  }
}
