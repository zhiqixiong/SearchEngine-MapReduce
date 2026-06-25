# MiniSearch V3：基于 HDFS + MapReduce + Wiki 数据源的分布式小型搜索引擎

本项目面向云计算与虚拟化课程实验，核心是使用 **HDFS + Hadoop MapReduce** 实现一个可演示的小型搜索引擎。V3 版本把主数据源升级为开放 Wiki / MediaWiki API，不再把 `sample://` 或自建 HTML 作为主展示数据。

---

## 1. 项目亮点

- 真实外部开放知识数据源：`tools/crawler/wiki_crawler.py`
- 统一 rawData 输入格式：固定文档、Wiki、网页爬虫都能进入同一套索引流水线
- HDFS 存储：`/search/rawData`、`/search/filtered`、`/search/postings`、`/search/index`
- 三阶段 MapReduce：`FilterJob -> PostingJob -> RankAndSplitIndexJob`
- TF-IDF 排名、positions、摘要、高亮、多关键词查询
- Java 内置 Web 查询服务：`SearchWebServer`
- 命令行 `SearchShell`
- 可选 myShell 运维控制台
- 自建 HTML 语料保留为无外网环境下的 fallback

---

## 2. 五台机器分工

| 机器 | 角色 |
|---|---|
| master | NameNode、ResourceManager、Job 提交节点、Web 查询服务 |
| slave1 | DataNode、NodeManager、MapTask / ReduceTask |
| slave2 | DataNode、NodeManager、MapTask / ReduceTask |
| slave3 | DataNode、NodeManager、MapTask / ReduceTask |
| 外部机 | 可选 Wiki 爬虫节点 / 打包编译节点；如果与 Hadoop 私网不通，不直接写 HDFS |

推荐最稳部署方式：在 **master** 上运行 Hadoop 脚本和 Web 服务；如果 master 访问不了外网，则让外部机只负责 `wiki_rawData.txt` 采集，再 `scp` 到 master。

---

## 3. 核心数据流

```text
Wiki / MediaWiki API
    ↓ wiki_crawler.py
wiki_rawData.txt
    ↓ 上传 HDFS
/search/rawData
    ↓ Job-1 FilterJob
/search/filtered
    ↓ Job-2 PostingJob
/search/postings
    ↓ Job-3 RankAndSplitIndexJob
/search/index
    ↓ getmerge
output/wiki/hadoop/invertedIndex.txt
    ↓ SearchWebServer / SearchShell
查询结果
```

---

## 4. rawData 格式

```text
DID<TAB>URL<TAB>TITLE CONTENT
```

例如：

```text
0    https://zh.wikipedia.org/wiki/Hadoop    Hadoop ... 分布式系统基础架构 ...
```

---

## 5. 三阶段 MapReduce Job

### Job 1：FilterJob

输入：`/search/rawData`

输出：`/search/filtered`

职责：文本清洗、英文分词、中文 bigram、去停用词，保留 token 顺序。

### Job 2：PostingJob

输入：`/search/filtered`

输出：`/search/postings`

职责：统计每个 term 在每篇文档中的出现次数、文档长度和 positions。

### Job 3：RankAndSplitIndexJob

输入：`/search/postings`

输出：`/search/index`

职责：计算 DF、IDF、TF-IDF，生成最终倒排索引。

---

## 6. 编译

在能正常使用 Maven 的机器上：

```bash
cd SearchEngine-MapReduce
JAVA_HOME=/usr/lib/jvm/java-11-openjdk mvn clean package -DskipTests
```

项目编译目标仍是 Java 8 字节码，Hadoop 2.10.2 运行时继续使用 Java 8。

如果 master 没有 Maven，可以在外部机编译后打包传到 master：

```bash
cd /root
tar -czf SearchEngine-MapReduce-built.tar.gz SearchEngine-MapReduce
scp SearchEngine-MapReduce-built.tar.gz hduser_@106.15.47.149:~/
```

master 上：

```bash
cd ~
tar -xzf SearchEngine-MapReduce-built.tar.gz
cd SearchEngine-MapReduce
source ~/.bashrc
```

