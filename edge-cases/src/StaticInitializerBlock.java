import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class StaticInitializerBlock {
    public static final String APP_NAME = "j2k-eval";
    public static int instanceCount = 0;
    public static final Map<String, Integer> PRIORITY_MAP;
    public static final Registry REGISTRY = Registry.INSTANCE;

    static {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("LOW", 1);
        m.put("MEDIUM", 5);
        m.put("HIGH", 10);
        PRIORITY_MAP = Collections.unmodifiableMap(m);
    }

    public static class Registry {
        public static final Registry INSTANCE = new Registry();

        private Registry() {
        }

        public String getName() {
            return "default-registry";
        }
    }
}
