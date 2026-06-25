package searchengine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SecondaryIndexBuilder {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java searchengine.SecondaryIndexBuilder <invertedIndexFile> <secondaryIndexFile>");
            System.exit(1);
        }
        build(Paths.get(args[0]), Paths.get(args[1]));
    }

    public static void build(Path invertedIndexFile, Path secondaryIndexFile) throws IOException {
        Path parent = secondaryIndexFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (RandomAccessFile raf = new RandomAccessFile(invertedIndexFile.toFile(), "r");
             BufferedWriter writer = Files.newBufferedWriter(secondaryIndexFile, StandardCharsets.UTF_8)) {
            long offset = raf.getFilePointer();
            String line;
            while ((line = raf.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    String term = line.substring(0, tab);
                    writer.write(term + "\t" + offset);
                    writer.newLine();
                }
                offset = raf.getFilePointer();
            }
        }
    }
}
