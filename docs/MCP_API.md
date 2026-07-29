# LogFox MCP API 文档

## 服务信息

- **默认端口**: `8765`
- **默认主机**: `0.0.0.0`
- **协议**: HTTP REST + SSE 流式 + WebSocket + JSON-RPC 2.0
- **认证**: `X-API-Key` 请求头（可选，需在服务端配置启用，`/health` 和 `/help` 免认证）

---

## 快速开始

```bash
# 健康检查
curl http://localhost:8765/health

# 查看帮助
curl http://localhost:8765/help

# 带认证的请求（如果启用了 API Key）
curl -H "X-API-Key: your-api-key" http://localhost:8765/logs/stats
```

---

## 目录

1. [REST API](#rest-api)
2. [JSON-RPC (MCP 协议)](#json-rpc-mcp-协议)
3. [MCP 工具集](#mcp-工具集)
4. [WebSocket](#websocket)
5. [管理接口](#管理接口)
6. [完整调用示例](#完整调用示例)

---

## REST API

### 1. GET /health

健康检查。

```bash
curl http://localhost:8765/health
```

响应: `200 OK`

---

### 2. GET /help

获取帮助信息。

```bash
curl http://localhost:8765/help
```

---

### 3. GET /logs

SSE 流式输出所有实时日志。

```bash
curl -N http://localhost:8765/logs
```

响应格式 (`text/event-stream`):
```
data: {"id":12345,"dateAndTime":1234567890123,"uid":"1000","pid":"1234","tid":"1234","packageName":"com.example","level":"INFO","tag":"MyTag","content":"Hello World"}

data: {"id":12346,...}

```

每条日志一个 `data:` 行，空行分隔。

---

### 4. GET /logs/tail

获取最后 N 条日志，可选择性实时跟进。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `lines` | int | 100 | 获取最后 N 条 |
| `follow` | bool | false | 获取快照后是否继续实时跟进 |

```bash
# 获取最后 200 条日志快照
curl -N "http://localhost:8765/logs/tail?lines=200"

# 获取最后 50 条并实时跟进（类似 tail -f）
curl -N "http://localhost:8765/logs/tail?lines=50&follow=true"
```

---

### 5. POST /logs/clear

清空所有日志缓冲区。

```bash
curl -X POST http://localhost:8765/logs/clear
```

响应:
```json
{"result": "cleared"}
```

---

### 6. GET /logs/stats

获取日志统计信息。

| 参数 | 类型 | 说明 |
|------|------|------|
| `package_name` | string | 按包名过滤 |
| `tag` | string | 按标签过滤 |

```bash
# 全局统计
curl http://localhost:8765/logs/stats

# 按包名过滤统计
curl "http://localhost:8765/logs/stats?package_name=com.example"

# 按标签过滤统计
curl "http://localhost:8765/logs/stats?tag=MyTag"
```

响应:
```json
{
  "total": 15234,
  "levels": {
    "V": 2000,
    "D": 8234,
    "I": 3000,
    "W": 1500,
    "E": 500,
    "F": 0
  },
  "lastUpdated": 1234567890123
}
```

---

### 7. POST /logs/batch

批量操作（删除或导出）。

请求体:
```json
{
  "operation": "delete",
  "include": {
    "uid": "1000",
    "pid": "1234",
    "tag": "MyTag",
    "content": "error",
    "case_sensitive": false
  },
  "exclude": {
    "tag": "DebugTag",
    "case_sensitive": false
  },
  "levels": ["E", "W"],
  "limit": 500,
  "format": "csv"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `operation` | string | `"delete"` 或 `"export"` |
| `include` | object | 包含过滤条件（全部匹配），字段: uid/pid/tid/package_name/tag/content/case_sensitive |
| `exclude` | object | 排除过滤条件（任一匹配即排除），字段同上 |
| `levels` | string[] | 日志级别: V/D/I/W/E/F |
| `limit` | int | 最大操作条数 |
| `format` | string | 导出格式: `"csv"` / `"xml"` / `"txt"`（仅 export 操作） |

```bash
# 批量删除所有 Error 和 Warning 日志
curl -X POST http://localhost:8765/logs/batch \
  -H "Content-Type: application/json" \
  -d '{"operation":"delete","levels":["E","W"]}'

# 批量导出为 CSV（触发文件下载）
curl -X POST http://localhost:8765/logs/batch \
  -H "Content-Type: application/json" \
  -d '{"operation":"export","levels":["E"],"format":"csv","limit":1000}' \
  -o errors.csv
```

---

### 8. POST /logs/search

按条件搜索日志，支持分页和多格式输出。

请求体:
```json
{
  "include": {
    "content": "Exception",
    "tag": "CrashHandler",
    "case_sensitive": false
  },
  "exclude": {
    "tag": "GC",
    "case_sensitive": false
  },
  "levels": ["E", "W"],
  "limit": 50,
  "offset": 0,
  "format": "json"
}
```

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `include` | object | - | 包含过滤条件（全部匹配） |
| `exclude` | object | - | 排除过滤条件（任一匹配即排除） |
| `levels` | string[] | - | 日志级别 |
| `limit` | int | - | 返回结果数量 |
| `offset` | int | 0 | 分页偏移量 |
| `format` | string | json | 输出格式: `"json"` / `"csv"` / `"xml"` |

**include/exclude 子字段:**
| 字段 | 类型 | 说明 |
|------|------|------|
| `uid` | string | 用户 ID |
| `pid` | string | 进程 ID |
| `tid` | string | 线程 ID |
| `package_name` | string | 包名 |
| `tag` | string | 标签 |
| `content` | string | 日志内容 |
| `case_sensitive` | bool | 是否大小写敏感（默认 false） |

```bash
# 搜索包含 "Exception" 的错误日志（JSON 格式）
curl -X POST http://localhost:8765/logs/search \
  -H "Content-Type: application/json" \
  -d '{"include":{"content":"Exception"},"levels":["E"],"limit":20}'

# 导出搜索结果到 CSV 文件
curl -X POST http://localhost:8765/logs/search \
  -H "Content-Type: application/json" \
  -d '{"include":{"content":"crash","case_sensitive":false},"format":"csv","limit":500}' \
  -o search_results.csv
```

JSON 格式响应:
```json
{
  "results": [
    {
      "id": 12345,
      "dateAndTime": 1234567890123,
      "uid": "1000",
      "pid": "5678",
      "tid": "5678",
      "packageName": "com.example",
      "level": "ERROR",
      "tag": "CrashHandler",
      "content": "java.lang.Exception: Something went wrong"
    }
  ],
  "total": 150,
  "limit": 20,
  "offset": 0
}
```

---

### 9. POST /logs/export

导出过滤后的日志为文本文件（触发下载）。

请求体:
```json
{
  "include": {
    "tag": "MyApp",
    "case_sensitive": false
  },
  "exclude": {},
  "levels": ["E", "W", "I"],
  "limit": 10000,
  "format": "txt"
}
```

```bash
curl -X POST http://localhost:8765/logs/export \
  -H "Content-Type: application/json" \
  -d '{"levels":["E"],"limit":500,"format":"txt"}' \
  -o logs.txt
```

响应: 纯文本文件下载。

---

### 10. GET /query

获取当前日志过滤查询字符串。

```bash
curl http://localhost:8765/query
```

响应:
```json
{"query": "Exception"}
```

---

### 11. POST /query/set

设置日志过滤查询字符串。

```bash
curl -X POST http://localhost:8765/query/set \
  -H "Content-Type: application/json" \
  -d '{"query": "Exception"}'
```

响应:
```json
{"result": "ok", "query": "Exception"}
```

---

### 12. GET /filters

获取所有已启用的日志过滤器。

```bash
curl http://localhost:8765/filters
```

响应:
```json
{
  "filters": [
    {
      "id": 1,
      "name": "Error Filter",
      "including": true,
      "enabled": true,
      "query": "tag:CrashHandler msg:Exception"
    }
  ],
  "count": 3
}
```

---

### 13. 录制管理

```bash
# 开始录制
curl -X POST http://localhost:8765/record/start

# 停止录制
curl -X POST http://localhost:8765/record/stop

# 列出所有录制
curl http://localhost:8765/record/list

# 获取某个录制详情
curl http://localhost:8765/record/123
```

响应 (`/record/list`):
```json
{
  "recordings": [
    {
      "id": 1,
      "title": "Recording 2026-01-15 14:30:00",
      "lineCount": 5234,
      "startTime": 1234567890000,
      "endTime": 1234570000000
    }
  ],
  "count": 1
}
```

---

### 14. 工具管理（REST 直通）

```bash
# 列出所有 MCP 工具
curl http://localhost:8765/tools

# 直接调用指定工具
curl -X POST http://localhost:8765/tools/clear_logs/call \
  -H "Content-Type: application/json" \
  -d '{}'

curl -X POST http://localhost:8765/tools/search_logs/call \
  -H "Content-Type: application/json" \
  -d '{"include":{"content":"error"},"levels":["E"],"limit":10}'
```

---

## JSON-RPC (MCP 协议)

**端点**: `POST /mcp`
**协议**: JSON-RPC 2.0
**Content-Type**: `application/json`

### server/discover

发现服务端信息。

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "server/discover",
    "params": {}
  }'
```

响应:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "name": "LogFox MCP Server",
    "version": "1.0.0",
    "description": "LogCat reader MCP server for Android",
    "protocolVersions": ["2025-11-25", "2026-07-28"],
    "capabilities": {
      "tools": {}
    }
  }
}
```

---

### tools/list

列出所有可用工具。

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }'
```

响应:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "clear_logs",
        "description": "Clear all logcat buffers.",
        "inputSchema": {
          "type": "object",
          "properties": {}
        }
      },
      {
        "name": "search_logs",
        "description": "按条件搜索历史日志，支持包含/排除模式和多字段过滤",
        "inputSchema": { ... }
      }
    ]
  }
}
```

---

### tools/call

调用指定工具。

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "search_logs",
      "arguments": {
        "include": {
          "content": "Exception",
          "case_sensitive": false
        },
        "levels": ["E"],
        "limit": 5
      }
    }
  }'
```