---

## 7. 主展示：Wiki 数据源 + Hadoop 三阶段 + Web 前端

确保 Hadoop 已启动：

```bash
hdfs dfs -ls /
yarn node -list
```

进入项目：

```bash
cd ~/SearchEngine-MapReduce
```

如果 master 能访问 Wiki API：

```bash
bash scripts/run_wiki_master.sh 30 10 1 zh
```

参数含义：

```text
30  抓取目标文档数
10  查询 TopK
1   reducer 数量
zh  使用中文 Wikipedia / MediaWiki API seed list
```

启动 Web 前端：

```bash
bash scripts/start_search_web.sh output/wiki/hadoop 10 8080
```

浏览器访问：

```text
http://master公网IP:8080
```

推荐搜索：

```text
hadoop
mapreduce
hdfs
搜索引擎
倒排索引
网络爬虫
云计算
虚拟化
操作系统
python
java
```

检查不是 sample 数据：

```bash
grep -c 'sample://' output/wiki/wiki_rawData.txt
head -n 3 output/wiki/wiki_rawData.txt
```

`sample://` 计数应为 0。

---

## 8. 如果 master 无法访问外网

外部机抓取 Wiki 数据：

```bash
cd /root/SearchEngine-MapReduce
bash scripts/crawl_wiki_only.sh 30 zh output/wiki/wiki_rawData.txt
scp output/wiki/wiki_rawData.txt hduser_@106.15.47.149:~/SearchEngine-MapReduce/output/wiki/wiki_rawData.txt
```

master 建索引：

```bash
cd ~/SearchEngine-MapReduce
bash scripts/run_wiki_existing_master.sh output/wiki/wiki_rawData.txt 10 1
bash scripts/start_search_web.sh output/wiki/hadoop 10 8080
```

---

## 9. 固定 OSTEP 文档演示

固定文档用于验证 Hadoop 三阶段核心链路：

```bash
bash scripts/run_all_master.sh 8 2 1
```

查询：

```bash
java -jar target/search-engine-1.0.0.jar shell \
  output/hadoop/invertedIndex.txt \
  output/hadoop/filteredSourceFile.txt \
  2
```

推荐搜索：

```text
schedule
mlfq
memory
file
inode
```

---

## 10. 自建 HTML 兜底模式

如果没有任何外网，可以使用可复现的自建 HTML 语料：

```bash
bash scripts/run_web_corpus_master.sh 36 10 1 18080
bash scripts/start_search_web.sh output/crawler/hadoop 10 8080
```

这不是主展示数据源，只是离线兜底。

---

## 11. 命令行查询

Wiki 索引：

```bash
java -jar target/search-engine-1.0.0.jar shell \
  output/wiki/hadoop/invertedIndex.txt \
  output/wiki/hadoop/filteredSourceFile.txt \
  10
```

退出：

```text
exit
quit
q
```

---

## 12. myShell 运维控制台（可选）

编译：

```bash
bash scripts_myshell/compile_myshell.sh
```

运行：

```bash
./myshell/tsh_search
```

常用命令：

```text
sehelp              查看帮助
sewiki 30 10        Wiki 数据源 + Hadoop 三阶段建索引
seweb 10 8080       启动 Web 查询服务
sehadoop 8 2        固定 OSTEP 文档 Hadoop 三阶段流程
sewebbuild 36 10    离线自建 HTML 兜底流程
seshell 10          命令行查询
jobs / fg / bg      myShell 作业控制
quit / exit / q     退出
```

---

## 13. 答辩表述建议

本项目将搜索引擎拆分为数据采集、分布式存储、离线索引构建和在线查询四层。Wiki 爬虫只负责生成统一 rawData；HDFS 负责保存 rawData、filtered、postings 和最终 index；MapReduce 的三个 Job 分别完成文本标准化、posting 统计和 TF-IDF 排名；Web 服务加载最终索引，为用户提供在线查询页面。这样既保留了 Hadoop/MapReduce 的课程核心，又展示了完整搜索引擎系统形态。
