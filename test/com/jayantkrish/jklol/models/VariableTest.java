import java.util.Arrays;
import java.util.NoSuchElementException;

import junit.framework.TestCase;

import com.jayantkrish.jklol.models.Variable;

public class VariableTest extends TestCase {

	private Variable<String> v;

	public void setUp() {
		v = new Variable<String>("foo", Arrays.asList(new String[] {"bar", "baz"}));
	}

	public void testGetValue() {
		assertEquals(v.getValue(0), "bar");
		assertEquals(v.getValue(1), "baz");

		try {
			v.getValue(2);
		} catch (IndexOutOfBoundsException e) {
			return;
		}
		fail("Expected IndexOutOfBoundsException!");
	}

	public void testGetValueIndex() {
		assertEquals(v.getValueIndex("bar"), 0);
		assertEquals(v.getValueIndex("baz"), 1);
		try {
			assertEquals(v.getValueIndex("foo"), 0);
		} catch (NoSuchElementException e) {
			return;
		}
		fail("Expected NoSuchElementException");
	}

	public void testGetValueIndexObject() {
		assertEquals(v.getValueIndexObject("bar"), 0);
		assertEquals(v.getValueIndexObject("baz"), 1);
		try {
			v.getValueIndexObject(null);
		} catch (NoSuchElementException e) {
			return;
		}
		fail("Expected NoSuchElementException");
	}

}