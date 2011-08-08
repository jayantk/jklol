import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.MixtureFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;
import com.jayantkrish.jklol.util.Assignment;

/**
 * Unit tests for {@link MixtureFactor}.
 * 
 * @author jayant
 */
public class MixtureFactorTest extends TestCase {

	private TableFactor f;
	private TableFactor g;
	private TableFactor h;
	
	private MixtureFactor m;
	
	private DiscreteVariable v;
	
	private static final double EXPECTED_PARTITION_FUNCTION = 10.0 + 2.0 * 20.0 + 3.0 * 15.0;
	
	public void setUp() {
		v = new DiscreteVariable("True/False",
				Arrays.asList(new String[] {"T", "F"}));

		// Max marginal on 0, 
		// F : 2 + 12 + 18 = 32
		// T : 4 + 10 + 9 = 23 

		f = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 1}),
				Arrays.asList(new DiscreteVariable[] {v, v})));
		f.setWeightList(Arrays.asList(new String[] {"F", "F"}), 1.0);
		f.setWeightList(Arrays.asList(new String[] {"F", "T"}), 2.0);
		f.setWeightList(Arrays.asList(new String[] {"T", "F"}), 3.0);
		f.setWeightList(Arrays.asList(new String[] {"T", "T"}), 4.0);
		
		g = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 2, 3}),
				Arrays.asList(new DiscreteVariable[] {v, v, v})));
		
		g.setWeightList(Arrays.asList(new String[] {"F", "F", "F"}), 2.0);
		g.setWeightList(Arrays.asList(new String[] {"F", "F", "T"}), 3.0);
		g.setWeightList(Arrays.asList(new String[] {"F", "T", "F"}), 6.0);
		g.setWeightList(Arrays.asList(new String[] {"T", "T", "T"}), 4.0);
		g.setWeightList(Arrays.asList(new String[] {"T", "T", "F"}), 5.0);

		h = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 4}),
				Arrays.asList(new DiscreteVariable[] {v, v})));
		
		h.setWeightList(Arrays.asList(new String[] {"F", "F"}), 6.0);
		h.setWeightList(Arrays.asList(new String[] {"F", "T"}), 4.0);
		h.setWeightList(Arrays.asList(new String[] {"T", "F"}), 3.0);
		h.setWeightList(Arrays.asList(new String[] {"T", "T"}), 2.0);
		
		List<Factor> factors = Lists.newArrayList();
		factors.addAll(Arrays.asList(new TableFactor[] {f, g, h}));
		m = MixtureFactor.create(factors, Arrays.asList(new Double[] {1.0, 2.0, 3.0}));
	}
	
	public void testCreateInvalidFactors() {
		Factor factor = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 2, 4}),
				Arrays.asList(new DiscreteVariable[] {v, v, v})));
		List<Factor> factors = Lists.newArrayList();
		factors.addAll(Arrays.asList(f, g, factor));
		try {
			MixtureFactor.create(factors, Arrays.asList(new Double[] {1.0, 1.0, 1.0}));			
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException.");
	}
	
	public void testCreateInvalidWeights() {
		List<Factor> factors = Lists.newArrayList();
		factors.addAll(Arrays.asList(f, g, h));
		try {
			MixtureFactor.create(factors, Arrays.asList(new Double[] {1.0, 1.0, -1.0}));			
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException.");
	}
	
	public void testGetVars() {
		assertEquals(Arrays.asList(new Integer[] {0, 1, 2, 3, 4}), m.getVars().getVariableNums());
	}
	
	public void testGetUnnormalizedProbability() {
		assertEquals(3.0 + 2.0 * 4.0 + 3.0 * 2.0, m.getUnnormalizedProbability(
				Arrays.asList(new String[] {"T", "F", "T", "T", "T"})));
		assertEquals(4.0 + 2.0 * 5.0 + 3.0 * 2.0, m.getUnnormalizedProbability(
				Arrays.asList(new String[] {"T", "T", "T", "F", "T"})));
		assertEquals(3.0 + 0.0 + 3.0 * 3.0, m.getUnnormalizedProbability(
				Arrays.asList(new String[] {"T", "F", "F", "F", "F"})));
		assertEquals(2.0 + 2.0 * 6.0 + 3.0 * 4.0, 
				m.getUnnormalizedProbability("F", "T", "T", "F", "T"));
	}
	
	public void testGetPartitionFunction() {
		assertEquals(EXPECTED_PARTITION_FUNCTION, m.getPartitionFunction());
	}
		
	public void testMarginalizeInvalid1() {
		try {
			m.marginalize(Arrays.asList(new Integer[] {0}));
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException.");
	}
	
	public void testMarginalizeInvalid2() {
		try {
			m.marginalize(Arrays.asList(new Integer[] {1, 2}));
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException.");
	}
	
	public void testMarginalize1() {
		Factor marginal = m.marginalize(Arrays.asList(new Integer[] {0, 1, 3, 4}));
		assertEquals(2.0 * 5.0,
				marginal.getUnnormalizedProbability("F"));
		assertEquals(2.0 * 15.0,
				marginal.getUnnormalizedProbability("T"));
		
		assertEquals(EXPECTED_PARTITION_FUNCTION, marginal.getPartitionFunction());
	}
	
	public void testMarginalize2() {
		Factor marginal = m.marginalize(Arrays.asList(new Integer[] {1, 2, 3, 4}));
		assertEquals(7.0 + 2.0 * 9.0 + 3.0 * 5.0,
				marginal.getUnnormalizedProbability("T"));
		assertEquals(3.0 + 2.0 * 11.0 + 3.0 * 10.0,
				marginal.getUnnormalizedProbability("F"));
		
		assertEquals(EXPECTED_PARTITION_FUNCTION, marginal.getPartitionFunction());
	}
	
	public void testMaxMarginalize1() {
		Factor marginal = m.maxMarginalize(Arrays.asList(new Integer[] {0, 2, 3, 4}));
		assertEquals(32.0, marginal.getUnnormalizedProbability("T"));
		assertEquals(31.0, marginal.getUnnormalizedProbability("F"));
	}
	
	public void testMaxMarginalize2() {
		Factor marginal = m.maxMarginalize(Arrays.asList(new Integer[] {1, 2, 3, 4}));
		assertEquals(23.0, marginal.getUnnormalizedProbability("T"));
		assertEquals(32.0, marginal.getUnnormalizedProbability("F"));
	}
	
	public void testProduct1() {
		TableFactor message = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0}),
				Arrays.asList(new DiscreteVariable[] {v})));
		message.setWeightList(Arrays.asList(new String[] {"F"}), 1.0);
		message.setWeightList(Arrays.asList(new String[] {"T"}), 2.0);
		
		Factor newMixture = m.product(message);
		assertEquals(2.0 * (3.0 + 2.0 * 4.0 + 3.0 * 2.0), 
				newMixture.getUnnormalizedProbability("T", "F", "T", "T", "T"));
		assertEquals(2.0 * (4.0 + 2.0 * 5.0 + 3.0 * 2.0), 
				newMixture.getUnnormalizedProbability("T", "T", "T", "F", "T"));
		assertEquals(2.0 + 2.0 * 6.0 + 3.0 * 4.0, 
				newMixture.getUnnormalizedProbability("F", "T", "T", "F", "T"));
	}
	
	public void testProduct2() {
		TableFactor message = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {3}),
				Arrays.asList(new DiscreteVariable[] {v})));
		message.setWeightList(Arrays.asList(new String[] {"F"}), 2.0);
		message.setWeightList(Arrays.asList(new String[] {"T"}), 3.0);

		Factor newMixture = m.product(message);
		assertEquals(3.0 + 2.0 * 4.0 * 3.0 + 3.0 * 2.0, 
				newMixture.getUnnormalizedProbability("T", "F", "T", "T", "T"));
		assertEquals(4.0 + 2.0 * 5.0 * 2.0 + 3.0 * 2.0, 
				newMixture.getUnnormalizedProbability("T", "T", "T", "F", "T"));
		assertEquals(2.0 + 2.0 * 6.0 * 2.0 + 3.0 * 4.0, 
				newMixture.getUnnormalizedProbability("F", "T", "T", "F", "T"));
	}
	
	public void testProductInvalid1() {
		Factor message = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 1}),
				Arrays.asList(new DiscreteVariable[] {v, v})));
		try {
			m.product(message);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException.");
	}
	
	public void testProductInvalid2() {
		Factor message = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 1}),
				Arrays.asList(new DiscreteVariable[] {v, v})));
		try {
			m.product(message);
		} catch (IllegalArgumentException e) {
			return;
		}
		fail("Expected IllegalArgumentException.");
	}
	
	public void testConditional1() {
		Factor conditional = m.conditional(new Assignment(
				Arrays.asList(new Integer[] {1, 3}), Arrays.asList(new String[] {"T", "F"})));

		assertEquals(4.0 + 2.0 * 5.0 + 3.0 * 2.0, 
				conditional.getUnnormalizedProbability("T", "T", "T", "F", "T"));
		assertEquals(2.0 * 2.0 + 3.0 * 6.0, 
				conditional.getUnnormalizedProbability("F", "F", "F", "F", "F"));
		assertEquals(4.0 + 3.0 * 2.0, 
				conditional.getUnnormalizedProbability("T", "T", "T", "T", "T"));
	}
	
	public void testConditional2() {
		Factor conditional = m.conditional(new Assignment(
				Arrays.asList(new Integer[] {0, 1}), Arrays.asList(new String[] {"T", "T"})));

		assertEquals(4.0 + 2.0 * 5.0 + 3.0 * 2.0, 
				conditional.getUnnormalizedProbability("T", "T", "T", "F", "T"));
		assertEquals(0.0, 
				conditional.getUnnormalizedProbability("F", "F", "F", "F", "F"));
		assertEquals(4.0 * 2.0 + 2.0 * 3.0, 
				conditional.getUnnormalizedProbability("T", "F", "T", "T", "T"));
	}
	
	public void testCoerceToDiscrete() {
		Factor discreteFactor = m.coerceToDiscrete();
		assertEquals(3.0 + 2.0 * 4.0 + 3.0 * 2.0, discreteFactor.getUnnormalizedProbability(
				Arrays.asList(new String[] {"T", "F", "T", "T", "T"})));
		assertEquals(4.0 + 2.0 * 5.0 + 3.0 * 2.0, discreteFactor.getUnnormalizedProbability(
				Arrays.asList(new String[] {"T", "T", "T", "F", "T"})));
		assertEquals(3.0 + 0.0 + 3.0 * 3.0, discreteFactor.getUnnormalizedProbability(
				Arrays.asList(new String[] {"T", "F", "F", "F", "F"})));
		assertEquals(2.0 + 2.0 * 6.0 + 3.0 * 4.0, 
				discreteFactor.getUnnormalizedProbability("F", "T", "T", "F", "T"));
		
		assertEquals(EXPECTED_PARTITION_FUNCTION, discreteFactor.getPartitionFunction());
	}
}
