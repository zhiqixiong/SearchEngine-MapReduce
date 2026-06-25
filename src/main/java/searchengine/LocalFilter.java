package searchengine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalFilter {
    public static void filter(Path rawDataFile, Path filteredFile) throws IOException {
        TextCleaner cleaner = new TextCleaner();
        Path parent = filteredFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedReader reader = Files.newBufferedReader(rawDataFile, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(filteredFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) {
                    continue;
                }
                String tokenLine = cleaner.cleanToTokenLine(parts[2]);
                if (!tokenLine.isEmpty()) {
                    writer.write(parts[0] + "\t" + parts[1] + "\t" + tokenLine);
                    writer.newLine();
                }
            }
        }
    }
}
