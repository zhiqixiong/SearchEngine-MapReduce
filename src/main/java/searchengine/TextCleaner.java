package searchengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TextCleaner {
    private final StopWords stopWords;

    public TextCleaner() {
        this.stopWords = StopWords.loadDefaultOrFile("data/stopwords.txt");
    }

    public TextCleaner(StopWords stopWords) {
        this.stopWords = stopWords;
    }

    public List<String> cleanAndTokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }

        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ");
        String[] parts = normalized.trim().split("\\s+");
        for (String part : parts) {
            if (part.length() < 2) {
                continue;
            }
            if (stopWords.contains(part)) {
                continue;
            }
            tokens.add(part);
        }
        return tokens;
    }

    public String cleanToTokenLine(String text) {
        return String.join(" ", cleanAndTokenize(text));
    }

    public String cleanQueryTerm(String query) {
        List<String> tokens = cleanAndTokenize(query);
        if (tokens.isEmpty()) {
            return "";
        }
        return tokens.get(0);
    }
}
