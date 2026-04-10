/**
 * Helper class that delegates to Calculator.
 * Used to test that test scoping correctly includes tests
 * that transitively cover fault-region classes.
 */
public class MathHelper {

    private final Calculator calc = new Calculator();

    /**
     * Computes the average of two integers using Calculator.divide().
     * Since Calculator.divide() has the off-by-one bug, this will
     * return (a+b)/2 + 1 instead of (a+b)/2.
     */
    public int average(int a, int b) {
        int sum = calc.add(a, b);
        return calc.divide(sum, 2);
    }

    /**
     * Checks if a divides b evenly, using Calculator operations.
     */
    public boolean dividesEvenly(int a, int b) {
        int quotient = calc.divide(a, b);
        int product = calc.multiply(quotient, b);
        return product == a;
    }
}
