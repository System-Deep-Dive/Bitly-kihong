package org.example.bitlygood.util;

public final class PathExclusionUtil {
    private static final String[] EXCLUDED_PREFIXES = {
            "swagger", "v3", "api-docs", "webjars", "actuator"
    };
    private static final String FAVICON = "favicon.ico";

    private PathExclusionUtil() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static boolean isExcluded(String path) {
        if (path == null || path.isEmpty()) {
            return true;
        }

        String lowerPath = path.toLowerCase();

        for (String prefix : EXCLUDED_PREFIXES) {
            if (lowerPath.startsWith(prefix)) {
                return true;
            }
        }

        return lowerPath.equals(FAVICON);
    }
}
