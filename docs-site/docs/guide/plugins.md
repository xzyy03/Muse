# 插件

插件把「工具」交给 AI，而不用改 App 本身：一个插件是一个目录，里面放一份 `plugin.json` 清单声明工具，宿主启动时读清单、把工具注册进工具箱，调用时在容器里执行你指定的命令。

插件不加载任何框架代码，也不跑在 App 进程里 —— App 只读声明、只执行你的脚本。与技能的分工：**技能**告诉 AI「怎么做」（流程与知识），**插件**给 AI「多一只手」（可调用的工具）。

## 目录

插件放在 AI 配置目录下：

```
~/.muse/plugins/
└── wordcount/              每个插件一个子目录
    ├── plugin.json         清单（必需）
    ├── README.md           说明（可选，详情页展示）
    └── wordcount.sh        脚本等任意文件
```

`~/.muse/plugins.json` 记录被禁用的插件 id（`{"disabled": ["wordcount"]}`），可在设置里开关，也可手工编辑。

## plugin.json

```json
{
  "id": "wordcount",
  "name": "字数统计",
  "version": "1.0.0",
  "description": "统计文本的字符数、行数与词数",
  "author": "你",
  "tools": [
    {
      "name": "word_count",
      "description": "统计一段文本的字符数、行数与词数",
      "command": "{dir}/wordcount.sh",
      "timeout": 30,
      "parameters": [
        {
          "name": "text",
          "type": "string",
          "description": "要统计的文本",
          "required": true
        }
      ]
    }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 插件唯一标识，缺省用目录名 |
| `name` / `version` / `description` / `author` | 展示用信息 |
| `tools[].name` | 工具名，模型看到的就是它；与内置工具同名时会被跳过，不会覆盖内置工具 |
| `tools[].command` | 在容器里执行的 shell 命令，`{dir}` 会替换成插件目录绝对路径 |
| `tools[].timeout` | 超时秒数，默认 120，上限 1800 |
| `tools[].parameters[]` | 参数声明，`type` 取 `string` / `integer` / `boolean` |

## 脚本拿到什么

调用时宿主先导出环境变量，再执行 `command`：

| 变量 | 内容 |
| --- | --- |
| `MUSE_ARGS` | 参数 JSON，如 `{"text":"hello"}` |
| `MUSE_PLUGIN_ID` | 插件 id |
| `MUSE_PLUGIN_DIR` | 插件目录绝对路径 |
| `MUSE_WORKSPACE` | 当前工作区路径，无工作区时为空串 |

**返回值**：脚本的 stdout 就是工具结果，原样交给模型。退出码非 0 时按失败处理，stdout 一并回传作为错误详情。

写 stdout 时注意控制体量：整段输出会进对话上下文，几千行日志既费 token 又挤占上下文。

## 示例：字数统计

`~/.muse/plugins/wordcount/wordcount.sh`：

```sh
#!/bin/sh
exec python3 -c '
import json, os
d = json.loads(os.environ["MUSE_ARGS"])
t = d.get("text", "")
print(f"字符数 {len(t)}")
print(f"行数 {len(t.splitlines())}")
print(f"词数 {len(t.split())}")
'
```

记得给执行权限（`chmod +x`），或者在 `command` 里直接写 `sh {dir}/wordcount.sh`。

## 管理

**设置 → 插件**：查看已安装插件、逐个启停、点开看声明了哪些工具和目录位置，也能在这里删除插件。

开关会立刻生效，不需要重开 App 或新开会话 —— 关掉的插件，其工具会从工具箱里移除。

## 注意

- 插件命令在容器里以当前用户身份执行，权限和 `Bash` 工具一致。只安装你信任的插件。
- 工具名不能与内置工具重名，否则该工具被跳过（在设置页详情里看不到它）。
- 插件目录里的文件不会被 App 覆盖，可以放心放脚本和数据。
