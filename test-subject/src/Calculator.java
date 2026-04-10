/**
 * Calculator class with one intentional bug for demonstrating the
 * coincidentally correct (CC) test problem in SBFL.
 *
 * BUG: divide() has an off-by-one error — returns (a/b)+1 instead of a/b.
 *
 * Six CC tests exercise divide() with weak assertions that don't catch
 * the off-by-one error, pushing the buggy line DOWN in standard Ochiai.
 * Ochiai-MS detects these CC tests via low mutation scores and discounts
 * their contribution, correctly ranking the buggy line higher.
 */
public class Calculator {

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        return a - b;
    }

    public int multiply(int a, int b) {
        return a * b;
    }

    // BUG: off-by-one error — returns (a/b)+1 instead of a/b
    public int divide(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero");
        }
        int result = a / b;
        return result + 1;  // BUG: the +1 is wrong
    }

    public int modulo(int a, int b) {
        return a % b;
    }

    public int power(int base, int exponent) {
        if (exponent < 0) {
            throw new IllegalArgumentException("Negative exponent");
        }
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }

    public int absolute(int a) {
        if (a < 0) {
            return -a;
        }
        return a;
    }

    public int negate(int a) {
        return -a;
    }

    public int square(int a) {
        return a * a;
    }

    public boolean isEven(int a) {
        return a % 2 == 0;
    }

    public int max(int a, int b) {
        return (a > b) ? a : b;
    }

    public int min(int a, int b) {
        return (a < b) ? a : b;
    }
}

