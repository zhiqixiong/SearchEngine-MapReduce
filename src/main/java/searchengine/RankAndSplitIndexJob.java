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
import java.util.List;
import java.util.Locale;

/**
 * Job-3 in the final design: postingFile -> final invertedIndex.
 * It calculates DF/IDF/TF-IDF and optionally partitions terms by prefix.
 */
public class RankAndSplitIndexJob extends Configured implements Tool {
    public static class RankMapper extends Mapper<LongWritable, Text, Text, Text> {
        private final Text outKey = new Text();
        private final Text outValue = new Text();

        @Override
        protected void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String[] parts = value.toString().split("\t", 2);
            if (parts.length < 2) {
                return;
            }
            outKey.set(parts[0]);
            String[] postings = parts[1].split(";");
            for (String posting : postings) {
                if (!posting.trim().isEmpty()) {
                    outValue.set(posting.trim());
                    context.write(outKey, outValue);
                }
            }
        }
    }

    public static class RankReducer extends Reducer<Text, Text, Text, Text> {
        private int docCount;

        @Override
        protected void setup(Context context) {
            docCount = context.getConfiguration().getInt("searchengine.doc.count", 1);
        }

        @Override
        protected void reduce(Text term, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            List<PostingWithScore> postings = new ArrayList<>();
            for (Text value : values) {
                PostingCore.RawPosting raw = PostingCore.parsePosting(value.toString());
                postings.add(new PostingWithScore(raw, 0.0));
            }
            if (postings.isEmpty()) {
                return;
            }

            int df = postings.size();
            double idf = Math.log((docCount + 1.0) / (df + 1.0)) + 1.0;
            for (int i = 0; i < postings.size(); i++) {
                PostingCore.RawPosting raw = postings.get(i).raw;
                double tf = raw.docLen == 0 ? 0.0 : raw.tfCount / (double) raw.docLen;
                postings.set(i, new PostingWithScore(raw, tf * idf));
            }
            postings.sort((a, b) -> Double.compare(b.score, a.score));

            List<String> encoded = new ArrayList<>();
            for (PostingWithScore posting : postings) {
                encoded.add(posting.raw.did + ":"
                        + String.format(Locale.US, "%.6f", posting.score)
                        + ":" + PostingCore.joinPositions(posting.raw.positions));
            }
            context.write(term, new Text(String.join(";", encoded)));
        }
    }

    private static class PostingWithScore {
        final PostingCore.RawPosting raw;
        final double score;

        PostingWithScore(PostingCore.RawPosting raw, double score) {
            this.raw = raw;
            this.score = score;
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Usage: hadoop jar search-engine.jar searchengine.RankAndSplitIndexJob <postingInput> <indexOutput> <docCount> [numReducers]");
            return 1;
        }

        getConf().setInt("searchengine.doc.count", Integer.parseInt(args[2]));
        Job job = Job.getInstance(getConf(), "search-engine-rank-and-split-index-job");
        job.setJarByClass(RankAndSplitIndexJob.class);
        job.setMapperClass(RankMapper.class);
        job.setReducerClass(RankReducer.class);
        job.setPartitionerClass(PrefixPartitioner.class);
        if (args.length == 4) {
            job.setNumReduceTasks(Integer.parseInt(args[3]));
        }

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
        int code = ToolRunner.run(new RankAndSplitIndexJob(), args);
        System.exit(code);
    }
}
