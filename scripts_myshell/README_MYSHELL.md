# myShell 控制台使用说明

`myshell/tsh_search.c` 是在原 myShell 作业控制基础上扩展出的搜索引擎运维控制台。它仍然支持 `jobs / fg / bg / Ctrl+C / Ctrl+Z`，同时增加搜索引擎快捷命令。

## 编译

```bash
bash scripts_myshell/compile_myshell.sh
./myshell/tsh_search
```

## 快捷命令

| 命令 | 作用 |
|---|---|
| `sehelp` | 显示帮助 |
| `sewiki 30 10` | 主展示：Wiki / MediaWiki API 数据源 + Hadoop 三阶段索引 |
| `seweb 10 8080` | 启动 Web 查询服务 |
| `seshell 10` | 启动命令行 SearchShell |
| `sehadoop 8 2` | 固定 OSTEP 文档 Hadoop 三阶段流程 |
| `sewebbuild 36 10` | 离线兜底：自建 HTML 语料 + 爬虫 + Hadoop 三阶段 |
| `secrawl 40 &` | 旧版通用爬虫，本地 rawData 生成 |
| `sebuild 40` | 基于通用爬虫 rawData 跑本地索引流程 |
| `sefixed` | 使用固定 `data/ostep` 文档跑本地索引流程 |
| `seclean` | 清理本地输出 |
| `quit` / `exit` / `q` | 退出 myShell |

## 典型演示

```text
search-tsh> sewiki 30 10
search-tsh> seweb 10 8080
search-tsh> seshell 10
search-tsh> q
```

后台作业控制演示：

```text
search-tsh> sewiki 30 10 &
search-tsh> jobs
search-tsh> fg %1
```

注意：myShell 是运维控制台，不是搜索引擎核心。真正的课程核心仍然是 HDFS + MapReduce 三阶段索引构建。
