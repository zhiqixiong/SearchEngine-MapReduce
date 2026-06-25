package searchengine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Document {
    private final int did;
    private final String filename;
    private final List<String> tokens;

    public Document(int did, String filename, List<String> tokens) {
        this.did = did;
        this.filename = filename;
        this.tokens = new ArrayList<>(tokens);
    }

    public int getDid() {
        return did;
    }

    public String getFilename() {
        return filename;
    }

    public List<String> getTokens() {
        return Collections.unmodifiableList(tokens);
    }
}
