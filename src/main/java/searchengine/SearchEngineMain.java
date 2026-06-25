package searchengine;

import java.nio.file.Paths;
import java.util.Arrays;

public class SearchEngineMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(1);
        }

        String command = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        switch (command) {
            case "prepare":
                PrepareRawData.main(rest);
                break;
            case "local":
                LocalPipeline.main(rest);
                break;
            case "rawlocal":
                RawDataPipeline.main(rest);
                break;
            case "buildSecondary":
                SecondaryIndexBuilder.main(rest);
                break;
            case "shell":
                if (rest.length != 3) {
                    System.err.println("Usage: java -jar search-engine.jar shell <indexFile> <filteredFile> <topK>");
                    System.exit(1);
                }
                SearchShell.fromFullIndex(Paths.get(rest[0]), Paths.get(rest[1]), Integer.parseInt(rest[2])).run();
                break;
            case "shell2":
                if (rest.length != 4) {
                    System.err.println("Usage: java -jar search-engine.jar shell2 <indexFile> <secondaryFile> <filteredFile> <topK>");
                    System.exit(1);
                }
                SearchShell.fromSecondary(Paths.get(rest[0]), Paths.get(rest[1]),
                        Paths.get(rest[2]), Integer.parseInt(rest[3])).run();
                break;
            case "web":
                if (rest.length < 3 || rest.length > 4) {
                    System.err.println("Usage: java -jar search-engine.jar web <indexFile> <filteredFile> <topK> [port]");
                    System.exit(1);
                }
                SearchWebServer.main(rest);
                break;
            default:
                usage();
                System.exit(1);
        }
    }

    private static void usage() {
        System.err.println("Commands:");
        System.err.println("  prepare <inputDir> <outputFile>");
        System.err.println("  local <inputDir> <outputDir>");
        System.err.println("  rawlocal <rawDataFile> <outputDir>");
        System.err.println("  buildSecondary <indexFile> <secondaryIndexFile>");
        System.err.println("  shell <indexFile> <filteredFile> <topK>");
        System.err.println("  shell2 <indexFile> <secondaryFile> <filteredFile> <topK>");
        System.err.println("  web <indexFile> <filteredFile> <topK> [port]");
        System.err.println("Hadoop three-stage jobs:");
        System.err.println("  hadoop jar search-engine.jar searchengine.FilterJob <rawDataInput> <filteredOutput>");
        System.err.println("  hadoop jar search-engine.jar searchengine.PostingJob <filteredInput> <postingOutput>");
        System.err.println("  hadoop jar search-engine.jar searchengine.RankAndSplitIndexJob <postingInput> <indexOutput> <docCount> [reducers]");
    }
}
