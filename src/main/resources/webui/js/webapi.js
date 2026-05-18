/**
 * WebAPIクライアントクラスです。<br>
 * <p>
 * サーバーAPIの呼び出しを一元管理します。<br>
 * 失敗時はform-error要素にエラーメッセージを表示してnullを返します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/10	Kitagawa		新規作成
 *-->
 */
class WebAPI {
	/**
	 * POSTリクエストを送信してレスポンスを返します。<br>
	 * @param {string} url - リクエストURL
	 * @param {Object} payload - リクエストボディ
	 * @param {number} timeout - タイムアウト時間(ms)
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async #post(url, payload, timeout, showError) {
		const abortCtrl = new AbortController();
		const abortTimer = setTimeout(() => abortCtrl.abort(), timeout);
		if (showError) {
			Utils.clearError();
			Utils.clearWarning();
			Utils.clearInfo();
		}
		try {
			const response = await fetch(url, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(payload ?? {}),
				signal: abortCtrl.signal,
			});
			const result = await response.json();
			if (!result.success) {
				if (showError) {
					Utils.showError(result.error);
				}
				return null;
			}
			if (result.warning) {
				Utils.showWarning(result.warning);
			}
			return result;
		} catch (e) {
			if (showError) {
				Utils.catchFatal(e);
			}
			return null;
		} finally {
			clearTimeout(abortTimer);
		}
	}

	/**
	 * 会話ログを取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getConversationLog(payload, showError = true) {
		return await WebAPI.#post(Constants.API_GET_CONVERSATION_LOG, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * プロジェクトのデフォルト値を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getDefaultProject(payload, showError = true) {
		return await WebAPI.#post(Constants.API_GET_DEFAULT_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * プロジェクト一覧を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getProjectList(payload, showError = true) {
		return await WebAPI.#post(Constants.API_GET_PROJECT_LIST, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * カレントプロジェクトを取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getCurrentProject(payload, showError = true) {
		return await WebAPI.#post(Constants.API_GET_CURRENT_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * カレントプロジェクトが選択されているかを確認します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 */
	static async hasCurrentProject(payload) {
		const result = await WebAPI.getCurrentProject(payload, false);
		return result !== null;
	}

	/**
	 * オーケストレーターステータスを取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 */
	static async getOrchestratorStatus(payload) {
		return await WebAPI.#post(Constants.API_GET_ORCHESTRATOR_STATUS, payload, Constants.POLLING_ABORT_TIMEOUT, false);
	}

	/**
	 * プロジェクト情報を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getProject(payload, showError = true) {
		return await WebAPI.#post(Constants.API_GET_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * プロジェクト情報を保存します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {string} payload.title - タイトル
	 * @param {string} payload.content - タスクプロンプト内容
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async saveProject(payload, showError = true) {
		return await WebAPI.#post(Constants.API_SAVE_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * プロジェクトを削除します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async deleteProject(payload, showError = true) {
		return await WebAPI.#post(Constants.API_DELETE_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * エージェント一覧を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getAgentList(payload, showError = true) {
		return await WebAPI.#post(Constants.API_GET_AGENT_LIST, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * エージェントを保存します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {string} payload.name - エージェント名
	 * @param {string} payload.type - エージェントタイプ
	 * @param {string} payload.model - モデル名
	 * @param {string} payload.extraArgs - 追加引数
	 * @param {boolean} payload.leader - リーダーエージェントフラグ
	 * @param {string} payload.role - 役割名
	 * @param {string} payload.personality - 性質
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async saveAgent(payload, showError = true) {
		return await WebAPI.#post(Constants.API_SAVE_AGENT, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * エージェントを削除します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {string} payload.name - エージェント名
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async deleteAgent(payload, showError = true) {
		return await WebAPI.#post(Constants.API_DELETE_AGENT, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * プロジェクトを選択します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.projectName - プロジェクト名
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async selectProject(payload, showError = true) {
		return await WebAPI.#post(Constants.API_SELECT_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * エージェント処理を開始します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async startOrchestrator(payload, showError = true) {
		return await WebAPI.#post(Constants.API_START_ORCHESTRATOR, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * エージェント処理を停止します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async stopOrchestrator(payload, showError = true) {
		return await WebAPI.#post(Constants.API_STOP_ORCHESTRATOR, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * 環境設定情報を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getConfig(payload, showError = true) {
		return await WebAPI.#post(Constants.API_GET_CONFIG, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}

	/**
	 * 環境設定情報を保存します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.theme - テーマ
	 * @param {string} payload.repositoryPath - リポジトリパス
	 * @param {string} payload.webuiPort - WebUIポート
	 * @param {string} payload.webuiConnection - WebUI同時接続数
	 * @param {string} payload.agentTimeout - エージェントタイムアウト(秒)
	 * @param {string} payload.cliClaudeCommand - Claude Code CLIコマンド
	 * @param {string} payload.cliGeminiCommand - Gemini CLIコマンド
	 * @param {string} payload.cliCodexCommand - Codex CLIコマンド
	 * @param {string} payload.cliCopilotCommand - GitHub Copilot CLIコマンド
	 * @param {boolean} showError - エラーメッセージを表示する場合にtrueを指定
	 */
	static async saveConfig(payload, showError = true) {
		return await WebAPI.#post(Constants.API_SAVE_CONFIG, payload, Constants.WEBAPI_ABORT_TIMEOUT, showError);
	}
}
