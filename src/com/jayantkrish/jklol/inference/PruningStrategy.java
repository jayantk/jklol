package com.jayantkrish.jklol.inference;

import com.jayantkrish.jklol.models.Factor;

public interface PruningStrategy {

  Factor apply(Factor factor);
}
