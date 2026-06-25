package searchengine;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InvertedIndexJob extends Configured implements Tool {
    public static class IndexMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String[] parts = value.toString().split("\t", 3);
            if (parts.length < 3) {
                return;
            }
            int did = Integer.parseInt(parts[0]);
            String[] tokens = parts[2].trim().split("\\s+");
            if (tokens.length == 0) {
                return;
            }

            Map<String, List<Integer>> positionsByTerm = new HashMap<>();
            for (int i = 0; i < tokens.length; i++) {
                positionsByTerm.computeIfAbsent(tokens[i], term -> new ArrayList<>()).add(i);
            }

            for (Map.Entry<String, List<Integer>> entry : positionsByTerm.entrySet()) {
                outKey.set(entry.getKey());
                outValue.set(did + "," + tokens.length + "," + entry.getValue().size()
                        + "," + joinPositions(entry.getValue()));
                context.write(outKey, outValue);
            }
        }

        private static String joinPositions(List<Integer> positions) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < positions.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(positions.get(i));
            }
            return builder.toString();
        }
    }

    public static class IndexReducer extends Reducer<Text, Text, Text, Text> {
        private int docCount;

        @Override
        protected void setup(Context context) {
            docCount = context.getConfiguration().getInt("searchengine.doc.count", 1);
        }

        @Override
        protected void reduce(Text term, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            List<ReducePosting> postings = new ArrayList<>();
            for (Text value : values) {
                postings.add(ReducePosting.parse(value.toString()));
            }

            int df = postings.size();
            double idf = Math.log((docCount + 1.0) / (df + 1.0)) + 1.0;
            postings.sort((a, b) -> Double.compare(b.rank(idf), a.rank(idf)));

            List<String> encoded = new ArrayList<>();
            for (ReducePosting posting : postings) {
                encoded.add(posting.did + ":"
                        + String.format(Locale.US, "%.6f", posting.rank(idf))
                        + ":" + joinPositions(posting.positions));
            }
            context.write(term, new Text(String.join(";", encoded)));
        }

        private static String joinPositions(List<Integer> positions) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < positions.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(positions.get(i));
            }
            return builder.toString();
        }
    }

    private static class ReducePosting {
        final int did;
        final int totalWords;
        final int tfCount;
        final List<Integer> positions;

        ReducePosting(int did, int totalWords, int tfCount, List<Integer> positions) {
            this.did = did;
            this.totalWords = totalWords;
            this.tfCount = tfCount;
            this.positions = positions;
            Collections.sort(this.positions);
        }

        double rank(double idf) {
            return (tfCount / (double) totalWords) * idf;
        }

        static ReducePosting parse(String text) {
            String[] parts = text.split(",", 4);
            int did = Integer.parseInt(parts[0]);
            int totalWords = Integer.parseInt(parts[1]);
            int tfCount = Integer.parseInt(parts[2]);
            List<Integer> positions = new ArrayList<>();
            if (parts.length == 4 && !parts[3].isEmpty()) {
                String[] posParts = parts[3].split(",");
                for (String pos : posParts) {
                    positions.add(Integer.parseInt(pos));
                }
            }
            return new ReducePosting(did, totalWords, tfCount, positions);
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: hadoop jar search-engine.jar searchengine.InvertedIndexJob <input> <output> <docCount>");
            return 1;
        }

        getConf().setInt("searchengine.doc.count", Integer.parseInt(args[2]));
        Job job = Job.getInstance(getConf(), "search-engine-inverted-index");
        job.setJarByClass(InvertedIndexJob.class);
        job.setMapperClass(IndexMapper.class);
        job.setReducerClass(IndexReducer.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        TextInputFormat.addInputPath(job, new Path(args[0]));
        TextOutputFormat.setOutputPath(job, new Path(args[1]));

        return job.waitForCompletion(true) ? 0 : 2;
    }

    public static void main(String[] args) throws Exception {
        int code = ToolRunner.run(new InvertedIndexJob(), args);
        System.exit(code);
    }
}
