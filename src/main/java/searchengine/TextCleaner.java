package searchengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared tokenizer used by both indexing and querying.
 *
 * Job-1 keeps readable normalized tokens for summaries. Job-2 converts English
 * tokens to Porter stems as index keys, so schedule/scheduling/scheduled can hit
 * the same inverted-index entry. Chinese tokens are kept as lightweight bigrams.
 */
public class TextCleaner {
    private final StopWords stopWords;
    private final PorterStemmer stemmer;

    public TextCleaner() {
        this(StopWords.loadDefaultOrFile("data/stopwords.txt"));
    }

    public TextCleaner(StopWords stopWords) {
        this.stopWords = stopWords;
        this.stemmer = new PorterStemmer();
    }

    /** Clean text into readable tokens. These tokens are used for summaries. */
    public List<String> cleanAndTokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }

        String normalized = text.toLowerCase(Locale.ROOT);
        StringBuilder english = new StringBuilder();
        StringBuilder cjk = new StringBuilder();

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch >= 'a' && ch <= 'z') {
                flushCjk(cjk, tokens);
                english.append(ch);
            } else if (isCjk(ch)) {
                flushEnglish(english, tokens);
                cjk.append(ch);
            } else {
                flushEnglish(english, tokens);
                flushCjk(cjk, tokens);
            }
        }
        flushEnglish(english, tokens);
        flushCjk(cjk, tokens);
        return tokens;
    }

    /** Convert one already-clean token to an index key. */
    public String toIndexTerm(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        String normalized = token.toLowerCase(Locale.ROOT);
        if (isAsciiEnglish(normalized)) {
            if (normalized.length() < 2 || stopWords.contains(normalized)) {
                return "";
            }
            String stemmed = stemmer.stem(normalized);
            if (stemmed == null || stemmed.length() < 2 || stopWords.contains(stemmed)) {
                return "";
            }
            return stemmed;
        }
        return normalized;
    }

    private void flushEnglish(StringBuilder english, List<String> tokens) {
        if (english.length() < 2) {
            english.setLength(0);
            return;
        }
        String token = english.toString();
        english.setLength(0);
        if (!stopWords.contains(token)) {
            tokens.add(token);
        }
    }

    private void flushCjk(StringBuilder cjk, List<String> tokens) {
        if (cjk.length() == 0) {
            return;
        }
        String text = cjk.toString();
        cjk.setLength(0);
        if (text.length() == 1) {
            tokens.add(text);
            return;
        }
        for (int i = 0; i < text.length() - 1; i++) {
            tokens.add(text.substring(i, i + 2));
        }
    }

    private boolean isAsciiEnglish(String token) {
        for (int i = 0; i < token.length(); i++) {
            char ch = token.charAt(i);
            if (ch < 'a' || ch > 'z') {
                return false;
            }
        }
        return true;
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    public String cleanToTokenLine(String text) {
        return String.join(" ", cleanAndTokenize(text));
    }

    public String cleanQueryTerm(String query) {
        List<String> tokens = cleanAndTokenize(query);
        if (tokens.isEmpty()) {
            return "";
        }
        return toIndexTerm(tokens.get(0));
    }
}
