package searchengine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Posting {
    private final int did;
    private final double rank;
    private final List<Integer> positions;

    public Posting(int did, double rank, List<Integer> positions) {
        this.did = did;
        this.rank = rank;
        this.positions = new ArrayList<>(positions);
        Collections.sort(this.positions);
    }

    public int getDid() {
        return did;
    }

    public double getRank() {
        return rank;
    }

    public List<Integer> getPositions() {
        return Collections.unmodifiableList(positions);
    }
}
