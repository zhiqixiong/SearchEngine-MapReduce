# SearchEngine-MapReduce

本项目是《云计算与虚拟化技术》课程实验：基于 Hadoop MapReduce 构建一个简易全文搜索引擎，并融合三个部分：

1. **爬虫系统**：自动采集网页/技术文档，输出统一的 `rawData`。
2. **Hadoop/MapReduce 索引系统**：在 HDFS 上完成文本过滤和倒排索引构建。
3. **myShell 总控制台**：复用 Unix 实验中的作业控制 Shell，用来启动和管理爬虫、索引构建和查询任务。

核心思想：

```text
crawler / fixed corpus
        -> rawData
        -> filteredSourceFile
        -> invertedIndexFile
        -> secondaryIndexFile
        -> SearchShell query
```

## 1. 目录结构

```text
SearchEngine-MapReduce/
├── data/
│   ├── ostep/                  # 固定小语料，用于本地调试
│   ├── mock/                   # 模块测试用 mock 数据
│   └── stopwords.txt
├── tools/crawler/
│   ├── enhanced_crawler.py     # 爬虫：输出 rawData 格式
│   └── requirements.txt
├── src/main/java/searchengine/
│   ├── FilterJob.java          # Hadoop map-only 清洗任务
│   ├── InvertedIndexJob.java   # Hadoop 倒排索引任务
│   ├── RawDataPipeline.java    # 本地从 rawData 建索引
│   ├── LocalPipeline.java      # 本地从 data/ostep 建索引
│   └── SearchShell.java        # 查询终端
├── scripts/
│   ├── run_all.sh              # 固定语料 Hadoop 流程
│   ├── run_crawler_local.sh    # 爬虫 + 本地建索引
│   └── run_crawler_hadoop.sh   # 爬虫 + Hadoop 建索引
├── myshell/
│   ├── tsh_filled.c            # 原始 myShell 实现
│   └── tsh_search.c            # 增强版：加入搜索引擎快捷命令
└── scripts_myshell/            # 给 myShell 调用的脚本
```

## 2. Maven 编译

```bash
mvn clean package -DskipTests
```

生成：

```text
target/search-engine-1.0.0.jar
```

## 3. 本地固定语料调试

```bash
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

## 4. 爬虫 + 本地建索引

安装 Python 依赖：

```bash
pip3 install -r tools/crawler/requirements.txt
```

运行：

```bash
bash scripts/run_crawler_local.sh 40 10
java -jar target/search-engine-1.0.0.jar shell output/crawler/local/invertedIndex.txt output/crawler/local/filteredSourceFile.txt 10
```

如果网络不稳定，可只用内置样本文档生成 rawData：

```bash
python3 tools/crawler/enhanced_crawler.py --sample-only --target 8 --output output/crawler/crawler_rawData.txt
java -jar target/search-engine-1.0.0.jar rawlocal output/crawler/crawler_rawData.txt output/crawler/local
```

## 5. 爬虫 + Hadoop 正式流程

```bash
bash scripts/run_crawler_hadoop.sh 40 10
java -jar target/search-engine-1.0.0.jar shell output/crawler/hadoop/invertedIndex.txt output/crawler/hadoop/filteredSourceFile.txt 10
```

这个流程会执行：

```text
crawler -> rawData -> HDFS /search/rawData
        -> Hadoop FilterJob -> HDFS /search/filtered
        -> Hadoop InvertedIndexJob -> HDFS /search/index
        -> getmerge -> SearchShell
```

## 6. myShell 融合方式

在 Linux / WSL / 云主机上编译增强版 myShell：

```bash
bash scripts_myshell/compile_myshell.sh
./myshell/tsh_search
```

进入 `search-tsh>` 后可以使用：

```text
sehelp                    显示搜索引擎快捷命令
secrawl 40                运行爬虫，生成 output/crawler/crawler_rawData.txt
sebuild 40                基于爬虫 rawData 本地建索引
seshell 10                启动 Java SearchShell，TopK=10
sehadoop 40 10            爬虫 + Hadoop FilterJob + InvertedIndexJob
sefixed                   使用固定 data/ostep 语料本地建索引
seclean                   清理输出
jobs                      查看 myShell 后台任务
fg %1 / bg %1             前后台切换任务
```

示例：

```text
search-tsh> secrawl 40 &
search-tsh> jobs
search-tsh> fg %1
search-tsh> sebuild 40
search-tsh> seshell 10
```

这样 myShell 不只是摆设：它作为外层实验控制台，真正管理爬虫和索引构建这类长任务。

## 7. 文件格式

### rawData

```text
DID<TAB>URL_OR_FILENAME<TAB>CONTENT
```

### filteredSourceFile

```text
DID<TAB>URL_OR_FILENAME<TAB>token1 token2 token3 ...
```

### invertedIndexFile

```text
TERM<TAB>DID:rank:pos1,pos2,pos3;DID:rank:pos1,pos2,pos3
```

## 8. 汇报重点

- 爬虫解决“原始数据从哪里来”。
- HDFS 存储 rawData、filteredSourceFile、invertedIndexFile。
- MapReduce 负责离线索引构建，不负责每次用户查询。
- SearchShell 负责在线查询。
- myShell 负责实验任务调度和作业控制，复用 `jobs/fg/bg/SIGCHLD` 机制管理长任务。
