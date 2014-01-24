package com.jayantkrish.jklol.cvsm.eval;

import java.util.List;

/**
 * A function that can be applied to arguments and returns
 * a value.
 *  
 * @author jayantk
 */
public interface FunctionValue {

  Object apply(List<Object> argumentValues, Environment env, Eval eval);
}
