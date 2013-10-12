package com.jayantkrish.jklol.sequence;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;

public class ConvertingLocalContext<I, O> implements LocalContext<O> {
  
  private final LocalContext<I> inputContext;
  private final Function<I, O> converter;

  public ConvertingLocalContext(LocalContext<I> inputContext, Function<I, O> converter) {
    this.inputContext = Preconditions.checkNotNull(inputContext);
    this.converter = Preconditions.checkNotNull(converter);
  }

  @Override
  public O getItem() {
    return converter.apply(inputContext.getItem());
  }

  @Override
  public O getItem(int relativeOffset, Function<? super Integer, O> endFunction) {
    if (relativeOffset < getMinOffset()) {
      return endFunction.apply(relativeOffset - getMinOffset());
    } else if (relativeOffset > getMaxOffset()) {
      return endFunction.apply(relativeOffset - getMaxOffset());
    } else {
      return converter.apply(inputContext.getItem(relativeOffset, Functions.<I>constant(null)));
    }
  }

  @Override
  public int getMaxOffset() {
    return inputContext.getMaxOffset();
  }
  
  @Override
  public int getMinOffset() {
    return inputContext.getMinOffset();
  }
}
