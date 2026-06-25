package searchengine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class InvertedIndexCore {
    private static class RawPosting {
        final int did;
        final int totalWords;
        final List<Integer> positions;

        RawPosting(int did, int totalWords, List<Integer> positions) {
            this.did = did;
            this.totalWords = totalWords;
            this.positions = positions;
        }

        int tfCount() {
            return positions.size();
        }
    }

    public static void build(Path filteredFile, Path indexFile, int docCount) throws IOException {
        Map<String, List<RawPosting>> raw = readFiltered(filteredFile);
        Path parent = indexFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(indexFile, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, List<RawPosting>> entry : raw.entrySet()) {
                writer.write(toIndexLine(entry.getKey(), entry.getValue(), docCount));
                writer.newLine();
            }
        }
    }

    public static Map<String, List<Posting>> loadIndex(Path indexFile) throws IOException {
        Map<String, List<Posting>> index = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(indexFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                IndexRecord record = parseIndexLine(line);
                if (record != null) {
                    index.put(record.term, record.postings);
                }
            }
        }
        return index;
    }

    public static Map<Integer, Document> loadDocuments(Path filteredFile) throws IOException {
        Map<Integer, Document> docs = new HashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(filteredFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) {
                    continue;
                }
                int did = Integer.parseInt(parts[0]);
                List<String> tokens = new ArrayList<>();
                String tokenLine = parts[2].trim();
                if (!tokenLine.isEmpty()) {
                    Collections.addAll(tokens, tokenLine.split("\\s+"));
                }
                docs.put(did, new Document(did, parts[1], tokens));
            }
        }
        return docs;
    }

    public static IndexRecord parseIndexLine(String line) {
        String[] parts = line.split("\t", 2);
        if (parts.length < 2) {
            return null;
        }

        List<Posting> postings = new ArrayList<>();
        String[] postingParts = parts[1].split(";");
        for (String postingText : postingParts) {
            String[] fields = postingText.split(":", 3);
            if (fields.length < 3) {
                continue;
            }
            int did = Integer.parseInt(fields[0]);
            double rank = Double.parseDouble(fields[1]);
            List<Integer> positions = new ArrayList<>();
            if (!fields[2].trim().isEmpty()) {
                String[] posParts = fields[2].split(",");
                for (String pos : posParts) {
                    positions.add(Integer.parseInt(pos));
                }
            }
            postings.add(new Posting(did, rank, positions));
        }
        postings.sort((a, b) -> Double.compare(b.getRank(), a.getRank()));
        return new IndexRecord(parts[0], postings);
    }

    static String toIndexLine(String term, List<RawPosting> postings, int docCount) {
        int df = postings.size();
        double idf = Math.log((docCount + 1.0) / (df + 1.0)) + 1.0;
        List<String> encodedPostings = new ArrayList<>();

        postings.sort((a, b) -> {
            double rankA = rank(a, idf);
            double rankB = rank(b, idf);
            return Double.compare(rankB, rankA);
        });

        for (RawPosting posting : postings) {
            double rank = rank(posting, idf);
            StringBuilder positions = new StringBuilder();
            for (int i = 0; i < posting.positions.size(); i++) {
                if (i > 0) {
                    positions.append(',');
                }
                positions.append(posting.positions.get(i));
            }
            encodedPostings.add(posting.did + ":"
                    + String.format(Locale.US, "%.6f", rank)
                    + ":" + positions);
        }
        return term + "\t" + String.join(";", encodedPostings);
    }

    private static double rank(RawPosting posting, double idf) {
        double tf = posting.tfCount() / (double) posting.totalWords;
        return tf * idf;
    }

    private static Map<String, List<RawPosting>> readFiltered(Path filteredFile) throws IOException {
        Map<String, List<RawPosting>> index = new TreeMap<>();
        try (BufferedReader reader = Files.newBufferedReader(filteredFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) {
                    continue;
                }
                int did = Integer.parseInt(parts[0]);
                String[] tokens = parts[2].trim().split("\\s+");
                Map<String, List<Integer>> positionsByTerm = new LinkedHashMap<>();
                for (int i = 0; i < tokens.length; i++) {
                    positionsByTerm.computeIfAbsent(tokens[i], key -> new ArrayList<>()).add(i);
                }
                for (Map.Entry<String, List<Integer>> entry : positionsByTerm.entrySet()) {
                    index.computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                            .add(new RawPosting(did, tokens.length, entry.getValue()));
                }
            }
        }
        return index;
    }

    public static class IndexRecord {
        public final String term;
        public final List<Posting> postings;

        public IndexRecord(String term, List<Posting> postings) {
            this.term = term;
            this.postings = postings;
        }
    }
}
