package searchengine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SearchWebServer {
    private final SearchQueryService service;
    private final int topK;

    public SearchWebServer(SearchQueryService service, int topK) {
        this.service = service;
        this.topK = topK;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Usage: java searchengine.SearchWebServer <indexFile> <filteredFile> <topK> [port]");
            System.exit(1);
        }
        SearchQueryService service = SearchQueryService.load(Paths.get(args[0]), Paths.get(args[1]));
        int topK = Integer.parseInt(args[2]);
        int port = args.length == 4 ? Integer.parseInt(args[3]) : 8080;
        SearchWebServer app = new SearchWebServer(service, topK);
        app.start(port);
    }

    private void start(int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", this::handleIndex);
        server.createContext("/api/search", this::handleApiSearch);
        server.setExecutor(null);
        server.start();
        System.out.println("MiniSearch Web started at http://0.0.0.0:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed");
            return;
        }
        send(exchange, 200, "text/html; charset=utf-8", indexHtml());
    }

    private void handleApiSearch(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "application/json; charset=utf-8", "{\"error\":\"method not allowed\"}");
            return;
        }
        Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
        String query = params.getOrDefault("q", "").trim();
        int k = topK;
        if (params.containsKey("k")) {
            try {
                k = Math.max(1, Math.min(50, Integer.parseInt(params.get("k"))));
            } catch (NumberFormatException ignored) {
            }
        }
        List<SearchResult> results = service.search(query, k);
        StringBuilder json = new StringBuilder();
        json.append("{\"query\":\"").append(jsonEscape(query)).append("\",\"count\":")
                .append(results.size()).append(",\"results\":[");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append('{')
                    .append("\"rank\":").append(i + 1).append(',')
                    .append("\"did\":").append(r.getDid()).append(',')
                    .append("\"url\":\"").append(jsonEscape(r.getFilename())).append("\",")
                    .append("\"score\":").append(String.format(java.util.Locale.US, "%.6f", r.getScore())).append(',')
                    .append("\"positions\":\"").append(jsonEscape(joinPositions(r))).append("\",")
                    .append("\"summary\":\"").append(jsonEscape(r.getSummary())).append("\",")
                    .append("\"highlightedSummary\":\"").append(jsonEscape(r.getHighlightedSummary())).append("\"")
                    .append('}');
        }
        json.append("]}");
        send(exchange, 200, "application/json; charset=utf-8", json.toString());
    }

    private static String joinPositions(SearchResult r) {
        StringBuilder b = new StringBuilder();
        List<Integer> positions = r.getPositions();
        for (int i = 0; i < positions.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(positions.get(i));
        }
        return b.toString();
    }

    private static Map<String, String> parseQuery(String raw) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            map.put(urlDecode(key), urlDecode(value));
        }
        return map;
    }

    private static String urlDecode(String s) throws IOException {
        return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private static String jsonEscape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 32) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.toString();
    }

    private static String indexHtml() {
        return "<!doctype html>\n" +
                "<html lang=\"zh-CN\"><head><meta charset=\"utf-8\">\n" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
                "<title>MiniSearch</title>\n" +
                "<style>\n" +
                "body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Arial,sans-serif;margin:0;background:#f6f8fb;color:#182033}.wrap{max-width:980px;margin:0 auto;padding:48px 20px}.hero{background:white;border-radius:22px;padding:34px;box-shadow:0 18px 50px rgba(22,34,51,.08)}h1{margin:0 0 10px;font-size:42px}.sub{color:#667085;margin-bottom:24px}.bar{display:flex;gap:10px}input{flex:1;font-size:18px;padding:15px 18px;border:1px solid #d0d5dd;border-radius:14px}button{font-size:17px;padding:0 24px;border:0;border-radius:14px;background:#2563eb;color:white;cursor:pointer}.meta{margin:20px 0;color:#667085}.card{background:white;margin:14px 0;padding:20px;border-radius:18px;border:1px solid #e5e7eb}.rank{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:50%;background:#eff6ff;color:#1d4ed8;font-weight:700}.url{margin-left:10px;color:#175cd3;word-break:break-all}.score{color:#667085;font-size:14px;margin:8px 0}.summary{line-height:1.75}.empty{padding:30px;text-align:center;color:#667085}mark{background:#fde68a;border-radius:4px;padding:1px 3px}.tips{display:flex;gap:8px;flex-wrap:wrap;margin-top:14px}.tip{font-size:13px;border:1px solid #d0d5dd;border-radius:999px;padding:6px 10px;background:#fff;color:#344054;cursor:pointer}\n" +
                "</style></head><body><div class=\"wrap\"><div class=\"hero\">\n" +
                "<h1>MiniSearch</h1><div class=\"sub\">基于 HDFS + MapReduce 三阶段索引构建的小型分布式搜索引擎</div>\n" +
                "<div class=\"bar\"><input id=\"q\" placeholder=\"输入 hadoop、mapreduce、倒排索引、爬虫、python...\" autofocus><button onclick=\"go()\">搜索</button></div>\n" +
                "<div class=\"tips\"><span class=\"tip\" onclick=\"setq('hadoop')\">hadoop</span><span class=\"tip\" onclick=\"setq('mapreduce')\">mapreduce</span><span class=\"tip\" onclick=\"setq('倒排索引')\">倒排索引</span><span class=\"tip\" onclick=\"setq('爬虫')\">爬虫</span><span class=\"tip\" onclick=\"setq('搜索引擎')\">搜索引擎</span><span class=\"tip\" onclick=\"setq('python crawler')\">python crawler</span></div>\n" +
                "</div><div id=\"meta\" class=\"meta\"></div><div id=\"results\"></div></div>\n" +
                "<script>\n" +
                "const q=document.getElementById('q'), meta=document.getElementById('meta'), results=document.getElementById('results');q.addEventListener('keydown',e=>{if(e.key==='Enter')go()});function setq(s){q.value=s;go()}async function go(){const v=q.value.trim();if(!v)return;meta.textContent='Searching...';results.innerHTML='';const r=await fetch('/api/search?q='+encodeURIComponent(v)+'&k=10');const data=await r.json();meta.textContent='Query: '+data.query+' · '+data.count+' result(s)';if(!data.results.length){results.innerHTML='<div class=empty>No result found.</div>';return;}results.innerHTML=data.results.map(x=>`<div class=card><span class=rank>${x.rank}</span><span class=url>${escapeHtml(x.url)}</span><div class=score>score: ${x.score} · positions: ${escapeHtml(x.positions)}</div><div class=summary>${x.highlightedSummary}</div></div>`).join('')}function escapeHtml(s){return (s||'').replace(/[&<>]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]))}\n" +
                "</script></body></html>";
    }
}
