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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Job-2 in the final design: filteredSourceFile -> postingFile.
 *
 * Input line:
 *   DID \t FILENAME_OR_URL \t token1 token2 token3 ...
 *
 * Output line:
 *   term \t DID:FILENAME:docLen:tfCount:pos1,pos2;DID:FILENAME:docLen:tfCount:...
 */
public class PostingJob extends Configured implements Tool {
    public static class PostingMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();
        private TextCleaner cleaner;

        @Override
        protected void setup(Context context) {
            cleaner = new TextCleaner();
        }

        @Override
        protected void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String[] parts = value.toString().split("\t", 3);
            if (parts.length < 3) {
                return;
            }
            int did;
            try {
                did = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                return;
            }
            String filename = parts[1];
            String tokenLine = parts[2].trim();
            if (tokenLine.isEmpty()) {
                return;
            }
            String[] tokens = tokenLine.split("\\s+");
            if (tokens.length == 0) {
                return;
            }

            Map<String, List<Integer>> positionsByTerm = new LinkedHashMap<>();
            for (int i = 0; i < tokens.length; i++) {
                if (!tokens[i].isEmpty()) {
                    String indexTerm = cleaner.toIndexTerm(tokens[i]);
                    if (!indexTerm.isEmpty()) {
                        positionsByTerm.computeIfAbsent(indexTerm, t -> new ArrayList<>()).add(i);
                    }
                }
            }

            for (Map.Entry<String, List<Integer>> entry : positionsByTerm.entrySet()) {
                outKey.set(entry.getKey());
                outValue.set(PostingCore.encodePosting(
                        did,
                        filename,
                        tokens.length,
                        entry.getValue().size(),
                        entry.getValue()));
                context.write(outKey, outValue);
            }
        }
    }

    public static class PostingReducer extends Reducer<Text, Text, Text, Text> {
        @Override
        protected void reduce(Text term, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            List<String> flattened = new ArrayList<>();
            for (Text value : values) {
                String text = value.toString();
                if (text.indexOf(';') >= 0) {
                    String[] parts = text.split(";");
                    for (String part : parts) {
                        if (!part.trim().isEmpty()) {
                            flattened.add(part.trim());
                        }
                    }
                } else if (!text.trim().isEmpty()) {
                    flattened.add(text.trim());
                }
            }
            context.write(term, new Text(String.join(";", flattened)));
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: hadoop jar search-engine.jar searchengine.PostingJob <filteredInput> <postingOutput>");
            return 1;
        }

        Job job = Job.getInstance(getConf(), "search-engine-posting-job");
        job.setJarByClass(PostingJob.class);
        job.setMapperClass(PostingMapper.class);
        job.setCombinerClass(PostingReducer.class);
        job.setReducerClass(PostingReducer.class);

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
        int code = ToolRunner.run(new PostingJob(), args);
        System.exit(code);
    }
}
