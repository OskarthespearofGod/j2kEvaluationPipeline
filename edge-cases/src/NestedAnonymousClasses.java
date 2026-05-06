import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public class NestedAnonymousClasses {
    public static Transformer buildTransformer(final String prefix) {
        return new Transformer() {
            private final Comparator<String> sorter = new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    return Integer.compare(a.length(), b.length());
                }
            };

            @Override
            public String transform(String input) {
                List<String> values = Arrays.asList(prefix, input);
                Collections.sort(values, sorter);
                return prefix + "_" + input.toLowerCase();
            }
        };
    }

    public static Predicate<String> buildPredicate(final int minLength) {
        return new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return s.length() >= minLength;
            }
        };
    }

    public interface Transformer {
        String transform(String input);
    }
}
