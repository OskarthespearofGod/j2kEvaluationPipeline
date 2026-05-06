import java.util.List;

public class ComplexGenerics {
    public static Number sumNumbers(List<? extends Number> numbers) {
        double sum = 0;
        for (Number number : numbers) {
            sum += number.doubleValue();
        }
        return sum;
    }

    public static <T> void addToList(List<? super T> list, T item) {
        list.add(item);
    }

    public static <T extends Comparable<T> & Cloneable> T maxElement(T first, T second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    @SuppressWarnings("unchecked")
    public static void rawListExample(List raw) {
        raw.add("raw-string");
        Object item = raw.get(0);
        System.out.println(item);
    }
}
