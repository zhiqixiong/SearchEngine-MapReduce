# V3 Wiki-Web 演示步骤

## 主线说明

V3 的主线不是 sample，也不是自建 HTML，而是开放 Wiki / MediaWiki API 数据源：

```text
Wiki API -> wiki_rawData.txt -> HDFS -> FilterJob -> PostingJob -> RankAndSplitIndexJob -> Web Search
```

自建 HTML 语料仍保留，但只作为无外网环境下的可复现兜底模式。

## 方式 A：master 能访问 Wikipedia / MediaWiki API

```bash
cd ~/SearchEngine-MapReduce
bash scripts/run_wiki_master.sh 30 10 1 zh
bash scripts/start_search_web.sh output/wiki/hadoop 10 8080
```

浏览器打开：

```text
http://master公网IP:8080
```

推荐查询：

```text
hadoop
mapreduce
搜索引擎
倒排索引
网络爬虫
云计算
虚拟化
操作系统
python
java
```

## 方式 B：master 无法访问外网，外部机抓取，master 建索引

外部机：

```bash
cd /root/SearchEngine-MapReduce
bash scripts/crawl_wiki_only.sh 30 zh output/wiki/wiki_rawData.txt
scp output/wiki/wiki_rawData.txt hduser_@106.15.47.149:~/SearchEngine-MapReduce/output/wiki/wiki_rawData.txt
```

master：

```bash
cd ~/SearchEngine-MapReduce
bash scripts/run_wiki_existing_master.sh output/wiki/wiki_rawData.txt 10 1
bash scripts/start_search_web.sh output/wiki/hadoop 10 8080
```

## 检查不是 sample 数据

```bash
grep -c 'sample://' output/wiki/wiki_rawData.txt
head -n 3 output/wiki/wiki_rawData.txt
```

`sample://` 计数应该为 0。
