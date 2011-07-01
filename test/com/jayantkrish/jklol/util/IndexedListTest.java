import com.jayantkrish.jklol.util.IndexedList;
import junit.framework.*;

import java.util.NoSuchElementException;

public class IndexedListTest extends TestCase {

    private IndexedList<Double> list;

    public void setUp() {
	list = new IndexedList<Double>();
	list.add(23.0);
	list.add(7.2);
    }

    public void testContains() {
	assertTrue(list.contains(7.2));
	assertTrue(list.contains(23.0));
	assertFalse(list.contains(null));
	assertFalse(list.contains(7.0));
    }

    public void testAdd() {
	list.add(23.0);
	assertEquals(list.size(), 2);

	list.add(null);
	assertEquals(list.size(), 3);
    }

    public void testSize() {
	assertEquals(list.size(), 2);
    }

    public void testGetIndex() {
	assertEquals(list.getIndex(23.0), 0);
	try {
	    list.getIndex(2345.0);
	} catch (NoSuchElementException e) {
	    return;
	}
	fail("Expected NoSuchElementException!");
    }

    public void testGet() {
	assertEquals(list.get(0), 23.0);
	assertEquals(list.get(1), 7.2);

	try {
	    list.get(2);
	} catch (IndexOutOfBoundsException e) {
	    return;
	}
	fail("Expected IndexOutOfBoundsException!");
    }

    public void testGetNegative() {
	try {
	    list.get(-1);
	} catch (IndexOutOfBoundsException e) {
	    return;
	}
	fail("Expected IndexOutOfBoundsException!");
    }}
