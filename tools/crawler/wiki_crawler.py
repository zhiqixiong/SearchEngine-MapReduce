#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Conservative MediaWiki/Wikipedia crawler for MiniSearch.

The crawler does not scrape arbitrary HTML pages. It calls the public
MediaWiki API, collects plain-text page extracts, and writes the rawData format:

    DID<TAB>URL<TAB>TITLE CONTENT

Recommended classroom usage:
  python3 tools/crawler/wiki_crawler.py --lang zh --target 30 \
      --output output/wiki/wiki_rawData.txt

If zhwiki is not reachable from the Hadoop master, run this script on an
external machine with Internet access, then scp output/wiki/wiki_rawData.txt to
master and run scripts/run_wiki_existing_master.sh.
"""

import argparse
import json
import random
import re
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Optional
from urllib.parse import urlencode
from urllib.request import Request, urlopen


DEFAULT_ENDPOINTS = {
    "zh": "https://zh.wikipedia.org/w/api.php",
    "en": "https://en.wikipedia.org/w/api.php",
    "mediawiki": "https://www.mediawiki.org/w/api.php",
}

DEFAULT_SEEDS = {
    "zh": "tools/crawler/wiki_seed_titles_zh.txt",
    "en": "tools/crawler/wiki_seed_titles_en.txt",
    "mediawiki": "tools/crawler/wiki_seed_titles_en.txt",
}


@dataclass
class WikiArticle:
    did: int
    title: str
    url: str
    content: str
    source: str


def clean_text(text: str) -> str:
    text = text or ""
    text = text.replace("\t", " ").replace("\r", "\n")
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"[ \u00a0]+", " ", text)
    return text.strip()


def one_line(text: str) -> str:
    return re.sub(r"\s+", " ", text or "").strip()


class WikiCrawler:
    def __init__(self, endpoint: str, user_agent: str, delay: float = 0.5, timeout: int = 20):
        self.endpoint = endpoint
        self.user_agent = user_agent
        self.delay = delay
        self.timeout = timeout
        self.articles: List[WikiArticle] = []
        self.seen_titles = set()
        self.seen_urls = set()

    def api_get(self, params: Dict[str, str]) -> Dict:
        query = urlencode(params)
        url = self.endpoint + "?" + query
        req = Request(url, headers={"User-Agent": self.user_agent, "Accept": "application/json"})
        with urlopen(req, timeout=self.timeout) as resp:
            raw = resp.read()
        return json.loads(raw.decode("utf-8"))

    def add_article(self, title: str, url: str, extract: str, source: str) -> bool:
        title = one_line(title)
        url = one_line(url)
        extract = clean_text(extract)
        if not title or not url or len(extract) < 120:
            return False
        title_key = title.casefold()
        if title_key in self.seen_titles or url in self.seen_urls:
            return False
        self.seen_titles.add(title_key)
        self.seen_urls.add(url)
        did = len(self.articles)
        content = clean_text(title + "\n" + extract)
        self.articles.append(WikiArticle(did, title, url, content, source))
        print(f"[wiki] {did:03d} {title} -> {url}")
        return True

    def fetch_page(self, title: str, exchars: int = 9000) -> bool:
        title = one_line(title)
        if not title:
            return False
        try:
            data = self.api_get({
                "action": "query",
                "format": "json",
                "redirects": "1",
                "prop": "extracts|info",
                "explaintext": "1",
                "exsectionformat": "plain",
                "exchars": str(exchars),
                "inprop": "url",
                "titles": title,
            })
            pages = data.get("query", {}).get("pages", {})
            ok = False
            for page in pages.values():
                if "missing" in page:
                    continue
                page_title = page.get("title", title)
                fullurl = page.get("fullurl") or ""
                extract = page.get("extract") or ""
                ok = self.add_article(page_title, fullurl, extract, "mediawiki-api") or ok
            return ok
        except Exception as exc:
            print(f"[wiki] fetch failed: {title} ({exc})", file=sys.stderr)
            return False
        finally:
            time.sleep(self.delay)

    def search_titles(self, keyword: str, limit: int = 5) -> List[str]:
        try:
            data = self.api_get({
                "action": "query",
                "format": "json",
                "list": "search",
                "srsearch": keyword,
                "srlimit": str(limit),
                "srprop": "",
            })
            titles = [x.get("title", "") for x in data.get("query", {}).get("search", [])]
            return [one_line(t) for t in titles if one_line(t)]
        except Exception as exc:
            print(f"[wiki] search failed: {keyword} ({exc})", file=sys.stderr)
            return []
        finally:
            time.sleep(self.delay)

    def crawl(self, seeds: Iterable[str], target: int, search_expand: bool = True) -> List[WikiArticle]:
        seeds = [one_line(s) for s in seeds if one_line(s) and not one_line(s).startswith("#")]
        for title in seeds:
            if len(self.articles) >= target:
                return self.articles[:target]
            self.fetch_page(title)

        if search_expand and len(self.articles) < target:
            # Expand with search results around the same technical topics. This
            # keeps the corpus close to the course project instead of random pages.
            shuffled = list(seeds)
            random.shuffle(shuffled)
            for keyword in shuffled:
                if len(self.articles) >= target:
                    break
                for title in self.search_titles(keyword, limit=4):
                    if len(self.articles) >= target:
                        break
                    self.fetch_page(title)
        return self.articles[:target]

    def save(self, output: Path) -> None:
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("w", encoding="utf-8") as f:
            for i, art in enumerate(self.articles):
                # Re-number to keep DID compact after filtering.
                content = one_line(art.content)
                f.write(f"{i}\t{art.url}\t{content}\n")
        json_path = output.with_suffix(".json")
        json_path.write_text(json.dumps([asdict(a) for a in self.articles], ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[wiki] rawData written: {output}")
        print(f"[wiki] json written: {json_path}")


def load_seed_titles(path: Path) -> List[str]:
    if not path.exists():
        raise SystemExit(f"[wiki] seed file not found: {path}")
    return [line.strip() for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.strip().startswith("#")]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", default="zh", choices=["zh", "en", "mediawiki"], help="default endpoint/seed set")
    ap.add_argument("--endpoint", default=None, help="override MediaWiki API endpoint")
    ap.add_argument("--seed-file", default=None, help="one title per line")
    ap.add_argument("--titles", nargs="*", default=None, help="additional or replacement titles")
    ap.add_argument("--target", type=int, default=30)
    ap.add_argument("--output", default="output/wiki/wiki_rawData.txt")
    ap.add_argument("--delay", type=float, default=0.5)
    ap.add_argument("--timeout", type=int, default=20)
    ap.add_argument("--no-search-expand", action="store_true", help="only fetch explicit titles")
    ap.add_argument("--user-agent", default="MiniSearchCourseCrawler/1.0 (educational Hadoop MapReduce search engine; contact: course-demo@example.com)")
    args = ap.parse_args()

    endpoint = args.endpoint or DEFAULT_ENDPOINTS[args.lang]
    seed_path = Path(args.seed_file or DEFAULT_SEEDS[args.lang])
    seeds = load_seed_titles(seed_path)
    if args.titles:
        # Put explicit titles first but keep the default seed list as backup.
        seeds = args.titles + seeds

    print(f"[wiki] endpoint={endpoint}")
    print(f"[wiki] target={args.target}, delay={args.delay}s, seedCount={len(seeds)}")
    crawler = WikiCrawler(endpoint, args.user_agent, delay=args.delay, timeout=args.timeout)
    crawler.crawl(seeds, args.target, search_expand=not args.no_search_expand)

    if not crawler.articles:
        print("[wiki] ERROR: no article collected. Try running on a machine with Internet access, or use --lang en/mediawiki.", file=sys.stderr)
        return 2
    if len(crawler.articles) < min(args.target, 5):
        print(f"[wiki] WARN: only {len(crawler.articles)} articles collected; index can still be built but corpus is small.", file=sys.stderr)

    crawler.save(Path(args.output))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
