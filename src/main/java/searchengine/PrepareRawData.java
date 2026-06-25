package searchengine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PrepareRawData {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java searchengine.PrepareRawData <inputDir> <outputFile>");
            System.exit(1);
        }
        prepare(Paths.get(args[0]), Paths.get(args[1]));
    }

    public static void prepare(Path inputDir, Path outputFile) throws IOException {
        List<Path> files = listTxtFiles(inputDir);
        if (files.isEmpty()) {
            throw new IOException("No .txt files found in " + inputDir);
        }

        Path parent = outputFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            int did = 0;
            for (Path file : files) {
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                        .replaceAll("\\s+", " ")
                        .trim();
                writer.write(did + "\t" + file.getFileName().toString() + "\t" + content);
                writer.newLine();
                did++;
            }
        }
    }

    private static List<Path> listTxtFiles(Path inputDir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputDir, "*.txt")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    files.add(path);
                }
            }
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return files;
    }
}
