# LogFox

<img src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="200" />

又一款 Android LogCat 阅读器

[![license](https://img.shields.io/github/license/F0x1d/LogFox)](./LICENSE)
[![release](https://img.shields.io/github/v/release/F0x1d/LogFox)](https://github.com/F0x1d/LogFox/releases/latest)
[![downloads](https://img.shields.io/github/downloads/F0x1d/LogFox/total)](https://github.com/F0x1d/LogFox/releases/latest)

## 功能特性

- [Shizuku](https://shizuku.rikka.app/)、Root 和 ADB 支持
- 记录日志并导出为包含设备信息的 ZIP 文件
- 监控并获取 Java/JNI 崩溃和 ANR 通知
- 强大的日志过滤器
- 无障碍支持
- Material You 设计
- **MCP 服务器** - 支持模型上下文协议，可与 AI 编码助手集成（Cursor、Continue 等）

## 下载

<a href="https://f-droid.org/packages/com.f0x1d.logfox">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
         alt="在 F-Droid 上获取"
         height="80" />
</a>

## 截图

<p align="center">
  <img src="./metadata/en-US/images/phoneScreenshots/1.png" width="30%" />
  <img src="./metadata/en-US/images/phoneScreenshots/2.png" width="30%" />
  <img src="./metadata/en-US/images/phoneScreenshots/3.png" width="30%" />
  <img src="./metadata/en-US/images/phoneScreenshots/4.png" width="30%" />
  <img src="./metadata/en-US/images/phoneScreenshots/5.png" width="30%" />
</p>

## 反馈

**提交 Issue 时必须附带崩溃日志，否则将不予采纳。**

- 问题现象和复现步骤可选择性提供
- **崩溃日志是必须的，缺少崩溃日志的 Issue 将直接关闭**

## 开发文档

- [入门指南](doc/01-getting-started.md) - 环境变量、构建命令、构建要求、常见问题
- [架构说明](doc/02-architecture.md) - 模块组织、项目结构、MCP 模块架构
- [MCP 功能设计](doc/03-mcp-api.md) - MCP Server 端点、工具、过滤参数、请求/响应格式、认证、WebSocket
- [CI/CD 操作流程](doc/04-cicd.md) - 提交代码、下载构建日志、快速定位错误

## 许可证

```txt
Copyright (C) 2022-2026 Maksim Zoteev

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```