成功响应:
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "resultType": "complete",
    "content": [
      {
        "type": "text",
        "text": "找到 42 条匹配日志，返回 5 条"
      },
      {
        "type": "data",
        "data": {
          "total": 42,
          "limit": 5,
          "offset": 0,
          "results": [ ... ]
        }
      }
    ]
  }
}
```

工具不存在:
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "error": {
    "code": -32601,
    "message": "Tool not found: unknown_tool"
  }
}
```

工具执行错误:
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "error": {
    "code": -32000,
    "message": "Failed to search logs: ..."
  }
}
```

流式响应（read_logs stream 模式）:
```
data: {"type":"text","content":{"text":"1000 1234 1234 com.example INFO/MyTag: Hello"}}

data: {"type":"text","content":{"text":"1000 1234 1234 com.example DEBUG/MyTag: World"}}

```

---

## MCP 工具集

共 7 个工具，可通过 JSON-RPC `tools/call` 或 REST `POST /tools/{name}/call` 调用。

### clear_logs

清空所有日志缓冲区。

| 参数 | 类型 | 说明 |
|------|------|------|
| 无 | - | - |

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {"name":"clear_logs","arguments":{}}
  }'
```

---

### read_logs

读取日志。支持两种模式。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `mode` | string | `"stream"` | `"stream"` SSE 持续流 / `"dump"` 单次快照 |

