# SearchEngine-MapReduce

基于 Hadoop MapReduce 的简化全文搜索引擎。项目主线是：

```text
rawData -> filteredSourceFile -> invertedIndexFile -> secondaryIndex -> SearchShell
```

核心目标不是做复杂网页系统，而是用云计算课程中的分布式存储、并行计算、Shuffle 聚合和倒排索引思想，实现一个可以查询英文关键词的搜索引擎原型。

## 1. 项目结构

```text
SearchEngine-MapReduce/
├── data/
│   ├── ostep/                  # 8 篇输入文档，可替换为真正 OSTEP txt
│   ├── stopwords.txt
│   └── mock/
├── src/main/java/searchengine/
│   ├── PrepareRawData.java
│   ├── FilterJob.java
│   ├── InvertedIndexJob.java
│   ├── SecondaryIndexBuilder.java
│   ├── SearchShell.java
│   ├── TextCleaner.java
│   ├── StopWords.java
│   └── LocalPipeline.java
├── scripts/
│   ├── init_hdfs.sh
│   ├── run_filter.sh
│   ├── run_index.sh
│   ├── run_shell.sh
│   ├── run_all.sh
│   └── run_local_demo.sh
└── pom.xml
```

## 2. 统一文件格式

### rawData

HDFS 路径：

```text
/search/rawData
```

格式：

```text
DID\tFILENAME\tCONTENT
```

一篇文档必须占一行。

### filteredSourceFile

HDFS 路径：

```text
/search/filtered
```

格式：

```text
DID\tFILENAME\ttoken1 token2 token3 ...
```

`position` 统一使用过滤后 token 序列中的下标。

### invertedIndexFile

HDFS 路径：

```text
/search/index
```

格式：

```text
TERM\tDID:rank:pos1,pos2,pos3;DID:rank:pos1,pos2,pos3
```

`rank = TF * IDF`，其中：

```text
TF = term 在该文档中的出现次数 / 该文档 token 总数
IDF = log((N + 1) / (df + 1)) + 1
```

## 3. Hadoop 运行方式

检查环境：

```bash
java -version
mvn -version
hadoop version
```

一键运行：

```bash
bash scripts/run_all.sh
```

运行完成后启动查询：

```bash
java -jar target/search-engine-1.0.0.jar shell \
  output/local/invertedIndex.txt \
  output/local/filteredSourceFile.txt \
  2
```

测试词：

```text
schedule
scheduling
mlfq
memory
file
inode
exit
```

## 4. 分阶段运行

生成 rawData：

```bash
mvn clean package -DskipTests
java -cp target/search-engine-1.0.0.jar searchengine.PrepareRawData data/ostep output/rawData.txt
hdfs dfs -rm -r -f /search/rawData
hdfs dfs -mkdir -p /search/rawData
hdfs dfs -put output/rawData.txt /search/rawData/
```

运行过滤 Job：

```bash
bash scripts/run_filter.sh
```

运行倒排索引 Job：

```bash
bash scripts/run_index.sh 8
```

拉取结果并查询：

```bash
bash scripts/run_shell.sh 2
```

## 5. 本地自测方式

如果当前机器没有 Hadoop 和 Maven，可以先跑本地 demo：

```bash
bash scripts/run_local_demo.sh
```

然后查询：

```bash
java -cp target/classes searchengine.SearchEngineMain shell \
  output/local/invertedIndex.txt \
  output/local/filteredSourceFile.txt \
  2
```

注意：本地 demo 只是为了验证算法和 Shell，最终课程演示仍建议用 Hadoop MapReduce 脚本。

## 6. 验收命令

查看 rawData 行数：

```bash
hdfs dfs -cat /search/rawData/rawData.txt | wc -l
```

预期：

```text
8
```

查看 filtered：

```bash
hdfs dfs -cat /search/filtered/part-* | head -3
```

查看倒排索引：

```bash
hdfs dfs -cat /search/index/part-* | grep '^schedule'
hdfs dfs -cat /search/index/part-* | grep '^memory'
hdfs dfs -cat /search/index/part-* | grep '^file'
hdfs dfs -cat /search/index/part-* | grep '^inode'
```

## 7. 汇报时的技术重点

1. `PrepareRawData` 把多文件语料整理成 Hadoop 友好的一行一文档格式。
2. `FilterJob` 是 map-only 任务，体现按文档并行清洗。
3. `InvertedIndexJob` 的 Mapper 做文档内局部统计，Reducer 通过 Shuffle 得到同一个 term 的全局 posting list。
4. `rank` 使用 TF-IDF，既考虑词在文档中的频率，也考虑词在全局语料中的区分度。
5. `SecondaryIndexBuilder` 用 `term -> offset` 避免每次查询扫描完整倒排索引。
6. `SearchShell` 根据 positions 从 filtered token 序列截取摘要，保证摘要和索引位置一致。
