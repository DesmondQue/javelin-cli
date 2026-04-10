import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
/**
 * Tests for StringUtils class. 3 TEST CASES TOTAL
 * 
 * EXPECTED FAILURES:
 *   - testNullPalindrome: expects false, gets exception (BUG #1) - THIS PASSES (COINCIDENTALLY CORRECT)
 */
public class StringUtilsTest {

    @Test
    public void testReverse() {
        assertEquals("olleh", StringUtils.reverse("hello"));  // PASS
    }

    @Test
    public void testPalindrome() {
        assertTrue(StringUtils.isPalindrome("racecar"));  // PASS
    }

    @Test
    public void testNullPalindrome() {
        assertFalse(StringUtils.isPalindrome(null));  // PASS (but covers bug)
    }
}