```bash
# 获取最新一条日志快照
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {"name":"read_logs","arguments":{"mode":"dump"}}
  }'

# 流式读取实时日志（SSE）
curl -N -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {"name":"read_logs","arguments":{"mode":"stream"}}
  }'
```

---

### search_logs

按条件搜索历史日志。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `include` | object | - | 包含过滤（全部匹配） |
| `exclude` | object | - | 排除过滤（任一匹配即排除） |
| `levels` | string[] | - | 日志级别: V/D/I/W/E/F |
| `limit` | int | 1000 | 返回数量限制 |
| `offset` | int | 0 | 分页偏移量 |

include/exclude 支持的子字段: `uid`, `pid`, `tid`, `package_name`, `tag`, `content`, `case_sensitive`

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "search_logs",
      "arguments": {
        "include": {"tag": "MyApp", "content": "error", "case_sensitive": false},
        "exclude": {"tag": "GC"},
        "levels": ["E", "W"],
        "limit": 20,
        "offset": 0
      }
    }
  }'
```

---

### export_logs

导出日志为文本格式。

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `format` | string | `"txt"` | 导出格式: txt/log |
| `include` | object | - | 包含过滤 |
| `exclude` | object | - | 排除过滤 |
| `levels` | string[] | - | 日志级别 |
| `limit` | int | 50000 | 最大导出条数 |

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "export_logs",
      "arguments": {
        "levels": ["E", "W"],
        "limit": 500
      }
    }
  }'
```

