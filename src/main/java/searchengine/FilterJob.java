package searchengine;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.IOException;

public class FilterJob extends Configured implements Tool {
    public static class FilterMapper extends Mapper<LongWritable, Text, NullWritable, Text> {
        private final Text output = new Text();
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
            String tokenLine = cleaner.cleanToTokenLine(parts[2]);
            if (tokenLine.isEmpty()) {
                return;
            }
            output.set(parts[0] + "\t" + parts[1] + "\t" + tokenLine);
            context.write(NullWritable.get(), output);
        }
    }

    @Override
    public int run(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: hadoop jar search-engine.jar searchengine.FilterJob <input> <output>");
            return 1;
        }

        Job job = Job.getInstance(getConf(), "search-engine-filter");
        job.setJarByClass(FilterJob.class);
        job.setMapperClass(FilterMapper.class);
        job.setNumReduceTasks(0);

        job.setMapOutputKeyClass(NullWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        TextInputFormat.addInputPath(job, new Path(args[0]));
        TextOutputFormat.setOutputPath(job, new Path(args[1]));

        return job.waitForCompletion(true) ? 0 : 2;
    }

    public static void main(String[] args) throws Exception {
        int code = ToolRunner.run(new FilterJob(), args);
        System.exit(code);
    }
}
