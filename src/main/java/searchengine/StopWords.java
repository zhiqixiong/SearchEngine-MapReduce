package searchengine;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class StopWords {
    private static final String[] DEFAULT_WORDS = {
            "a", "an", "the", "is", "are", "was", "were", "to", "of",
            "and", "or", "in", "on", "for", "with", "as", "by", "it",
            "this", "that", "we", "you", "he", "she", "they", "be",
            "been", "have", "has", "had", "do", "does", "did", "from",
            "at", "into", "about", "than", "then", "there", "their",
            "its", "our", "can", "will", "would", "should", "could"
    };

    private final Set<String> words;

    public StopWords() {
        this.words = defaultWords();
    }

    public StopWords(Set<String> words) {
        this.words = new HashSet<>(words);
    }

    public boolean contains(String word) {
        return words.contains(word);
    }

    public Set<String> asSet() {
        return Collections.unmodifiableSet(words);
    }

    public static Set<String> defaultWords() {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, DEFAULT_WORDS);
        return set;
    }

    public static StopWords loadDefaultOrFile(String fileName) {
        Path path = Paths.get(fileName);
        if (!Files.exists(path)) {
            return new StopWords();
        }
        try {
            return load(path);
        } catch (IOException e) {
            return new StopWords();
        }
    }

    public static StopWords load(Path path) throws IOException {
        Set<String> set = defaultWords();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim().toLowerCase();
                if (!word.isEmpty() && !word.startsWith("#")) {
                    set.add(word);
                }
            }
        }
        return new StopWords(set);
    }
}
