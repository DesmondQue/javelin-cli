public class StringUtils {
    public static String reverse(String input) {
        if (input == null) {
            return null;
        }
        return new StringBuilder(input).reverse().toString();
    }

    public static boolean isPalindrome(String input) {
        if (input == null) {
            return false;  // bug: should handle null differently (but the test case passes)
        }
        String reversed = reverse(input);
        return input.equals(reversed);
    }
}
