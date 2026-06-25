package searchengine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SearchQueryService {
    private static final int SUMMARY_WORDS = 28;

    private final TextCleaner cleaner = new TextCleaner();
    private final Map<String, List<Posting>> indexMap;
    private final Map<Integer, Document> documents;

    public SearchQueryService(Map<String, List<Posting>> indexMap, Map<Integer, Document> documents) {
        this.indexMap = indexMap;
        this.documents = documents;
    }

    public static SearchQueryService load(Path indexFile, Path filteredFile) throws Exception {
        return new SearchQueryService(
                InvertedIndexCore.loadIndex(indexFile),
                InvertedIndexCore.loadDocuments(filteredFile));
    }

    public List<String> normalizeQueryTerms(String query) {
        List<String> rawTokens = cleaner.cleanAndTokenize(query);
        Set<String> terms = new LinkedHashSet<>();
        for (String token : rawTokens) {
            String term = cleaner.toIndexTerm(token);
            if (!term.isEmpty()) {
                terms.add(term);
            }
        }
        return new ArrayList<>(terms);
    }

    public List<SearchResult> search(String query, int topK) {
        List<String> terms = normalizeQueryTerms(query);
        if (terms.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, Accumulator> acc = new HashMap<>();
        for (String term : terms) {
            List<Posting> postings = indexMap.get(term);
            if (postings == null) {
                continue;
            }
            for (Posting posting : postings) {
                Accumulator a = acc.computeIfAbsent(posting.getDid(), Accumulator::new);
                a.score += posting.getRank();
                a.positions.addAll(posting.getPositions());
                a.matchedTerms.add(term);
            }
        }

        List<SearchResult> results = new ArrayList<>();
        for (Accumulator a : acc.values()) {
            Document doc = documents.get(a.did);
            if (doc == null) {
                continue;
            }
            Collections.sort(a.positions);
            int summaryStart = a.positions.isEmpty() ? 0 : a.positions.get(0);
            String summary = makeSummary(doc.getTokens(), summaryStart);
            String highlighted = makeHighlightedSummary(doc.getTokens(), summaryStart, a.matchedTerms);
            results.add(new SearchResult(a.did, doc.getFilename(), a.score, a.positions, summary, highlighted));
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > topK) {
            return new ArrayList<>(results.subList(0, topK));
        }
        return results;
    }

    private String makeSummary(List<String> tokens, int pos) {
        if (tokens.isEmpty()) {
            return "";
        }
        int start = Math.max(0, Math.min(pos, tokens.size() - 1));
        int left = Math.max(0, start - 8);
        int end = Math.min(left + SUMMARY_WORDS, tokens.size());
        return String.join(" ", tokens.subList(left, end));
    }

    private String makeHighlightedSummary(List<String> tokens, int pos, Set<String> matchedTerms) {
        if (tokens.isEmpty()) {
            return "";
        }
        int start = Math.max(0, Math.min(pos, tokens.size() - 1));
        int left = Math.max(0, start - 8);
        int end = Math.min(left + SUMMARY_WORDS, tokens.size());
        List<String> chunks = new ArrayList<>();
        for (String token : tokens.subList(left, end)) {
            String escaped = htmlEscape(token);
            String indexTerm = cleaner.toIndexTerm(token);
            if (!indexTerm.isEmpty() && matchedTerms.contains(indexTerm)) {
                chunks.add("<mark>" + escaped + "</mark>");
            } else {
                chunks.add(escaped);
            }
        }
        return chunks.stream().collect(Collectors.joining(" "));
    }

    private static String htmlEscape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static class Accumulator {
        final int did;
        double score;
        final List<Integer> positions = new ArrayList<>();
        final Set<String> matchedTerms = new LinkedHashSet<>();

        Accumulator(int did) {
            this.did = did;
        }
    }
}
