#!/bin/sh
exec python3 -c '
import json, os
d = json.loads(os.environ["MUSE_ARGS"])
t = d.get("text", "")
print("字符数 %d" % len(t))
print("行数 %d" % len(t.splitlines()))
print("词数 %d" % len(t.split()))
'
