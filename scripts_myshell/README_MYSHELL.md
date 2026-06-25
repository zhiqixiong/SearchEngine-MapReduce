# myShell 控制台使用说明

`myshell/tsh_search.c` 是在原 myShell 作业控制基础上扩展出的搜索引擎控制台。它仍然支持 `jobs / fg / bg / Ctrl+C / Ctrl+Z`，同时增加搜索引擎快捷命令。

## 编译

```bash
bash scripts_myshell/compile_myshell.sh
./myshell/tsh_search
```

## 快捷命令

| 命令 | 作用 |
|---|---|
| `sehelp` | 显示帮助 |
| `secrawl 40 &` | 后台运行爬虫，生成 `output/crawler/crawler_rawData.txt` |
| `sebuild 40` | 基于爬虫 rawData 跑本地三级索引流程 |
| `sehadoop 40 10` | 基于爬虫 rawData 提交 Hadoop 三级 Job |
| `sefixed` | 使用固定 `data/ostep` 文档跑本地索引流程 |
| `seshell 10` | 启动 SearchShell 查询终端 |
| `seclean` | 清理本地输出 |

## 典型演示

```text
search-tsh> secrawl 40 &
search-tsh> jobs
search-tsh> fg %1
search-tsh> sehadoop 40 10
search-tsh> seshell 10
```

注意：myShell 主机只是 edge/client 节点，负责提交 Hadoop 作业；真正执行 MapReduce 的是 Hadoop 集群的 slave 节点。
