<div align="center">
  <img src="src/main/resources/webui/image/logo.png" alt="AI Orchestrator" width="120">
  <h1>AI Orchestrator</h1>
  <p><strong>AI CLIツール向けマルチエージェントオーケストレーター</strong></p>
</div>

[![Java](https://img.shields.io/badge/Java-21%2B-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.8%2B-blue?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE.md)
[![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey)](#)

<div align="center">
  <a href="README.md"><img src="https://img.shields.io/badge/-English-1d76db?style=flat-square" alt="English"></a>
  &nbsp;
  <img src="https://img.shields.io/badge/-Japanese-888888?style=flat-square" alt="Japanese">
</div>

---

## What Is It?

![プロセスモニター](readme/screenshot_process_monitor.png)

このツールは、Claude・Gemini・Codex・Copilot などの AI CLI ツールを子プロセスとして起動・管理し、複数のエージェントが自律的に協調しながら複雑なタスクを遂行するマルチエージェントオーケストレーションシステムです。  
リーダーエージェントがサブタスクを並列に動作するワーカーエージェントへディスパッチするリーダー／ワーカー型の対話を自動化します。組み込みのブラウザベース Web UI から、プロジェクトの設定・エージェントのリアルタイム監視・会話ログの閲覧をすべてブラウザ上で完結できます。  
API クライアントではなく各 CLI の薄いラッパーとして動作するため、各 CLI が提供するスキル・プラグイン・MCP サーバー・ハーネスといった機能をエージェントがそのまま追加設定なしに活用できます。

---

## Features

- **マルチエージェント並列実行** — リーダーエージェントがキーワードベースのプロトコルで複数のワーカーエージェントへサブタスクを同時ディスパッチし、並列で作業を進めます。
- **セッション永続化** — 会話状態はプロジェクトごとに保存され、サーバー再起動をまたいでセッションの中断・再開が可能です。
- **ブラウザベース Web UI** — プロジェクト管理・エージェント設定・実行制御・会話ログ閲覧をすべてブラウザから操作できます。
- **リアルタイム監視** — 各エージェントの処理中、応答内容が Server-Sent Events (SSE) を通じて Web UI にリアルタイムで反映されます。
- **複数 AI バックエンド混在** — Claude・Gemini・Codex・Copilot を1つのプロジェクト内で組み合わせ、エージェントごとに最適なモデルを割り当てられます。
- **CLI 機能をそのまま活用** — AI Orchestrator は各 AI ツールを CLI サブプロセスとして起動するラッパーであるため、各 CLI が提供するスキル・プラグイン・MCP サーバー・ハーネスといった機能をエージェントがそのまま追加設定なしに利用できます。

### Web UI スクリーンショット

| エージェント設定 | プロジェクト設定 |
|---|---|
| ![エージェント設定](readme/screenshot_agent_setting.png) | ![プロジェクト設定](readme/screenshot_project_setting.png) |

| 会話ログ | アプリケーション設定 |
|---|---|
| ![会話ログ](readme/screenshot_log_view.png) | ![アプリケーション設定](readme/screenshot_config_setting.png) |

---

## Usage

> **前提:** `aiorch.jar` が事前にビルド済みであること。詳細は [Build](#build) を参照してください。

### サーバーの起動

```bash
java -jar aiorch.jar
```

### Web UI へのアクセス

ブラウザで以下の URL を開いてください。

```
http://localhost:8080/webui/
```

ポート番号は設定ファイルの `application.webui.port` で変更できます（変更後はサーバー再起動が必要）。

---

## Build

リポジトリをクローンし、Maven でビルドします。

```bash
git clone <repository-url>
cd org.ideaccum.aiorchestrator
mvn install
```

ビルド成功後、実行可能 JAR が以下のパスに生成されます。

```
builded/aiorch.jar
```

---

## Requirements

### Java ランタイム

- **Java 21** 以上

### AI CLI ツール

以下の AI CLI ツールのうち、少なくとも1つがインストールされている必要があります。使用しない CLI ツールの設定は不要です。

| CLI ツール | インストール参照先 |
|---|---|
| Claude Code CLI (`claude`) | [docs.anthropic.com](https://docs.anthropic.com/ja/docs/claude-code/overview) |
| Gemini CLI (`gemini`) | [github.com/google-gemini/gemini-cli](https://github.com/google-gemini/gemini-cli) |
| OpenAI Codex CLI (`codex`) | [github.com/openai/codex](https://github.com/openai/codex) |
| GitHub Copilot CLI (`copilot`) | [docs.github.com](https://docs.github.com/en/copilot/using-github-copilot/using-github-copilot-in-the-command-line) |
| Antigravity CLI (`agy`) | Antigravity CLI の公式ドキュメントを参照 |

> 使用する各 CLI ツールは **事前に認証済み** である必要があります。AI Orchestrator は CLI ツールを子プロセスとして起動するだけであり、認証処理は行いません。各ツールの公式ドキュメントに従ってサインイン・API キーの設定を完了させてください。

---

## Licensing

このツールは [MIT ライセンス](LICENSE.md) のもとで公開されています。

## Third-Party Licenses

| ライブラリ | バージョン | ライセンス |
|---|---|---|
| [Jackson Databind](https://github.com/FasterXML/jackson-databind) | 3.1.1 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Apache Velocity Engine](https://velocity.apache.org/) | 2.4.1 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| [Logback Classic](https://logback.qos.ch/) | 1.2.13 | [EPL v1.0](https://www.eclipse.org/legal/epl-v10.html) / [LGPL 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html) |
