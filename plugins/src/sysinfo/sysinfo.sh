#!/bin/sh
echo "主机: $(hostname)"
echo "内核: $(uname -sr)"
echo "架构: $(uname -m)"
echo
echo "磁盘:"
df -h / | tail -n +2
echo
echo "内存:"
free -h 2>/dev/null | head -n 2 || echo "（free 不可用）"
