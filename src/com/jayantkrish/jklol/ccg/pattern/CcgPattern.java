package com.jayantkrish.jklol.ccg.pattern;

import java.util.List;

import com.jayantkrish.jklol.ccg.CcgParse;

/**
 * A regex-like pattern for matching portions of a CCG parse.
 * 
 * @author jayant
 *
 */
public interface CcgPattern {

  List<CcgParse> match(CcgParse parse);
}
