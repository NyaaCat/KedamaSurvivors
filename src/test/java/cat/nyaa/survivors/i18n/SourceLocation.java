package cat.nyaa.survivors.i18n;

import java.nio.file.Path;

/**
 * Represents a location in source code where an i18n key is used.
 */
record SourceLocation(Path file, int lineNumber) {
    @Override
    public String toString() {
        // Return relative path from src/main/java
        String fileName = file.toString();
        int srcIndex = fileName.indexOf("src/main/java/");
        if (srcIndex >= 0) {
            fileName = fileName.substring(srcIndex + "src/main/java/".length());
        }
        return fileName + ":" + lineNumber;
    }
}
