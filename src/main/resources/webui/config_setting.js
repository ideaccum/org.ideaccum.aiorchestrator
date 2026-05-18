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
			Utils.catchFatal(e);
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
			Utils.catchFatal(e);
		}
	}

	/**
	 * 環境設定情報をサーバーから取得してフォームに反映します。<br>
	 */
	async #loadConfig() {
		try {
			const result = await WebAPI.getConfig({ _: null }, false);
			if (!result) {
				return;
			}
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
			document.getElementById("field-cli-codex").value = config.cliCodexCommand || "";
			document.getElementById("field-cli-copilot").value = config.cliCopilotCommand || "";
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 保存ボタンクリック時の処理を実行します。<br>
	 */
	async #onClickSave() {
		try {
			Utils.clearError();
			Utils.clearInfo();

			const result = await WebAPI.saveConfig({
				theme: document.getElementById("field-theme").value,
				repositoryPath: document.getElementById("field-repository-path").value.trim(),
				webuiPort: document.getElementById("field-webui-port").value.trim(),
				webuiConnection: document.getElementById("field-webui-connection").value.trim(),
				agentTimeout: document.getElementById("field-agent-timeout").value.trim(),
				cliClaudeCommand: document.getElementById("field-cli-claude").value.trim(),
				cliGeminiCommand: document.getElementById("field-cli-gemini").value.trim(),
				cliCodexCommand: document.getElementById("field-cli-codex").value.trim(),
				cliCopilotCommand: document.getElementById("field-cli-copilot").value.trim(),
			});
			if (!result) {
				return;
			}

			if (result.message) {
				Utils.showInfo(result.message);
			}

			/*
			 * theme.cssを再読み込みしてテーマ変更を即時反映
			 */
			const themeLink = document.querySelector('link[href*="theme.css"]');
			if (themeLink) {
				const base = themeLink.href.split("?")[0];
				themeLink.href = base + "?t=" + Date.now();
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}
}
