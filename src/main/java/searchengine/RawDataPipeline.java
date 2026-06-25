package searchengine;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RawDataPipeline {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java searchengine.RawDataPipeline <rawDataFile> <outputDir>");
            System.exit(1);
        }
        run(Paths.get(args[0]), Paths.get(args[1]));
    }

    public static void run(Path rawDataFile, Path outputDir) throws Exception {
        Path filtered = outputDir.resolve("filteredSourceFile.txt");
        Path postings = outputDir.resolve("postingFile.txt");
        Path index = outputDir.resolve("invertedIndex.txt");
        Path secondary = outputDir.resolve("secondaryIndex.txt");

        LocalFilter.filter(rawDataFile, filtered);
        PostingCore.build(filtered, postings);
        int docCount = countLines(rawDataFile);
        RankIndexCore.build(postings, index, docCount);
        SecondaryIndexBuilder.build(index, secondary);

        System.out.println("[Done] rawData pipeline generated:");
        System.out.println("rawData=" + rawDataFile);
        System.out.println(filtered);
        System.out.println(postings);
        System.out.println(index);
        System.out.println(secondary);
    }

    public static int countLines(Path file) throws Exception {
        int count = 0;
        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(file)) {
            while (reader.readLine() != null) {
                count++;
            }
        }
        return count;
    }
}