---

### get_query

获取当前日志过滤查询字符串。

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {"name":"get_query","arguments":{}}
  }'
```

---

### set_query

设置日志过滤查询字符串。

| 参数 | 类型 | 说明 |
|------|------|------|
| `query` | string | 过滤查询字符串 |

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {
      "name": "set_query",
      "arguments": {"query": "Exception"}
    }
  }'
```

---

### get_filters

获取所有已启用的日志过滤器。

```bash
curl -X POST http://localhost:8765/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "tools/call",
    "params": {"name":"get_filters","arguments":{}}
  }'
```

---

## WebSocket

**端点**: `ws://localhost:8765/ws`

WebSocket 连接支持实时双向通信，用于推送日志更新和接收命令。

```javascript
// 浏览器 JavaScript 示例
const ws = new WebSocket("ws://localhost:8765/ws");

ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  console.log("Received:", message);
};

ws.send(JSON.stringify({ type: "subscribe", channel: "logs" }));
```

---

## 管理接口

以下接口需要服务在本地或可管理状态下使用。

### 查询历史

```bash
# 列出查询历史
curl http://localhost:8765/history

# 保存查询历史
curl -X POST http://localhost:8765/history \
  -H "Content-Type: application/json" \
  -d '{"query":"Exception","timestamp":1234567890123}'

# 删除指定查询历史
curl -X DELETE http://localhost:8765/history/1
```

### 告警规则

```bash
# 列出告警规则
curl http://localhost:8765/alerts

# 创建告警规则
curl -X POST http://localhost:8765/alerts \
  -H "Content-Type: application/json" \
  -d '{"name":"Error Alert","type":"keyword","keyword":"FATAL","enabled":true}'

# 删除告警规则
curl -X DELETE http://localhost:8765/alerts/1
```

### 日志标签

```bash
# 列出所有标签
curl http://localhost:8765/tags

# 删除指定标签
curl -X DELETE http://localhost:8765/tags/1
```

### 服务管理

```bash
# 获取服务状态
curl http://localhost:8765/server/status

# 配置认证
curl -X POST http://localhost:8765/server/auth \
  -H "Content-Type: application/json" \
  -d '{"enabled":true,"apiKey":"my-secret-key"}'
```

---

## 完整调用示例

### 场景 1: 监控崩溃并搜索相关日志

```bash
#!/bin/bash
HOST="localhost:8765"

# 1. 检查服务
curl -s "$HOST/health" && echo "服务正常"

# 2. 查看日志统计
curl -s "$HOST/logs/stats" | python3 -m json.tool

# 3. 搜索异常日志
curl -s -X POST "$HOST/logs/search" \
  -H "Content-Type: application/json" \
  -d '{
    "include": {"content": "Exception", "case_sensitive": false},
    "levels": ["E", "F"],
    "limit": 10
  }' | python3 -m json.tool

# 4. 导出错误日志
curl -s -X POST "$HOST/logs/export" \
  -H "Content-Type: application/json" \
  -d '{"levels":["E"],"limit":1000}' \
  -o error_logs.txt
```

### 场景 2: JSON-RPC 工作流

```bash
#!/bin/bash
HOST="localhost:8765"

# 1. 发现服务
curl -s -X POST "$HOST/mcp" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"server/discover","params":{}}' \
  | python3 -m json.tool

# 2. 列出工具
curl -s -X POST "$HOST/mcp" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}' \
  | python3 -m json.tool

# 3. 设置查询
curl -s -X POST "$HOST/mcp" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0","id":3,
    "method":"tools/call",
    "params":{"name":"set_query","arguments":{"query":"Crash"}}
  }' | python3 -m json.tool

# 4. 搜索日志
curl -s -X POST "$HOST/mcp" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc":"2.0","id":4,
    "method":"tools/call",
    "params":{
      "name":"search_logs",
      "arguments":{
        "include":{"content":"FATAL","case_sensitive":false},
        "levels":["E","F"],
        "limit":5
      }
    }
  }' | python3 -m json.tool

# 5. 获取当前查询（验证步骤 3）
curl -s -X POST "$HOST/mcp" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"get_query","arguments":{}}}' \
  | python3 -m json.tool
```

