#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""A deterministic HTTP crawler for MiniSearch demo pages.

Output rawData format:
  DID<TAB>URL<TAB>CONTENT
"""
import argparse
import json
import re
import time
from collections import deque
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urldefrag, urljoin, urlparse
from urllib.request import Request, urlopen


class PageParser(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links = []
        self.text_parts = []
        self.title_parts = []
        self._skip = 0
        self._in_title = False

    def handle_starttag(self, tag, attrs):
        t = tag.lower()
        if t in {'script', 'style', 'noscript'}:
            self._skip += 1
        if t == 'title':
            self._in_title = True
        if t == 'a':
            for k, v in attrs:
                if k.lower() == 'href' and v:
                    self.links.append(v)

    def handle_endtag(self, tag):
        t = tag.lower()
        if t in {'script', 'style', 'noscript'} and self._skip > 0:
            self._skip -= 1
        if t == 'title':
            self._in_title = False

    def handle_data(self, data):
        if self._skip:
            return
        text = re.sub(r'\s+', ' ', data).strip()
        if not text:
            return
        if self._in_title:
            self.title_parts.append(text)
        self.text_parts.append(text)

    @property
    def title(self):
        return ' '.join(self.title_parts).strip()

    @property
    def text(self):
        return ' '.join(self.text_parts).strip()


def same_site(seed, url):
    a, b = urlparse(seed), urlparse(url)
    return (a.scheme, a.netloc) == (b.scheme, b.netloc)


def fetch(url, timeout=8):
    req = Request(url, headers={'User-Agent': 'MiniSearchCrawler/1.0'})
    with urlopen(req, timeout=timeout) as resp:
        ctype = resp.headers.get('Content-Type', '')
        if 'text/html' not in ctype and 'application/xhtml' not in ctype and ctype:
            return None
        data = resp.read(1024 * 1024)
    return data.decode('utf-8', errors='ignore')


def crawl(seed, target=40, delay=0.05):
    q = deque([seed])
    seen = set()
    articles = []
    while q and len(articles) < target:
        url = q.popleft()
        url, _frag = urldefrag(url)
        if url in seen:
            continue
        seen.add(url)
        try:
            html = fetch(url)
            if not html:
                continue
            parser = PageParser()
            parser.feed(html)
            text = parser.text
            if len(text) >= 80:
                articles.append({'url': url, 'title': parser.title or url, 'content': text})
                print(f"[crawler] {len(articles):03d} {url}")
            for link in parser.links:
                nxt, _ = urldefrag(urljoin(url, link))
                if same_site(seed, nxt) and nxt not in seen:
                    q.append(nxt)
            time.sleep(delay)
        except Exception as e:
            print(f"[crawler] skip {url}: {e}")
    return articles


def write_raw(articles, output):
    p = Path(output)
    p.parent.mkdir(parents=True, exist_ok=True)
    with p.open('w', encoding='utf-8') as f:
        for i, a in enumerate(articles):
            content = re.sub(r'\s+', ' ', a['content']).strip()
            f.write(f"{i}\t{a['url']}\t{content}\n")
    (p.parent / 'crawler_articles.json').write_text(json.dumps(articles, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f"[crawler] rawData written: {p}")
    print(f"[crawler] json written: {p.parent / 'crawler_articles.json'}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--seed', required=True, help='seed URL, e.g. http://127.0.0.1:18080/index.html')
    ap.add_argument('--target', type=int, default=40)
    ap.add_argument('--output', default='output/crawler/crawler_rawData.txt')
    ap.add_argument('--delay', type=float, default=0.05)
    args = ap.parse_args()
    articles = crawl(args.seed, args.target, args.delay)
    if not articles:
        raise SystemExit('[crawler] ERROR: no article collected')
    write_raw(articles, args.output)


if __name__ == '__main__':
    main()
