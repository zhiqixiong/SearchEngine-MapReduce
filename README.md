# MiniSearch：基于 HDFS + MapReduce 的分布式小型搜索引擎

本项目面向课程实验，核心目标是用 **HDFS + Hadoop MapReduce** 实现一个可演示的小型搜索引擎。最终版本包含：

- 自建可复现网页语料站点
- 真实 HTTP 爬虫，不再依赖 `sample://` 兜底数据
- HDFS 存储 rawData / filtered / postings / index
- 三阶段 MapReduce 索引构建
- TF-IDF 排名、位置列表、摘要、高亮
- 命令行 SearchShell
- Java 内置 Web 查询服务和前端页面
- 可选 myShell 运维控制台

---

## 1. 五台机器分工

推荐部署方式：

| 机器 | 角色 |
|---|---|
| master | NameNode、ResourceManager、Job 提交节点、Web 查询服务 |
| slave1 | DataNode、NodeManager、MapTask / ReduceTask |
| slave2 | DataNode、NodeManager、MapTask / ReduceTask |
| slave3 | DataNode、NodeManager、MapTask / ReduceTask |
| 外部机 | 可选 crawler / 运维入口；若与 Hadoop 私网不通，不直接提交 HDFS 写入 |

本项目最稳演示方式是在 **master** 上运行脚本，因为 master 可以访问 `172.26.112.x` 私网 DataNode。

---

## 2. 核心数据流

```text
自建 HTML 站点
    ↓ 真实 HTTP 爬虫
crawler_rawData.txt
    ↓ 上传 HDFS
/search/rawData
    ↓ Job-1 FilterJob
/search/filtered
    ↓ Job-2 PostingJob
/search/postings
    ↓ Job-3 RankAndSplitIndexJob
/search/index
    ↓ getmerge
output/crawler/hadoop/invertedIndex.txt
    ↓ SearchShell / Web 前端
查询结果
```

---

## 3. rawData 格式

```text
DID<TAB>URL_OR_FILENAME<TAB>CONTENT
```

例如：

```text
0    http://127.0.0.1:18080/hadoop-hdfs-01.html    Hadoop 与 HDFS 架构 ...
```

---

## 4. 三阶段 MapReduce Job

### Job 1：FilterJob

输入：`/search/rawData`

输出：`/search/filtered`

职责：文本清洗、英文分词、中文 bigram、去停用词，保留 token 顺序。

### Job 2：PostingJob

输入：`/search/filtered`

输出：`/search/postings`

职责：统计每个 term 在每篇文档中的出现次数、文档长度和 positions。

输出格式：

```text
term<TAB>DID:URL:docLen:tfCount:pos1,pos2;...
```

### Job 3：RankAndSplitIndexJob

输入：`/search/postings`

输出：`/search/index`

职责：计算 DF、IDF、TF-IDF，生成最终倒排索引。

输出格式：

```text
term<TAB>DID:score:pos1,pos2;DID:score:pos1,...
```

---

## 5. master 上一键演示：自建网页 + 爬虫 + Hadoop + Web

确保 Hadoop 已启动：

```bash
hdfs dfs -ls /
yarn node -list
```

进入项目：

```bash
cd ~/SearchEngine-MapReduce
```

一键生成自建网页语料、启动真实 HTTP 服务、爬虫抓取、跑三阶段 MapReduce：

```bash
bash scripts/run_web_corpus_master.sh 36 10 1 18080
```

参数含义：

```text
36     生成网页数量
10     查询 TopK
1      reducer 数量
18080  自建网页站点临时 HTTP 端口
```

启动 Web 搜索服务：

```bash
bash scripts/start_search_web.sh output/crawler/hadoop 10 8080
```

浏览器访问：

```text
http://master公网IP:8080
```

可以搜索：

```text
hadoop
mapreduce
倒排索引
爬虫
搜索引擎
python crawler
中文分词
五台机器
```

---

## 6. 命令行查询

```bash
java -jar target/search-engine-1.0.0.jar shell \
  output/crawler/hadoop/invertedIndex.txt \
  output/crawler/hadoop/filteredSourceFile.txt \
  10
```

退出：

```text
exit
quit
q
```

---

## 7. 固定 OSTEP 文档演示

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

## 8. myShell 运维控制台（可选）

编译：

```bash
bash scripts_myshell/compile_myshell.sh
```

启动：

```bash
./myshell/tsh_search
```

常用命令：

```text
sehelp
sehadoop 8 2
sewebbuild 36 10
seweb 10 8080
seshell 10
jobs
fg %1
bg %1
quit / exit / q
```

说明：myShell 不作为搜索引擎核心，而作为 SearchOpsShell 运维控制台，负责启动爬虫、构建索引、启动查询服务以及展示作业控制能力。

---

## 9. 为什么采用自建网页语料

公网爬虫容易受到反爬、动态加载、网络不稳定和正文选择器失效的影响。为了保证实验可复现，本项目构建了一个小型 HTML 站点，并通过真实 HTTP 服务发布，再由爬虫抓取。

这不是 `sample://` 假数据，而是：

```text
HTML 页面 → HTTP 服务 → Crawler → rawData → HDFS → MapReduce → Search
```

既保留了真实爬虫流程，又保证课堂演示稳定。

---

## 10. 项目目录

```text
src/main/java/searchengine/       Java 核心代码、MapReduce Job、Search Web Server
tools/web_corpus/                 自建 HTML 语料生成器
tools/crawler/                    HTTP 爬虫
scripts/                          Hadoop / Web 一键脚本
scripts_myshell/                  myShell 运维脚本
myshell/                          tiny shell + SearchOpsShell 命令
data/ostep/                       固定文档演示数据
```

