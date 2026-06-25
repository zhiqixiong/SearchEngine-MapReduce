# SearchEngine-MapReduce Fused

这是搜索引擎课程实验的融合版项目。最终系统由四个部分组成：

```text
Python 爬虫 / 固定 OSTEP 文档
        ↓
rawData 统一原始数据格式
        ↓
三级 Hadoop MapReduce 索引构建
        ↓
SearchShell 在线查询
        ↑
myShell 作为外层任务控制台
```

## 1. 核心变化

本版吸收了同学版本中的三级 Job 思想、PorterStemmer 词干化和 Combiner/局部聚合思想，但保留本项目的 rawData 通用输入格式、爬虫接入、中文 bigram 和 myShell 总控制台。

三级 MapReduce 核心流程：

| 阶段 | 类名 | 输入 | 输出 | 作用 |
|---|---|---|---|---|
| Job 1 | `FilterJob` | `/search/rawData` | `/search/filtered` | 文本清洗、分词、停用词过滤，保留可读 token 供摘要使用 |
| Job 2 | `PostingJob` | `/search/filtered` | `/search/postings` | 统计 term 在每篇文档中的 TF、docLen、positions |
| Job 3 | `RankAndSplitIndexJob` | `/search/postings` | `/search/index` | 统计 DF、计算 IDF/TF-IDF，生成最终倒排索引；支持前缀分区 |

数据流：

```text
/search/rawData
    ↓ FilterJob
/search/filtered
    ↓ PostingJob
/search/postings
    ↓ RankAndSplitIndexJob
/search/index
```

## 2. 输入输出格式

### rawData

```text
DID<TAB>FILENAME_OR_URL<TAB>CONTENT
```

例如：

```text
0	0.txt	Scheduling introduction ...
1	https://example.com/a	Python crawler search engine ...
```

### filteredSourceFile

```text
DID<TAB>FILENAME_OR_URL<TAB>token1 token2 token3 ...
```

这里保留的是可读 token，用于摘要展示。

### postingFile

```text
term<TAB>DID:FILENAME:docLen:tfCount:pos1,pos2;DID:FILENAME:docLen:tfCount:...
```

其中英文 term 会经过 PorterStemmer 归一化，中文 term 保留 bigram。

### invertedIndex

```text
term<TAB>DID:score:pos1,pos2;DID:score:pos1,pos2
```

`score = (tfCount / docLen) * (log((N + 1) / (df + 1)) + 1)`。

## 3. 本地调试

Windows / Linux 都可以先跑本地模式，不需要 Hadoop：

```bash
mvn clean package -DskipTests
java -jar target/search-engine-1.0.0.jar local data/ostep output/local
java -jar target/search-engine-1.0.0.jar shell output/local/invertedIndex.txt output/local/filteredSourceFile.txt 2
```

测试词：

```text
schedule
mlfq
memory
file
inode
exit
```

## 4. Hadoop 正式流程

你们集群使用 Hadoop 2.10.2，因此 `pom.xml` 已配置：

```xml
<hadoop.version>2.10.2</hadoop.version>
```

先创建 HDFS 目录：

```bash
bash scripts/init_hdfs.sh
```

固定 OSTEP 文档版一键运行：

```bash
bash scripts/run_all.sh 8 2 1
```

参数含义：

```text
8  = docCount
2  = SearchShell topK
1  = RankAndSplitIndexJob reducer 数量
```

也可以手动分步执行：

```bash
mvn clean package -DskipTests
java -jar target/search-engine-1.0.0.jar prepare data/ostep output/rawData.txt

hdfs dfs -rm -r -f /search/rawData /search/filtered /search/postings /search/index
hdfs dfs -mkdir -p /search/rawData /search/filtered /search/postings /search/index
hdfs dfs -put output/rawData.txt /search/rawData/rawData.txt

hadoop jar target/search-engine-1.0.0.jar searchengine.FilterJob /search/rawData /search/filtered
hadoop jar target/search-engine-1.0.0.jar searchengine.PostingJob /search/filtered /search/postings
hadoop jar target/search-engine-1.0.0.jar searchengine.RankAndSplitIndexJob /search/postings /search/index 8 1

mkdir -p output/hadoop
hdfs dfs -getmerge /search/index output/hadoop/invertedIndex.txt
hdfs dfs -getmerge /search/filtered output/hadoop/filteredSourceFile.txt
hdfs dfs -getmerge /search/postings output/hadoop/postingFile.txt
java -jar target/search-engine-1.0.0.jar shell output/hadoop/invertedIndex.txt output/hadoop/filteredSourceFile.txt 2
```

## 5. 爬虫版流程

安装依赖：

```bash
pip3 install -r tools/crawler/requirements.txt
```

一键爬虫 + Hadoop 三 Job：

```bash
bash scripts/run_crawler_hadoop.sh 40 10 1
```

参数含义：

```text
40 = 目标爬取文档数
10 = 查询返回 TopK
1 = reducer 数量
```

## 6. myShell 控制台

编译增强版 myShell：

```bash
bash scripts_myshell/compile_myshell.sh
./myshell/tsh_search
```

进入后：

```text
search-tsh> sehelp
search-tsh> secrawl 40 &
search-tsh> jobs
search-tsh> sehadoop 40 10
search-tsh> seshell 10
```

myShell 主机是 edge/client 节点，只负责运行爬虫、提交 Hadoop 作业和启动查询；真正执行 MapReduce 的是 Hadoop 集群中的 NodeManager。

## 7. 与同学版本的关系

本项目学习了同学版本的三个点：

1. **三级 Job 划分**：将索引构建拆成预处理、posting 统计、TF-IDF 排名与索引组织。
2. **PorterStemmer**：英文词形归一化，提升 `schedule/scheduling/scheduled` 等词族召回。
3. **Combiner/局部聚合思想**：Job 2 在 Mapper 中先按文档聚合 positions，并使用 Combiner 进一步减少 shuffle 数据。

但本项目没有照搬其多 txt 文件输入方式，而是保留 `rawData` 统一接口，因此可以同时支持固定文档和爬虫网页。
