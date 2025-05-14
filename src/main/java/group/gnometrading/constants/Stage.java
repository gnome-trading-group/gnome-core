package group.gnometrading.constants;

public enum Stage {
    DEV("dev"),
    STAGING("staging"),
    PROD("prod");

    private final String stageName;

    Stage(String stageName) {
        this.stageName = stageName;
    }

    public String getStageName() {
        return stageName;
    }

    public static Stage fromStageName(final String stageName) {
        for (Stage stage : Stage.values()) {
            if (stageName.equalsIgnoreCase(stage.getStageName())) {
                return stage;
            }
        }
        throw new IllegalArgumentException("Invalid stage name: " + stageName);
    }
}
