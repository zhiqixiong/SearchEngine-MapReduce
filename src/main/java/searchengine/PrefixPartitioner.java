package searchengine;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Partitioner;

/** Prefix-based partitioner inspired by the classmate version. */
public class PrefixPartitioner extends Partitioner<Text, Text> {
    @Override
    public int getPartition(Text key, Text value, int numPartitions) {
        if (numPartitions <= 1) {
            return 0;
        }
        String term = key.toString();
        String prefix = term.length() >= 2 ? term.substring(0, 2) : term;
        return (prefix.hashCode() & Integer.MAX_VALUE) % numPartitions;
    }
}
