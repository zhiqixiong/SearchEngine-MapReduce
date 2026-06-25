# myShell integration commands

Run from project root:

```bash
gcc -Wall -Wextra -g -D_GNU_SOURCE -o myshell/tsh_search myshell/tsh_search.c
./myshell/tsh_search
```

Inside `tsh>`:

```text
sehelp                    # show search-engine shortcuts
secrawl 40                # run crawler only
sebuild 40                # build local index from crawler rawData
seshell 10                # start Java SearchShell with topK=10
sehadoop 40 10            # crawler + Hadoop FilterJob + Hadoop InvertedIndexJob
seclean                   # clean generated outputs
jobs                      # myShell job list
fg %1 / bg %1             # myShell job control
```

All shortcuts are expanded to scripts in `scripts_myshell/`, so job control still uses the original `fork/execve/waitpid/SIGCHLD` path.