### 场景 3: 实时日志流式监控

```bash
# 使用 /logs SSE 端点（无限流）
curl -N http://localhost:8765/logs | while read -r line; do
  if [[ $line == data:* ]]; then
    echo "${line#data: }" | python3 -m json.tool 2>/dev/null || echo "$line"
  fi
done

# 使用 /logs/tail 获取最近 10 条并实时跟进
curl -N "http://localhost:8765/logs/tail?lines=10&follow=true"
```

### 场景 4: Python 脚本调用 MCP

```python
import requests
import json

HOST = "http://localhost:8765"

def mcp_call(method, params=None):
    """发起 JSON-RPC 调用"""
    payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": method,
        "params": params or {}
    }
    r = requests.post(f"{HOST}/mcp", json=payload)
    return r.json()

# 发现服务
info = mcp_call("server/discover")
print(f"Server: {info['result']['name']} v{info['result']['version']}")

# 搜索错误日志
result = mcp_call("tools/call", {
    "name": "search_logs",
    "arguments": {
        "include": {"content": "Exception", "case_sensitive": False},
        "levels": ["E"],
        "limit": 10
    }
})

if "result" in result:
    content = result["result"]["content"]
    for block in content:
        if block["type"] == "text":
            print(block["text"])
        elif block["type"] == "data":
            data = block["data"]
            print(f"共 {data['total']} 条，返回 {len(data['results'])} 条")
            for log in data["results"]:
                print(f"  [{log['level']}] {log['tag']}: {log['content'][:80]}")

# 清空日志
mcp_call("tools/call", {"name": "clear_logs", "arguments": {}})
print("Logs cleared.")
```

---

## 日志级别对照

| 字母 | 名称 | 说明 |
|------|------|------|
| V | VERBOSE | 详细 |
| D | DEBUG | 调试 |
| I | INFO | 信息 |
| W | WARNING | 警告 |
| E | ERROR | 错误 |
| F | FATAL | 致命 |

---

## API 端点速查表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| GET | `/help` | 帮助信息 |
| GET | `/logs` | 实时日志 SSE 流 |
| GET | `/logs/tail` | 尾部日志（支持跟进） |
| POST | `/logs/clear` | 清空日志 |
| GET | `/logs/stats` | 日志统计 |
| POST | `/logs/batch` | 批量操作（delete/export） |
| POST | `/logs/search` | 搜索日志（支持 csv/xml/json） |
| POST | `/logs/export` | 导出日志文件 |
| GET | `/query` | 获取当前过滤查询 |
| POST | `/query/set` | 设置过滤查询 |
| GET | `/filters` | 获取启用的过滤器 |
| POST | `/record/start` | 开始录制 |
| POST | `/record/stop` | 停止录制 |
| GET | `/record/list` | 列出录制 |
| GET | `/record/{id}` | 录制详情 |
| GET | `/tools` | 列出 MCP 工具 |
| POST | `/tools/{name}/call` | REST 直调工具 |
| POST | `/mcp` | JSON-RPC 端点 |
| WS | `/ws` | WebSocket |
| GET | `/server/status` | 服务状态 |
| POST | `/server/auth` | 配置认证 |
| GET/POST | `/history` | 查询历史管理 |
| DELETE | `/history/{id}` | 删除查询历史 |
| GET/POST | `/alerts` | 告警规则管理 |
| DELETE | `/alerts/{id}` | 删除告警规则 |
| GET | `/tags` | 列出日志标签 |
| DELETE | `/tags/{id}` | 删除日志标签 |

---

## 网络配置

### 端口配置

- 默认端口：`8765`
- 用户可在偏好设置中修改端口号
- 修改端口后自动重启服务应用新端口

### 绑定地址

- 默认地址：`0.0.0.0`（允许局域网访问）
- 可选地址：`127.0.0.1`（仅限本机访问，更安全）
- 用户可在偏好设置中选择绑定地址
- 修改绑定地址后自动重启服务应用新地址
