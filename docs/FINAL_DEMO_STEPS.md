# 最终演示步骤（推荐）

## 1. 上传代码到 master

```bash
cd ~
unzip SearchEngine-MapReduce-v2-web.zip
cd SearchEngine-MapReduce-v2-web
```

如果你是覆盖旧项目，把压缩包里的内容复制到 `~/SearchEngine-MapReduce` 即可。

## 2. 确认 Hadoop 集群正常

```bash
hdfs dfs -ls /
yarn node -list
```

## 3. 生成自建网页、真实爬虫、Hadoop 三阶段建索引

```bash
bash scripts/run_web_corpus_master.sh 36 10 1 18080
```

看到输出中有：

```text
[crawler] 001 http://127.0.0.1:18080/index.html
[crawler] 002 http://127.0.0.1:18080/hadoop-hdfs-01.html
...
[Done] self-hosted web-corpus search index built.
```

并检查没有 sample：

```bash
grep -c 'sample://' output/crawler/crawler_rawData.txt
```

结果应为 `0`。

## 4. 启动 Web 搜索服务

```bash
bash scripts/start_search_web.sh output/crawler/hadoop 10 8080
```

浏览器访问：

```text
http://master公网IP:8080
```

搜索：

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

## 5. 命令行搜索备用

```bash
java -jar target/search-engine-1.0.0.jar shell \
  output/crawler/hadoop/invertedIndex.txt \
  output/crawler/hadoop/filteredSourceFile.txt \
  10
```

## 6. myShell 可选演示

```bash
bash scripts_myshell/compile_myshell.sh
./myshell/tsh_search
```

进入后：

```text
search-tsh> sehelp
search-tsh> sewebbuild 36 10
search-tsh> seweb 10 8080
search-tsh> q
```
