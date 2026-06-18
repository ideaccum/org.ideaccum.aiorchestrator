/**
 * 環境設定ページコントローラースクリプトクラスです。<br>
 * <p>
 * アプリケーションの環境設定情報を設定する機能を提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/14	Kitagawa		新規作成
 *-->
 */
class ConfigSettingController {
	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 初期化処理を実行します。<br>
	 */
	async init() {
		try {
			/*
			 * 環境設定情報取得
			 */
			await this.#loadConfig();

			/*
			 * イベントハンドラ登録
			 */
			document.getElementById("btn-save").onclick = () => this.#onClickSave();
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 環境設定情報をサーバーから取得してフォームに反映します。<br>
	 */
	async #loadConfig() {
		try {
			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAwait(WebAPI.getConfig({}), {
				onDone: async (result) => {
					/*
					 * 環境設定情報フォーム反映
					 */
					const config = result.data?.config;
					if (!config) {
						return;
					}
					document.getElementById("field-theme").value = config.theme || "dark";
					document.getElementById("field-repository-path").value = config.repositoryPath || "";
					document.getElementById("field-webui-port").value = config.webuiPort || "";
					document.getElementById("field-webui-connection").value = config.webuiConnection || "";
					document.getElementById("field-agent-timeout").value = config.agentTimeout || "";
					document.getElementById("field-cli-claude").value = config.cliClaudeCommand || "";
					document.getElementById("field-cli-gemini").value = config.cliGeminiCommand || "";
					document.getElementById("field-cli-antigravity").value = config.cliAntigravityCommand || "";
					document.getElementById("field-cli-codex").value = config.cliCodexCommand || "";
					document.getElementById("field-cli-copilot").value = config.cliCopilotCommand || "";
				},
				onError: () => {
					/*
					 * エラー表示
					 */
					WebUI.showError("環境設定情報の取得に失敗しました。");
				},
			});
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 保存ボタンクリック時の処理を実行します。<br>
	 */
	async #onClickSave() {
		try {
			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAsync(
				WebAPI.saveConfig({
					theme: document.getElementById("field-theme").value, // テーマ
					repositoryPath: document.getElementById("field-repository-path").value.trim(), // リポジトリパス
					webuiPort: document.getElementById("field-webui-port").value.trim(), // WebUIポート番号
					webuiConnection: document.getElementById("field-webui-connection").value.trim(), // WebUI同時接続数
					agentTimeout: document.getElementById("field-agent-timeout").value.trim(), // エージェントタイムアウト(秒)
					cliClaudeCommand: document.getElementById("field-cli-claude").value.trim(), // Claude CLIコマンドパス
					cliGeminiCommand: document.getElementById("field-cli-gemini").value.trim(), // Gemini CLIコマンドパス
					cliAntigravityCommand: document.getElementById("field-cli-antigravity").value.trim(), // Antigravity CLIコマンドパス
					cliCodexCommand: document.getElementById("field-cli-codex").value.trim(), // Codex CLIコマンドパス
					cliCopilotCommand: document.getElementById("field-cli-copilot").value.trim(), // Copilot CLIコマンドパス
				}),
				{
					cancellable: false,
					onDone: async (result) => {
						/*
						 * メッセージ表示
						 */
						if (result?.message) {
							WebUI.showInfo(result.message);
						}

						/*
						 * テーマCSS更新(キャッシュクリア)
						 */
						const themeLink = document.querySelector('link[href*="theme.css"]');
						if (themeLink) {
							const base = themeLink.href.split("?")[0];
							themeLink.href = base + "?_t=" + Date.now();
						}
					},
					onError: (message) => {
						/*
						 * エラー表示
						 */
						WebUI.showError(message || "保存に失敗しました。");
					},
					onCancelled: () => {
						// キャンセル処理なし
					},
				},
			);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}
}
