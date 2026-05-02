# ServiceAgent - 智能客服 Agent

> 基于 Spring AI Alibaba + MCP + RAG 的共享单车智能客服系统

## 📖 项目简介

面向共享单车场景的智能客服 Agent，支持多轮对话、知识库问答、工单查询、投诉处理、地图路线规划等功能。

通过 RAG（检索增强生成）结合 Milvus 向量数据库，实现精准的知识库检索；通过 MCP（Model Context Protocol）协议接入腾讯云和高德地图等外部服务，扩展 Agent 工具能力。

## 🚀 核心特性

- **RAG 知识库问答**：文档上传 → 自动向量化 → 语义检索，基于业务文档精准回答
- **多工具 Agent**：订单查询、投诉历史查询、内部文档检索、时间工具
- **MCP 工具扩展**：
  - 腾讯云 MCP（SSE）：云日志服务 CLS 查询
  - 高德地图 MCP（stdio）：地点搜索、距离计算、骑行路线规划
- **流式输出**：支持 SSE 流式对话，实时返回 AI 回复
- **多轮对话**：基于 sessionId 维护会话上下文

## 🛠️ 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 开发语言 |
| Spring Boot | 3.2.0 | 应用框架 |
| Spring AI Alibaba | - | AI Agent 框架 |
| DashScope | - | 阿里云 AI 服务（qwen-turbo / qwen-max） |
| Milvus | - | 向量数据库 |
| MCP | - | Model Context Protocol 工具协议 |
| Python 3 | - | 高德地图 MCP Server |
| Docker | - | 向量数据库容器化部署 |

## 📦 项目结构

```
ServiceAgent/
├── src/main/java/org/example/
│   ├── agent/tool/                        # Agent 工具集
│   │   ├── DateTimeTools.java             # 时间查询工具
│   │   ├── InternalDocsTools.java         # 内部文档检索（RAG）
│   │   ├── QueryComplaintHistoryTools.java # 投诉历史查询
│   │   └── QueryOrderTools.java           # 订单查询
│   ├── controller/
│   │   ├── ChatController.java            # 对话接口
│   │   └── FileUploadController.java      # 文档上传接口
│   ├── service/
│   │   ├── ChatService.java               # Agent 对话服务
│   │   ├── RagService.java                # RAG 检索服务
│   │   ├── VectorEmbeddingService.java    # 向量化服务
│   │   ├── VectorIndexService.java        # 向量索引服务
│   │   └── VectorSearchService.java       # 向量搜索服务
│   └── config/                            # 配置类
├── src/main/resources/
│   ├── static/                            # Web 测试界面
│   └── application.yml                    # 应用配置（敏感值通过环境变量注入）
├── mcp-server/
│   └── map_server.py                      # 高德地图 MCP Server
├── bike-service-docs/                     # 业务知识库文档
├── .env.example                           # 环境变量模板
├── vector-database.yml                    # Milvus Docker Compose
└── Makefile                               # 快捷命令
```

## ⚙️ 环境配置

复制模板并填写真实值：

```bash
cp .env.example .env
```

编辑 `.env`：

```bash
# 阿里云 DashScope API Key
DASHSCOPE_API_KEY=your_dashscope_api_key_here

# 腾讯云 MCP SSE Token
TENCENT_MCP_SSE_ENDPOINT=/sse/your_token_here

# 高德地图 API Key
AMAP_API_KEY=your_amap_api_key_here

# map_server.py 的本机绝对路径
MAP_SERVER_PATH=/path/to/your/project/mcp-server/map_server.py
```

> `.env` 已加入 `.gitignore`，不会上传到 GitHub。

## 🚀 快速开始

### 1. 启动向量数据库（Milvus）

```bash
docker compose -f vector-database.yml up -d
```

### 2. 安装高德地图 MCP Server 依赖

```bash
cd mcp-server
pip install -r requirements.txt
```

### 3. 启动服务

```bash
mvn spring-boot:run
```

或使用一键启动：

```bash
make init
```

### 4. 上传知识库文档

```bash
# 上传业务文档（自动向量化入库）
curl -X POST http://localhost:9900/api/upload \
  -F "file=@bike-service-docs/01_unlock_failure.md"
```

### 5. 访问 Web 界面

```
http://localhost:9900
```

## 📡 接口说明

### 流式对话（推荐）

```bash
POST /api/chat_stream
Content-Type: application/json

{
  "Id": "session-001",
  "Question": "我的单车解锁失败怎么办？"
}
```

响应为 SSE 流式输出，实时返回 AI 回复内容。

### 普通对话

```bash
POST /api/chat
Content-Type: application/json

{
  "Id": "session-001",
  "Question": "帮我查一下附近的还车点"
}
```

### 文件上传（知识库录入）

```bash
POST /api/upload
Content-Type: multipart/form-data

file: <.txt 或 .md 文件>
```

### 健康检查

```bash
GET /milvus/health
```

## 📚 知识库文档

`bike-service-docs/` 目录下包含以下业务场景文档，上传后自动入库：

| 文件 | 场景 |
|------|------|
| 01_unlock_failure.md | 解锁失败处理 |
| 02_billing_dispute.md | 计费争议 |
| 03_bike_damage_report.md | 车辆损坏上报 |
| 04_deposit_refund.md | 押金退款 |
| 05_account_issues.md | 账户问题 |
| 06_parking_violation.md | 违规停车 |
| 07_compensation_policy.md | 赔偿政策 |
| 08_parking_location.md | 停车点查询 |
| 09_route_planning.md | 路线规划 |
| 10_map_service_guide.md | 地图服务指南 |

---

**版本**: v1.0.0 &nbsp;|&nbsp; **作者**: zlq228 &nbsp;|&nbsp; **许可证**: MIT
