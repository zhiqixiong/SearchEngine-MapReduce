package searchengine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SearchShell {
    private static final int SUMMARY_WORDS = 20;

    private final TextCleaner cleaner = new TextCleaner();
    private final Map<String, List<Posting>> indexMap;
    private final Map<Integer, Document> documents;
    private final int topK;

    private final Path indexPath;
    private final Map<String, Long> secondaryIndex;
    private final SearchQueryService queryService;

    public SearchShell(Map<String, List<Posting>> indexMap, Map<Integer, Document> documents, int topK) {
        this.indexMap = indexMap;
        this.documents = documents;
        this.topK = topK;
        this.indexPath = null;
        this.secondaryIndex = null;
        this.queryService = new SearchQueryService(indexMap, documents);
    }

    public SearchShell(Path indexPath, Map<String, Long> secondaryIndex,
                       Map<Integer, Document> documents, int topK) {
        this.indexMap = null;
        this.documents = documents;
        this.topK = topK;
        this.indexPath = indexPath;
        this.secondaryIndex = secondaryIndex;
        this.queryService = null;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 3) {
            SearchShell shell = fromFullIndex(Paths.get(args[0]), Paths.get(args[1]), Integer.parseInt(args[2]));
            shell.run();
            return;
        }
        if (args.length == 5 && "secondary".equals(args[0])) {
            SearchShell shell = fromSecondary(Paths.get(args[1]), Paths.get(args[2]),
                    Paths.get(args[3]), Integer.parseInt(args[4]));
            shell.run();
            return;
        }
        System.err.println("Usage: java searchengine.SearchShell <indexFile> <filteredFile> <topK>");
        System.err.println("   or: java searchengine.SearchShell secondary <indexFile> <secondaryFile> <filteredFile> <topK>");
        System.exit(1);
    }

    public static SearchShell fromFullIndex(Path indexFile, Path filteredFile, int topK) throws Exception {
        return new SearchShell(
                InvertedIndexCore.loadIndex(indexFile),
                InvertedIndexCore.loadDocuments(filteredFile),
                topK);
    }

    public static SearchShell fromSecondary(Path indexFile, Path secondaryFile,
                                            Path filteredFile, int topK) throws Exception {
        return new SearchShell(
                indexFile,
                loadSecondaryIndex(secondaryFile),
                InvertedIndexCore.loadDocuments(filteredFile),
                topK);
    }

    public void run() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Mini Search Engine. Type a term / multi-term query, or exit.");
        while (true) {
            System.out.print("search> ");
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            String query = line.trim();
            if ("exit".equalsIgnoreCase(query) || "quit".equalsIgnoreCase(query) || "q".equalsIgnoreCase(query)) {
                break;
            }
            if (query.isEmpty()) {
                continue;
            }
            search(query);
        }
    }

    public void search(String query) throws Exception {
        if (queryService != null) {
            List<SearchResult> results = queryService.search(query, topK);
            if (results.isEmpty()) {
                System.out.println("No result found.");
                return;
            }
            for (int i = 0; i < results.size(); i++) {
                SearchResult result = results.get(i);
                System.out.println("[" + (i + 1) + "] " + result.getFilename());
                System.out.printf("score: %.6f%n", result.getScore());
                System.out.println("positions: " + result.getPositions().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", ")));
                System.out.println("summary: " + result.getSummary());
                System.out.println();
            }
            return;
        }

        String term = cleaner.cleanQueryTerm(query);
        if (term.isEmpty()) {
            System.out.println("No result found.");
            return;
        }

        List<Posting> postings = lookup(term);
        if (postings == null || postings.isEmpty()) {
            System.out.println("No result found.");
            return;
        }

        int limit = Math.min(topK, postings.size());
        for (int i = 0; i < limit; i++) {
            Posting posting = postings.get(i);
            Document document = documents.get(posting.getDid());
            if (document == null) {
                continue;
            }

            System.out.println("[" + (i + 1) + "] " + document.getFilename());
            System.out.printf("score: %.6f%n", posting.getRank());
            System.out.println("positions: " + posting.getPositions().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ")));

            List<Integer> positions = posting.getPositions();
            if (!positions.isEmpty()) {
                System.out.println("summary 1: " + makeSummary(document.getTokens(), positions.get(0)));
            }
            if (positions.size() >= 2) {
                System.out.println("summary 2: " + makeSummary(document.getTokens(), positions.get(1)));
            } else {
                System.out.println("summary 2: not enough occurrence");
            }
            System.out.println();
        }
    }

    private List<Posting> lookup(String term) throws Exception {
        if (indexMap != null) {
            return indexMap.get(term);
        }
        Long offset = secondaryIndex.get(term);
        if (offset == null) {
            return null;
        }
        try (RandomAccessFile raf = new RandomAccessFile(indexPath.toFile(), "r")) {
            raf.seek(offset);
            String line = raf.readLine();
            InvertedIndexCore.IndexRecord record = InvertedIndexCore.parseIndexLine(line);
            return record == null ? null : record.postings;
        }
    }

    private static String makeSummary(List<String> tokens, int pos) {
        if (tokens.isEmpty() || pos >= tokens.size()) {
            return "";
        }
        int start = Math.max(pos, 0);
        int end = Math.min(start + SUMMARY_WORDS, tokens.size());
        return String.join(" ", tokens.subList(start, end));
    }

    private static Map<String, Long> loadSecondaryIndex(Path secondaryFile) throws Exception {
        Map<String, Long> map = new HashMap<>();
        List<String> lines = java.nio.file.Files.readAllLines(secondaryFile);
        for (String line : lines) {
            String[] parts = line.split("\t", 2);
            if (parts.length == 2) {
                map.put(parts[0], Long.parseLong(parts[1]));
            }
        }
        return map;
    }
}
