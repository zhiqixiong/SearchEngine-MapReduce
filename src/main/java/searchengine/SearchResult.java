package searchengine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SearchResult {
    private final int did;
    private final String filename;
    private final double score;
    private final List<Integer> positions;
    private final String summary;
    private final String highlightedSummary;

    public SearchResult(int did, String filename, double score, List<Integer> positions,
                        String summary, String highlightedSummary) {
        this.did = did;
        this.filename = filename;
        this.score = score;
        this.positions = new ArrayList<>(positions);
        Collections.sort(this.positions);
        this.summary = summary;
        this.highlightedSummary = highlightedSummary;
    }

    public int getDid() {
        return did;
    }

    public String getFilename() {
        return filename;
    }

    public double getScore() {
        return score;
    }

    public List<Integer> getPositions() {
        return Collections.unmodifiableList(positions);
    }

    public String getSummary() {
        return summary;
    }

    public String getHighlightedSummary() {
        return highlightedSummary;
    }
}
