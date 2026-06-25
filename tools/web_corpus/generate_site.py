#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate a reproducible mini web corpus for the search-engine demo.

The generated pages are real HTML pages with links. They can be served by
`python3 -m http.server` and crawled through HTTP, avoiding unstable public-site
anti-crawler problems while keeping the crawler pipeline real and reproducible.
"""
import argparse
import html
import json
from pathlib import Path

TOPICS = [
    ("hadoop-hdfs", "Hadoop 与 HDFS 架构", ["hadoop", "hdfs", "namenode", "datanode", "block", "replication", "分布式存储", "数据块", "副本", "容错"]),
    ("mapreduce-flow", "MapReduce 作业流程", ["mapreduce", "mapper", "reducer", "shuffle", "combiner", "partitioner", "分布式计算", "批处理", "任务调度"]),
    ("inverted-index", "倒排索引与位置列表", ["inverted", "index", "posting", "positions", "tfidf", "倒排索引", "词项", "位置列表", "文档频率"]),
    ("search-ranking", "搜索引擎排名模型", ["search", "ranking", "tfidf", "bm25", "score", "query", "搜索引擎", "相关性", "排名", "摘要"]),
    ("python-crawler", "Python 爬虫与网页采集", ["python", "crawler", "requests", "html", "link", "url", "爬虫", "网页", "正文提取", "去重"]),
    ("java-web", "Java 查询服务与 Web 前端", ["java", "httpserver", "api", "json", "frontend", "web", "查询服务", "前端", "接口", "高亮"]),
    ("hdfs-cluster", "五机 Hadoop 集群部署", ["cluster", "master", "slave", "yarn", "resourcemanager", "nodemanager", "五台机器", "集群", "调度", "节点"]),
    ("ops-shell", "SearchOpsShell 运维控制台", ["shell", "myshell", "jobs", "fg", "bg", "signal", "运维", "控制台", "后台任务"]),
    ("near-realtime", "准实时搜索与批量更新", ["realtime", "batch", "incremental", "reload", "index", "准实时", "批处理", "增量", "重建索引"]),
    ("chinese-token", "中文分词与 Bigram", ["token", "bigram", "segment", "chinese", "中文", "分词", "搜索", "索引", "关键词"]),
    ("pagerank-link", "链接图与 PageRank 扩展", ["pagerank", "link", "graph", "authority", "web", "链接", "图计算", "网页权重", "扩展"]),
    ("fault-tolerance", "HDFS 容错与副本机制", ["fault", "tolerance", "replication", "heartbeat", "rack", "容错", "副本", "心跳", "恢复"]),
]

PARA = (
    "本页面属于 MiniSearch 自建网页语料。页面通过真实 HTTP 服务发布，再由爬虫模块抓取，"
    "最终转换为 rawData 格式进入 HDFS。系统使用三阶段 MapReduce 进行索引构建："
    "FilterJob 负责文本清洗，PostingJob 负责统计词项位置，RankAndSplitIndexJob 负责计算 TF-IDF 并生成倒排索引。"
)


def page_html(slug, title, words, links):
    body_words = " ".join(words * 5)
    cn = " ".join([w for w in words if any('\u4e00' <= ch <= '\u9fff' for ch in w)])
    en = " ".join([w for w in words if w.isascii()])
    link_html = "\n".join(f'<li><a href="{html.escape(href)}">{html.escape(text)}</a></li>' for href, text in links)
    return f'''<!doctype html>
<html lang="zh-CN">
<head><meta charset="utf-8"><title>{html.escape(title)}</title></head>
<body>
<header><h1>{html.escape(title)}</h1><nav><a href="index.html">返回首页</a></nav></header>
<main>
<article>
<p>{html.escape(PARA)}</p>
<p>关键词：{html.escape(body_words)}</p>
<p>中文主题：{html.escape(cn)}。英文主题：{html.escape(en)}。</p>
<p>这个页面用于测试搜索查询、高亮摘要、多关键词匹配和位置列表。用户可以搜索 hadoop、mapreduce、倒排索引、爬虫、搜索引擎、python crawler、中文分词 等关键词。</p>
<p>在五台机器部署中，master 负责 NameNode、ResourceManager、Web 查询服务和任务提交；slave1、slave2、slave3 负责 DataNode、NodeManager 和 MapReduce 任务；外部节点可以作为爬虫节点或运维入口。</p>
</article>
<section><h2>相关页面</h2><ul>{link_html}</ul></section>
</main>
</body></html>'''


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', default='output/site/www', help='output directory for generated html pages')
    ap.add_argument('--count', type=int, default=36, help='page count to generate')
    args = ap.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    pages = []
    for i in range(args.count):
        topic = TOPICS[i % len(TOPICS)]
        suffix = i // len(TOPICS) + 1
        slug = f"{topic[0]}-{suffix:02d}"
        title = f"{topic[1]} #{suffix}"
        words = topic[2] + TOPICS[(i + 1) % len(TOPICS)][2][:4]
        pages.append((slug, title, words))

    for i, (slug, title, words) in enumerate(pages):
        links = []
        for step in (1, 2, 5):
            target = pages[(i + step) % len(pages)]
            links.append((f"{target[0]}.html", target[1]))
        (out / f"{slug}.html").write_text(page_html(slug, title, words, links), encoding='utf-8')

    index_items = "\n".join(
        f'<li><a href="{html.escape(slug)}.html">{html.escape(title)}</a></li>'
        for slug, title, _ in pages
    )
    index = f'''<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>MiniSearch 自建网页语料</title></head>
<body><h1>MiniSearch 自建网页语料</h1>
<p>这是一个可复现的小型网页站点，用于演示真实 HTTP 爬虫、HDFS 存储、MapReduce 索引构建和 Web 查询。</p>
<ul>{index_items}</ul>
</body></html>'''
    (out / 'index.html').write_text(index, encoding='utf-8')
    manifest = [{'url': f'{slug}.html', 'title': title} for slug, title, _ in pages]
    (out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"[generate_site] generated {len(pages)} pages under {out}")
    print(f"[generate_site] seed: {out / 'index.html'}")


if __name__ == '__main__':
    main()
