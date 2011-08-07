import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import com.google.common.collect.Lists;
import com.jayantkrish.jklol.models.DiscreteVariable;
import com.jayantkrish.jklol.models.Factor;
import com.jayantkrish.jklol.models.MixtureFactor;
import com.jayantkrish.jklol.models.TableFactor;
import com.jayantkrish.jklol.models.VariableNumMap;

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
	
	public void setUp() {
		v = new DiscreteVariable("True/False",
				Arrays.asList(new String[] {"T", "F"}));
	
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

		h = new TableFactor(new VariableNumMap(Arrays.asList(new Integer[] {0, 2, 4}),
				Arrays.asList(new DiscreteVariable[] {v, v, v})));
		
		h.setWeightList(Arrays.asList(new String[] {"T", "F", "F"}), 3.0);
		h.setWeightList(Arrays.asList(new String[] {"T", "F", "T"}), 4.0);
		h.setWeightList(Arrays.asList(new String[] {"F", "T", "F"}), 6.0);
		h.setWeightList(Arrays.asList(new String[] {"T", "T", "T"}), 2.0);
		h.setWeightList(Arrays.asList(new String[] {"T", "T", "F"}), 2.0);
		
		List<Factor> factors = Lists.newArrayList();
		factors.addAll(Arrays.asList(new TableFactor[] {f, g, h}));
		m = MixtureFactor.createMixtureFactor(factors, Arrays.asList(new Double[] {1.0, 2.0, 3.0}), true);
	}
	
	public void testGetVars() {
		assertEquals(Arrays.asList(new Integer[] {0, 1, 2, 3, 4}), m.getVars().getVariableNums());
	}
	
	public void testGetUnnormalizedProbability() {
		assertEquals(3.0, h.getUnnormalizedProbability(
				Arrays.asList(new String[] {"T", "F", "F"})));
		assertEquals(3.0 + 2.0 * 4.0 + 3.0 * 2.0, m.getUnnormalizedProbability(
				Arrays.asList(new String[] {"T", "F", "T", "T", "T"})));
		assertEquals(4.0 + 2.0 * 5.0 + 3.0 * 2.0, m.getUnnormalizedProbability(
				Arrays.asList(new String[] {"T", "T", "T", "F", "T"})));
		assertEquals(3.0 + 0.0 + 3.0 * 4.0, m.getUnnormalizedProbability(
				Arrays.asList(new String[] {"T", "F", "F", "F", "T"})));
	}
	
	public void testMarginalize1() {
		Factor marginal = m.marginalize(Arrays.asList(new Integer[] {0}));
		assertEquals(6.0 + 2.0 * 11.0 + 3.0 * 8.0,
				marginal.getUnnormalizedProbability(Arrays.asList(
				new String[] {"T", "T", "F", "F"})));
	}
	
	public void testMarginalize2() {
		Factor marginal = m.marginalize(Arrays.asList(new Integer[] {1, 2, 3, 4}));
		assertEquals(7.0 + 2.0 * 9.0 + 3.0 * 11.0,
				marginal.getUnnormalizedProbability(Arrays.asList(
				new String[] {"T"})));
		assertEquals(3.0 + 2.0 * 11.0 + 3.0 * 6.0,
				marginal.getUnnormalizedProbability(Arrays.asList(
				new String[] {"F"})));
	}
	
	public void testMaxMarginalize() {
		Factor marginal = m.maxMarginalize(Arrays.asList(new Integer[] {1, 3, 4}));
		assertEquals(7.0 + 2.0 * 9.0 + 3.0 * 11.0,
				marginal.getUnnormalizedProbability(Arrays.asList(
				new String[] {"T"})));
		assertEquals(3.0 + 2.0 * 11.0 + 3.0 * 6.0,
				marginal.getUnnormalizedProbability(Arrays.asList(
				new String[] {"F"})));
		
		// max x_2, x_3, x_4 f(x_1, x_2, x_4) + f(x_1, x_3, x_4)
		// = max x_4 (max x_2 f(x_1, x_2, x_4) + max x_3 f(x_1, x_3, x_4))
	}
}
