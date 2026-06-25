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
        // Emit overlapping bigrams so Chinese queries such as “爬虫” or “数据” can hit.
        // This is a lightweight alternative to Jieba and keeps the Java/Hadoop pipeline self-contained.
        for (int i = 0; i < text.length() - 1; i++) {
            tokens.add(text.substring(i, i + 2));
        }
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
        return tokens.get(0);
    }
}
