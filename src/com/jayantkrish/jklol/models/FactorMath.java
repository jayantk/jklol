package com.jayantkrish.jklol.models;

import java.util.ArrayList;
import java.util.List;

import com.jayantkrish.jklol.models.bayesnet.DirichletFactor;


/**
 * Static utility methods for mathematical operations on factors.
 * @author jayant
 *
 */
public class FactorMath {

	/**
	 * Multiplies together a list of factors. The density p(E) of event E in the returned 
	 * factor is proportional to p_1(E) * p_2(E) * ... * p_n(E), where p_i(E) is the density of E 
	 * in the ith factor.   
	 * @param factors
	 * @return
	 */
	public static Factor product(List<Factor> factors) {
		List<DiscreteFactor> discreteFactors = new ArrayList<DiscreteFactor>();
		List<DirichletFactor> dirichletFactors = new ArrayList<DirichletFactor>();
		for (Factor f : factors) {
			if (f instanceof DiscreteFactor) {
				discreteFactors.add((DiscreteFactor) f);
			} else if (f instanceof DirichletFactor){
				dirichletFactors.add((DirichletFactor) f);
			}
		}

		if (discreteFactors.size() > 0 && dirichletFactors.size() > 0) {
			throw new UnsupportedOperationException("Cannot multiply discrete factors with dirichlet factors.");
		}
		
		if (discreteFactors.size() > 0) {
			return TableFactor.productFactor(discreteFactors);
		}
		return DirichletFactor.productFactor(dirichletFactors);
	}

	public static Factor product(Factor f1, Factor f2) {
		if (f1 instanceof DiscreteFactor && f2 instanceof DiscreteFactor) {
			return TableFactor.productFactor((DiscreteFactor) f1, (DiscreteFactor) f2);
		} else if (f1 instanceof DirichletFactor && f2 instanceof DirichletFactor) {
			return DirichletFactor.productFactor((DirichletFactor) f1, (DirichletFactor) f2);
		}

		throw new UnsupportedOperationException("Cannot multiply: " + f1 + " with: " + f2);
	}

	private FactorMath() {
		// Prevent instantiation.
	}
}
