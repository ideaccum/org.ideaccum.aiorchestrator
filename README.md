# AI Orchestrator

複数のAI CLIエージェントを協調動作させるマルチエージェントオーケストレーターです。  
リーダー/ワーカー型のエージェント間対話を自動化し、ブラウザベースのWeb UIから操作・モニタリングができます。

---

## 概要

AI Orchestratorは、以下のAI CLIツールを子プロセスとして起動・管理し、エージェント同士が自律的に対話しながらタスクを遂行するシステムです。

| エージェント種別 | CLIツール |
|---|---|
| `claude-cli` | [Claude Code CLI](https://docs.anthropic.com/ja/docs/claude-code/overview) |
| `gemini-cli` | [Gemini CLI](https://github.com/google-gemini/gemini-cli) |
| `codex-cli` | [OpenAI Codex CLI](https://github.com/openai/codex) |
| `copilot-cli` | [GitHub Copilot CLI](https://docs.github.com/en/copilot/using-github-copilot/using-github-copilot-in-the-command-line) |

### 動作イメージ

```
ユーザー (Web UI)
    │
    ▼ タスク指示
[Orchestrator (Java)]
    │
    ├─ dispatch ──▶ Leader Agent (Claude / Gemini / ...)
    │                   │
    │                   ├─ dispatch ──▶ Worker A
    │                   ├─ dispatch ──▶ Worker B  ← 並列実行
    │                   └─ dispatch ──▶ Worker C
    │
    └─ Web UI (SSE) ──▶ ブラウザでリアルタイム表示
```

---

## 主な機能

- **マルチエージェント並列実行** — ディスパッチキーワードで複数エージェントを同時実行
- **セッション永続化** — 前回の会話状態を保持して中断再開が可能
- **Web UI** — プロジェクト管理・エージェント設定・実行制御・会話ログ閲覧
- **テンプレート付きプロジェクト生成** — フルスタック/ライト構成の雛形を同梱
- **複数AIバックエンド対応** — Claude・Gemini・Codex・Copilot を混在利用可能

---

## 動作要件

- **Java 21**以上
- **Apache Maven 3.8**以上
- 使用するAI CLIツール（いずれか1つ以上）
  - Claude Code CLI (`claude`)
  - Gemini CLI (`gemini`)
  - OpenAI Codex CLI (`codex`)
  - GitHub Copilot CLI (`copilot`)

> 各CLIツールのインストール・認証は各ツールの公式ドキュメントを参照してください。

---

## ビルド

```bash
mvn install
```

ビルド成功後、プロジェクトルートに `aiorch.jar` が生成されます。

---

## 起動

```bash
java -jar aiorch.jar [設定ファイルパス]
```

設定ファイルパスを省略すると `config/config.properties` が使用されます。  
設定ファイルが存在しない場合は、デフォルト設定で自動生成されます。

起動後、ブラウザで以下のURLにアクセスしてください。

```
http://localhost:8080/webui/
```

---

## 設定

### アプリケーション設定 (`config/config.properties`)

| キー | デフォルト値 | 説明 |
|---|---|---|
| `application.theme` | `dark` | UIテーマ (`dark` / `light`) |
| `application.repository.path` | `./repos` | プロジェクトリポジトリ配置パス |
| `application.webui.port` | `8080` | Web UIサーバーポート番号 |
| `application.webui.connection` | `256` | Web UI最大同時接続数 |
| `agent.timeout` | `300` | エージェント処理タイムアウト（秒） |
| `agent.cli.claude-cli.command` | `%APPDATA%\npm\claude.cmd` | Claude Code CLI 起動コマンド |
| `agent.cli.gemini-cli.command` | `%APPDATA%\npm\gemini.cmd` | Gemini CLI 起動コマンド |
| `agent.cli.codex-cli.command` | `%APPDATA%\npm\codex.cmd` | Codex CLI 起動コマンド |
| `agent.cli.copilot-cli.command` | `%APPDATA%\npm\copilot.cmd` | GitHub Copilot CLI 起動コマンド |

### プロジェクト構成

プロジェクトは `repos/` 配下にディレクトリ単位で管理されます。  
各プロジェクトディレクトリには `.orchestrator/` が作成され、以下のファイルが配置されます。

```
repos/
└── <プロジェクト名>/
    └── .orchestrator/
        ├── project.properties   # プロジェクト設定
        ├── agents/              # エージェント定義ファイル
        ├── conversation.json    # 会話ログ
        ├── sessions.json        # セッション情報
        └── logs/                # 実行ログ
```

---

## エージェントテンプレート

新規プロジェクト作成時に選択できるエージェント構成テンプレートが同梱されています。

### フルスタック (`template-agents-fullstack`)

コードベース調査・分析・文書化などに特化した10エージェント構成です。

| エージェント | 役割 |
|---|---|
| Orchestrator | リーダー。タスク全体を統括・割り振り |
| Analyst | コード・要件の分析担当 |
| Explorer | ファイル・構造の探索担当 |
| Extractor | 情報抽出担当 |
| Mapper | 構造マッピング担当 |
| MapperDetail | 詳細マッピング担当 |
| Tracer | 依存・参照トレース担当 |
| TracerDetail | 詳細トレース担当 |
| Reviewer | レビュー担当 |
| Documenter | ドキュメント生成担当 |

### ライト構成 (`template-agents-light-*`)

Claude / Gemini / Codex それぞれの単一バックエンド向け3エージェント構成です。

| エージェント | 役割 |
|---|---|
| Orchestrator | リーダー兼ディスパッチャー |
| Worker | 実作業担当 |
| Reviewer | レビュー担当 |

---

## アーキテクチャ

```
org.ideaccum.ai.orchestrator
├── Bootstrap                  # エントリーポイント
├── Constants                  # アプリケーション定数
├── processor/
│   └── Orchestrator           # マルチエージェント制御ループ
├── agent/
│   ├── Agent                  # エージェント基底
│   ├── AgentRunner            # 並列実行管理
│   ├── AgentFactory           # エージェント生成
│   └── AgentType              # エージェント種別列挙
├── agents/
│   ├── AbstractCliAgent       # CLI実行基底クラス
│   ├── ClaudeCliAgent         # Claude Code CLI 実装
│   ├── GeminiCliAgent         # Gemini CLI 実装
│   ├── CodexCliAgent          # Codex CLI 実装
│   └── CopilotCliAgent        # GitHub Copilot CLI 実装
├── context/
│   ├── Config                 # アプリケーション設定
│   ├── Context                # 実行コンテキスト
│   ├── PromptFactory          # Velocityテンプレートによるプロンプト生成
│   └── Sessions               # セッション永続化
└── webui/
    ├── AgentWebUIServer       # 組み込みHTTP/SSEサーバー
    └── AgentWebUIEventController # SSEイベント管理
```

---

## 技術スタック

| 分類 | ライブラリ / バージョン |
|---|---|
| 言語 | Java 21 |
| ビルド | Apache Maven 3 |
| JSONパース | Jackson Databind 3.1.1 |
| テンプレートエンジン | Apache Velocity 2.4.1 |
| ロギング | Logback 1.2.13 |
| テスト | JUnit Jupiter 6.1.0-M1 |

---

## ライセンス

このプロジェクトのライセンスについては [LICENSE](LICENSE) ファイルを参照してください。
