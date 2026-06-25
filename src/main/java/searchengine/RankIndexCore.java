package searchengine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local implementation of Job-3: postingFile -> final inverted index. */
public class RankIndexCore {
    public static void build(Path postingFile, Path indexFile, int docCount) throws IOException {
        Path parent = indexFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedReader reader = Files.newBufferedReader(postingFile, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(indexFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String output = rankLine(line, docCount);
                if (output != null) {
                    writer.write(output);
                    writer.newLine();
                }
            }
        }
    }

    public static String rankLine(String line, int docCount) {
        String[] parts = line.split("\t", 2);
        if (parts.length < 2) {
            return null;
        }
        String term = parts[0];
        String[] postingTexts = parts[1].split(";");
        List<PostingWithScore> postings = new ArrayList<>();
        for (String postingText : postingTexts) {
            if (postingText.trim().isEmpty()) {
                continue;
            }
            PostingCore.RawPosting raw = PostingCore.parsePosting(postingText);
            postings.add(new PostingWithScore(raw, 0.0));
        }
        if (postings.isEmpty()) {
            return null;
        }

        int df = postings.size();
        double idf = Math.log((docCount + 1.0) / (df + 1.0)) + 1.0;
        List<String> encoded = new ArrayList<>();
        for (int i = 0; i < postings.size(); i++) {
            PostingCore.RawPosting raw = postings.get(i).raw;
            double tf = raw.docLen == 0 ? 0.0 : raw.tfCount / (double) raw.docLen;
            postings.set(i, new PostingWithScore(raw, tf * idf));
        }
        postings.sort((a, b) -> Double.compare(b.score, a.score));
        for (PostingWithScore posting : postings) {
            encoded.add(posting.raw.did + ":"
                    + String.format(Locale.US, "%.6f", posting.score)
                    + ":" + PostingCore.joinPositions(posting.raw.positions));
        }
        return term + "\t" + String.join(";", encoded);
    }

    private static class PostingWithScore {
        final PostingCore.RawPosting raw;
        final double score;

        PostingWithScore(PostingCore.RawPosting raw, double score) {
            this.raw = raw;
            this.score = score;
        }
    }
}
