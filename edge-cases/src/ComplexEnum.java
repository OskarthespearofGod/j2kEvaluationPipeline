public enum ComplexEnum {
    LOW(1, "low priority") {
        @Override
        public ComplexEnum escalate() {
            return MEDIUM;
        }

        @Override
        public boolean isUrgent() {
            return false;
        }
    },
    MEDIUM(5, "medium priority") {
        @Override
        public ComplexEnum escalate() {
            return HIGH;
        }

        @Override
        public boolean isUrgent() {
            return false;
        }
    },
    HIGH(10, "high priority") {
        @Override
        public ComplexEnum escalate() {
            return HIGH;
        }

        @Override
        public boolean isUrgent() {
            return true;
        }
    };

    private final int value;
    private final String description;

    ComplexEnum(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public abstract ComplexEnum escalate();

    public abstract boolean isUrgent();

    public static ComplexEnum fromValue(int value) {
        for (ComplexEnum type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown value: " + value);
    }
}
