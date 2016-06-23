package com.jayantkrish.jklol.ccg.enumeratelf;

import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.jayantkrish.jklol.ccg.lambda.Type;
import com.jayantkrish.jklol.ccg.lambda2.Expression2;

public class LfNode {
  private final Expression2 lf;
  private final Type type;
  private final boolean[] usedMentions;
  
  public LfNode(Expression2 lf, Type type, boolean[] usedMentions) {
    this.lf = Preconditions.checkNotNull(lf);
    this.type = Preconditions.checkNotNull(type);
    this.usedMentions = Arrays.copyOf(usedMentions, usedMentions.length);
  }

  public Expression2 getLf() {
    return lf;
  }
  
  public Type getType() {
    return type;
  }
  
  public boolean[] getUsedMentions() {
    return usedMentions;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((lf == null) ? 0 : lf.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    result = prime * result + Arrays.hashCode(usedMentions);
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
    LfNode other = (LfNode) obj;
    if (lf == null) {
      if (other.lf != null)
        return false;
    } else if (!lf.equals(other.lf))
      return false;
    if (type == null) {
      if (other.type != null)
        return false;
    } else if (!type.equals(other.type))
      return false;
    if (!Arrays.equals(usedMentions, other.usedMentions))
      return false;
    return true;
  }
}