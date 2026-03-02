# 接口转发服务

一个支持多种HTTP方法和SSE流式传输的接口转发服务。

## 功能特性

- ✅ 支持所有HTTP方法（GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS）
- ✅ 动态目标URL配置（通过请求头或参数指定）
- ✅ 请求头处理和转发
- ✅ SSE流式传输支持（适用于AI对话等场景）

## 快速开始

### 启动服务

```bash
./mvnw spring-boot:run
```

服务将在 `http://localhost:8080` 启动

## 使用方式

### 1. 普通HTTP请求转发

通过 `X-Target-URL` 请求头指定目标地址：

```bash
curl -X POST http://localhost:8080/proxy/api/test \
  -H "X-Target-URL: https://api.example.com" \
  -H "Content-Type: application/json" \
  -d '{"message": "hello"}'
```

或通过查询参数指定：

```bash
curl "http://localhost:8080/proxy/api/test?targetUrl=https://api.example.com"
```

### 2. SSE流式传输

#### GET请求流式传输

```bash
curl -N http://localhost:8080/proxy/stream/chat \
  -H "X-Target-URL: https://api.openai.com/v1/chat/completions"
```

#### POST请求流式传输（AI对话场景）

```bash
curl -N -X POST http://localhost:8080/proxy/stream/chat \
  -H "X-Target-URL: https://api.openai.com/v1/chat/completions" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -d '{
    "model": "gpt-4",
    "messages": [{"role": "user", "content": "Hello"}],
    "stream": true
  }'
```

## 请求头处理

服务会自动转发大部分请求头，但会过滤以下头：
- `Host` - 自动设置为目标服务器
- `X-Target-URL` - 内部使用，不转发
- `X-Forwarded-*` - 避免代理链问题

## 配置

在 `application.properties` 中可以修改：
- `server.port` - 服务端口（默认8080）
- 日志级别等其他配置

## 技术栈

- Spring Boot 3.5.11
- Java 21
- Java HttpClient（原生，无需额外依赖）
- SseEmitter（用于SSE流式传输）

## 依赖说明

本项目仅使用最小依赖：
- `spring-boot-starter-web` - 提供REST API支持

不需要WebFlux或其他响应式编程库，使用Java原生HttpClient实现所有功能。
