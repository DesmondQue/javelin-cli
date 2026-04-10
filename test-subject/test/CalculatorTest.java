import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for Calculator class — demonstrates the CC test problem.
 *
 * 27 tests total:
 *   - 19 strong passing tests (correct methods, exact assertEquals assertions)
 *   - 1 strong passing test (testDivideByZero, assertThrows)
 *   - 1 failing test (testDivideExact — catches the off-by-one bug)
 *   - 6 CC passing tests (exercise buggy divide() with weak assertTrue assertions)
 *
 * The failing test calls add/subtract/multiply in its setup, so those methods'
 * lines are included in the fault-suspected region. Standard Ochiai then
 * incorrectly ranks those non-buggy lines ABOVE the buggy divide line, because
 * the 6 CC tests inflate passed(s) for divide's lines. Ochiai-MS fixes this
 * by discounting CC tests via their low mutation scores.
 */
public class CalculatorTest {

    private final Calculator calc = new Calculator();

    // ===== STRONG PASSING TESTS (exact assertions → high mutation score) =====

    @Test
    public void testAdd() {
        assertEquals(5, calc.add(2, 3));
    }

    @Test
    public void testAddNegative() {
        assertEquals(-1, calc.add(2, -3));
    }

    @Test
    public void testAddZero() {
        assertEquals(7, calc.add(7, 0));
    }

    @Test
    public void testSubtract() {
        assertEquals(2, calc.subtract(5, 3));
    }

    @Test
    public void testSubtractNegative() {
        assertEquals(8, calc.subtract(5, -3));
    }

    @Test
    public void testMultiply() {
        assertEquals(15, calc.multiply(3, 5));
    }

    @Test
    public void testMultiplyByZero() {
        assertEquals(0, calc.multiply(5, 0));
    }

    @Test
    public void testModulo() {
        assertEquals(1, calc.modulo(10, 3));
    }

    @Test
    public void testPower() {
        assertEquals(8, calc.power(2, 3));
    }

    @Test
    public void testPowerZero() {
        assertEquals(1, calc.power(5, 0));
    }

    @Test
    public void testAbsolute() {
        assertEquals(5, calc.absolute(-5));
    }

    @Test
    public void testAbsolutePositive() {
        assertEquals(3, calc.absolute(3));
    }

    @Test
    public void testNegate() {
        assertEquals(-5, calc.negate(5));
    }

    @Test
    public void testNegateNegative() {
        assertEquals(3, calc.negate(-3));
    }

    @Test
    public void testSquare() {
        assertEquals(25, calc.square(5));
    }

    @Test
    public void testIsEven() {
        assertTrue(calc.isEven(4));
    }

    @Test
    public void testIsOdd() {
        assertFalse(calc.isEven(5));
    }

    @Test
    public void testMax() {
        assertEquals(10, calc.max(5, 10));
    }

    @Test
    public void testMin() {
        assertEquals(5, calc.min(5, 10));
    }

    @Test
    public void testDivideByZero() {
        assertThrows(ArithmeticException.class, () -> calc.divide(10, 0));
    }

    // ===== FAILING TEST (catches the bug with exact assertion) =====
    // Also calls add/subtract/multiply to widen the fault-suspected region,
    // so those non-buggy methods' lines are included in the spectrum.

    @Test
    public void testDivideExact() {
        int a = calc.multiply(2, 5);   // a = 10 (covers multiply lines)
        int b = calc.subtract(5, 3);   // b = 2  (covers subtract lines)
        int c = calc.add(a, 0);        // c = 10 (covers add lines)
        // FAILS: divide(10, 2) returns 6 (buggy 5+1) instead of 5
        assertEquals(5, calc.divide(c, b));
    }

    // ===== CC TESTS (cover buggy divide() but pass due to weak assertions) =====
    // These tests all execute the buggy `return result + 1` line but their
    // assertions are too weak to detect the off-by-one error.

    @Test
    public void testDividePositiveResult() {
        // CC: divide(20,4) returns 6 (buggy 5+1), but 6 > 0 is true
        assertTrue(calc.divide(20, 4) > 0);
    }

    @Test
    public void testDivideNotNegative() {
        // CC: divide(100,3) returns 34 (buggy 33+1), but 34 >= 0 is true
        assertTrue(calc.divide(100, 3) >= 0);
    }

    @Test
    public void testDivideLessThanDividend() {
        // CC: divide(50,5) returns 11 (buggy 10+1), but 11 < 50 is true
        assertTrue(calc.divide(50, 5) < 50);
    }

    @Test
    public void testDivideBounded() {
        // CC: divide(30,3) returns 11 (buggy 10+1), but 11 <= 30 is true
        assertTrue(calc.divide(30, 3) <= 30);
    }

    @Test
    public void testDivideNonZero() {
        // CC: divide(7,2) returns 4 (buggy 3+1), but 4 != 0 is true
        assertTrue(calc.divide(7, 2) != 0);
    }

    @Test
    public void testDivideConsistency() {
        // CC: divide(12,4) returns 4 (buggy 3+1), but |4| < 100 is true
        assertTrue(Math.abs(calc.divide(12, 4)) < 100);
    }
}

