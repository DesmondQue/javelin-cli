import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for MathHelper — never directly references Calculator.
 * 
 * These tests transitively execute Calculator.divide() via MathHelper.average()
 * and MathHelper.dividesEvenly(). JaCoCo should report coverage on Calculator
 * lines for these tests, so test scoping should include MathHelperTest
 * when Calculator is in the fault region.
 */
public class MathHelperTest {

    private final MathHelper helper = new MathHelper();

    @Test
    public void testAverageExact() {
        // average(4, 6) → add(4,6)=10 → divide(10,2) → 5+1=6 (buggy)
        // This FAILS: expects 5 but gets 6
        assertEquals(5, helper.average(4, 6));
    }

    @Test
    public void testAveragePositive() {
        // CC: average(10, 20) → divide(30,2) → 15+1=16 (buggy), but 16 > 0
        assertTrue(helper.average(10, 20) > 0);
    }

    @Test
    public void testDividesEvenly() {
        // dividesEvenly(10, 5) → divide(10,5)=3 (buggy 2+1), multiply(3,5)=15, 15==10 → false
        // Bug makes this return false when it should be true
        assertFalse(helper.dividesEvenly(10, 5));
    }
}
