#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Crawler module for SearchEngine-MapReduce.

It collects technical documents and writes them in the rawData format used by
our Java/Hadoop pipeline:

    DID<TAB>URL_OR_TITLE<TAB>CONTENT

The crawler is intentionally conservative: it has random delay, URL de-dup, and
sample fallback documents. If network crawling fails on the cloud server, the
pipeline can still run with sample documents.
"""

import argparse
import json
import random
import re
import sys
import time
from dataclasses import dataclass, asdict
from datetime import datetime
from pathlib import Path
from typing import Iterable, List, Optional
from urllib.parse import quote, urljoin

try:
    import requests
    from bs4 import BeautifulSoup
except Exception:  # keep sample-only mode usable even without dependencies
    requests = None
    BeautifulSoup = None


@dataclass
class Article:
    did: int
    url: str
    title: str
    date: str
    content: str
    source: str


class EnhancedCrawler:
    def __init__(self, min_delay: float = 1.0, max_delay: float = 2.5):
        self.min_delay = min_delay
        self.max_delay = max_delay
        self.articles: List[Article] = []
        self.seen_urls = set()
        self.session = None
        if requests is not None:
            self.session = requests.Session()
            self.session.headers.update({
                "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                              "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
            })

    def delay(self) -> None:
        time.sleep(random.uniform(self.min_delay, self.max_delay))

    def add_article(self, url: str, title: str, content: str, source: str) -> bool:
        url = (url or "local-sample").strip()
        title = self.clean_one_line(title or "untitled")
        content = self.clean_content(content)
        if len(content) < 80:
            return False
        if url in self.seen_urls:
            return False
        self.seen_urls.add(url)
        did = len(self.articles)
        self.articles.append(Article(
            did=did,
            url=url,
            title=title,
            date=datetime.now().strftime("%Y-%m-%d"),
            content=f"{title}\n{content}",
            source=source,
        ))
        return True

    @staticmethod
    def clean_one_line(text: str) -> str:
        return re.sub(r"\s+", " ", text).strip()

    @staticmethod
    def clean_content(text: str) -> str:
        text = re.sub(r"\r\n?", "\n", text or "")
        text = re.sub(r"[ \t]+", " ", text)
        text = re.sub(r"\n{3,}", "\n\n", text)
        text = text.replace("\t", " ")
        return text.strip()

    def fetch(self, url: str) -> Optional[str]:
        if self.session is None:
            return None
        try:
            resp = self.session.get(url, timeout=15)
            resp.raise_for_status()
            # Requests often guesses wrong for Chinese pages; apparent_encoding helps.
            if not resp.encoding or resp.encoding.lower() == "iso-8859-1":
                resp.encoding = resp.apparent_encoding
            return resp.text
        except Exception as exc:
            print(f"[crawler] fetch failed: {url} ({exc})")
            return None

    def extract_article(self, url: str, source: str) -> bool:
        if BeautifulSoup is None:
            return False
        html = self.fetch(url)
        if not html:
            return False
        soup = BeautifulSoup(html, "html.parser")

        # Remove high-noise nodes first.
        for tag in soup.select("script,style,noscript,nav,footer,header,aside,form"):
            tag.decompose()

        title = ""
        for selector in ["h1.title-article", "h1.article-title", "h1.post-title", "h1.entry-title", "h1", "title"]:
            node = soup.select_one(selector)
            if node and node.get_text(strip=True):
                title = node.get_text(" ", strip=True)
                break
        if not title:
            title = url

        content = ""
        selectors = [
            "div.article-content", "div.markdown_views", "article", "main article",
            "div.post-content", "div.entry-content", "div.article-body", "div.postBody",
            "div#cnblogs_post_body", "div.content", "main", "body"
        ]
        for selector in selectors:
            node = soup.select_one(selector)
            if not node:
                continue
            pieces = []
            for p in node.find_all(["p", "li", "h2", "h3", "pre", "code"]):
                text = self.clean_one_line(p.get_text(" ", strip=True))
                if 10 <= len(text) <= 1200 and not self.is_noise(text):
                    pieces.append(text)
            content = "\n".join(pieces[:60])
            if len(content) >= 150:
                break
        return self.add_article(url, title, content, source)

    @staticmethod
    def is_noise(text: str) -> bool:
        lowers = text.lower()
        noise_words = ["广告", "关注", "扫码", "公众号", "登录", "注册", "copyright", "评论", "点赞", "收藏"]
        return any(w in lowers for w in noise_words)

    def create_sample_articles(self) -> None:
        samples = [
            ("sample://python-crawler", "Python 爬虫基础", "Python crawler uses requests to fetch pages and BeautifulSoup to parse HTML. 爬虫程序需要处理网页下载、链接提取、正文清洗、去重和随机延时。"),
            ("sample://mapreduce-search", "MapReduce 搜索引擎", "Hadoop MapReduce builds an inverted index from many documents. Mapper emits term and document information, shuffle groups the same term, and reducer writes posting lists. 搜索引擎通过倒排索引加速查询。"),
            ("sample://tfidf", "TF IDF 排序", "TF IDF ranks documents by term frequency and inverse document frequency. A term that appears frequently in one document but rarely in the corpus receives higher score. 相关度排序用于返回 top documents。"),
            ("sample://hdfs", "HDFS 分布式存储", "HDFS stores rawData, filteredSourceFile and invertedIndexFile. Hadoop jobs read input splits from HDFS and write results back to HDFS. 云计算课程强调分布式存储和批处理计算。"),
            ("sample://myshell", "myShell 任务控制", "myShell can run crawler and index building scripts as foreground or background jobs. jobs fg bg and signal handling help manage long running search engine tasks on Linux cloud server."),
            ("sample://java-searchshell", "Java 查询 Shell", "SearchShell loads inverted index and filtered documents. User types a query term such as python, crawler, memory, file or hadoop and the shell returns ranked results and summaries."),
            ("sample://web", "Web 开发文档", "Web development includes HTML CSS JavaScript server and database. Technical documents can be collected by crawler and converted into rawData format for indexing."),
            ("sample://database", "数据库索引", "Database index and search engine index both reduce query cost. Inverted index maps terms to documents and positions, while B tree index maps keys to records."),
        ]
        for url, title, content in samples:
            self.add_article(url, title, content, "sample")

    def discover_cnblogs(self, keywords: Iterable[str], limit: int) -> None:
        if BeautifulSoup is None:
            return
        for keyword in keywords:
            if len(self.articles) >= limit:
                return
            url = f"https://www.cnblogs.com/s?q={quote(keyword)}"
            html = self.fetch(url)
            if not html:
                continue
            soup = BeautifulSoup(html, "html.parser")
            for a in soup.find_all("a", href=True):
                href = a["href"]
                if "cnblogs.com" in href and "/p/" in href:
                    full_url = href if href.startswith("http") else urljoin("https://www.cnblogs.com", href)
                    if full_url in self.seen_urls:
                        continue
                    print(f"[crawler] cnblogs article: {full_url}")
                    self.extract_article(full_url, "cnblogs")
                    self.delay()
                    if len(self.articles) >= limit:
                        return
            self.delay()

    def crawl_fixed_urls(self, urls: Iterable[str], limit: int) -> None:
        for url in urls:
            if len(self.articles) >= limit:
                return
            if url in self.seen_urls:
                continue
            print(f"[crawler] fixed article: {url}")
            self.extract_article(url, "fixed")
            self.delay()

    def crawl(self, target: int = 40, sample_first: bool = True, network: bool = True) -> List[Article]:
        if sample_first:
            self.create_sample_articles()
        if network and len(self.articles) < target:
            self.discover_cnblogs(["Python 爬虫", "Hadoop MapReduce", "搜索引擎", "倒排索引", "Web 开发"], target)
        if network and len(self.articles) < target:
            self.crawl_fixed_urls([
                "https://www.runoob.com/python/python-tutorial.html",
                "https://www.runoob.com/html/html-tutorial.html",
                "https://www.runoob.com/java/java-tutorial.html",
            ], target)
        return self.articles[:target]

    def save_rawdata(self, output: Path) -> None:
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("w", encoding="utf-8") as f:
            for i, article in enumerate(self.articles):
                # Renumber continuously because network crawling may have skipped URLs.
                content = self.clean_one_line(article.content)
                f.write(f"{i}\t{article.url}\t{content}\n")
        print(f"[crawler] rawData written: {output} ({len(self.articles)} docs)")

    def save_json(self, output: Path) -> None:
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("w", encoding="utf-8") as f:
            json.dump([asdict(a) for a in self.articles], f, ensure_ascii=False, indent=2)
        print(f"[crawler] json written: {output}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate crawler rawData for SearchEngine-MapReduce")
    parser.add_argument("--target", type=int, default=40, help="target document count")
    parser.add_argument("--output", default="output/crawler/crawler_rawData.txt", help="rawData output path")
    parser.add_argument("--json", default="output/crawler/crawler_articles.json", help="article metadata json path")
    parser.add_argument("--sample-only", action="store_true", help="do not access network; only use built-in samples")
    parser.add_argument("--no-sample", action="store_true", help="do not create built-in samples first")
    args = parser.parse_args()

    crawler = EnhancedCrawler()
    crawler.crawl(target=args.target, sample_first=not args.no_sample, network=not args.sample_only)
    crawler.save_rawdata(Path(args.output))
    crawler.save_json(Path(args.json))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
