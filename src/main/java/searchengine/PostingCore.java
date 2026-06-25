package searchengine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Local implementation of Job-2: filteredSourceFile -> postingFile. */
public class PostingCore {
    public static final String SEP = "\t";

    public static void build(Path filteredFile, Path postingFile) throws IOException {
        TextCleaner cleaner = new TextCleaner();
        Map<String, List<String>> postingsByTerm = new TreeMap<>();
        try (BufferedReader reader = Files.newBufferedReader(filteredFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) {
                    continue;
                }
                int did;
                try {
                    did = Integer.parseInt(parts[0]);
                } catch (NumberFormatException e) {
                    continue;
                }
                String filename = parts[1];
                String tokenLine = parts[2].trim();
                if (tokenLine.isEmpty()) {
                    continue;
                }
                String[] tokens = tokenLine.split("\\s+");
                Map<String, List<Integer>> positionsByTerm = new LinkedHashMap<>();
                for (int i = 0; i < tokens.length; i++) {
                    if (!tokens[i].isEmpty()) {
                        String indexTerm = cleaner.toIndexTerm(tokens[i]);
                    if (!indexTerm.isEmpty()) {
                        positionsByTerm.computeIfAbsent(indexTerm, k -> new ArrayList<>()).add(i);
                    }
                    }
                }
                for (Map.Entry<String, List<Integer>> entry : positionsByTerm.entrySet()) {
                    String encoded = encodePosting(did, filename, tokens.length, entry.getValue().size(), entry.getValue());
                    postingsByTerm.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(encoded);
                }
            }
        }

        Path parent = postingFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(postingFile, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<String>> entry : postingsByTerm.entrySet()) {
                writer.write(entry.getKey());
                writer.write(SEP);
                writer.write(String.join(";", entry.getValue()));
                writer.newLine();
            }
        }
    }

    public static String encodePosting(int did, String filename, int docLen, int tfCount, List<Integer> positions) {
        return did + ":" + escape(filename) + ":" + docLen + ":" + tfCount + ":" + joinPositions(positions);
    }

    public static RawPosting parsePosting(String text) {
        String[] parts = text.split(":", 5);
        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid posting: " + text);
        }
        int did = Integer.parseInt(parts[0]);
        String filename = unescape(parts[1]);
        int docLen = Integer.parseInt(parts[2]);
        int tfCount = Integer.parseInt(parts[3]);
        List<Integer> positions = new ArrayList<>();
        if (!parts[4].isEmpty()) {
            String[] posParts = parts[4].split(",");
            for (String pos : posParts) {
                if (!pos.isEmpty()) {
                    positions.add(Integer.parseInt(pos));
                }
            }
        }
        Collections.sort(positions);
        return new RawPosting(did, filename, docLen, tfCount, positions);
    }

    public static String joinPositions(List<Integer> positions) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < positions.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(positions.get(i));
        }
        return builder.toString();
    }

    private static String escape(String s) {
        return s.replace("%", "%25").replace(":", "%3A").replace(";", "%3B").replace("\t", "%09");
    }

    private static String unescape(String s) {
        return s.replace("%09", "\t").replace("%3B", ";").replace("%3A", ":").replace("%25", "%");
    }

    public static class RawPosting {
        public final int did;
        public final String filename;
        public final int docLen;
        public final int tfCount;
        public final List<Integer> positions;

        public RawPosting(int did, String filename, int docLen, int tfCount, List<Integer> positions) {
            this.did = did;
            this.filename = filename;
            this.docLen = docLen;
            this.tfCount = tfCount;
            this.positions = new ArrayList<>(positions);
            Collections.sort(this.positions);
        }
    }
}
