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
	 * @param {boolean} [interactive=true] - エラーメッセージをUIに表示する場合にtrueを指定。UIロック制御はWebCtrl.doAwait/doAsyncが担う
	 */
	static async #post(url, payload, timeout, interactive = true) {
		/*
		 * リクエストタイムアウト設定
		 */
		const abortController = new AbortController();
		const abortTimer = setTimeout(() => abortController.abort(), timeout);

		try {
			/*
			 * リクエスト処理
			 */
			const response = await fetch(url, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(payload ?? {}),
				signal: abortController.signal,
			});

			/*
			 * レスポンスステータス判定
			 */
			if (!response.ok) {
				const result = await response.json().catch(() => null);
				if (interactive) {
					WebUI.showError(result?.error || "サーバーサイド処理でエラーが発生しました。");
				}
				return false;
			}

			/*
			 * レスポンス処理
			 */
			const result = await response.json();
			if (!result.success) {
				if (interactive) {
					WebUI.showError(result.error);
				}
				return null;
			}
			if (result.warning) {
				if (interactive) {
					WebUI.showWarning(result.warning);
				}
			}

			return result;
		} catch (e) {
			if (interactive) {
				WebUI.catchFatal(e);
			} else {
				console.error(e);
			}
			return null;
		} finally {
			clearTimeout(abortTimer);
		}
	}

	/**
	 * バイナリダウンロードPOSTリクエストを送信してブラウザダウンロードをトリガーします。<br>
	 * @param {string} url - リクエストURL
	 * @param {Object} payload - リクエストボディ
	 * @param {number} timeout - タイムアウト時間(ms)
	 * @param {boolean} [interactive=true] - エラーメッセージをUIに表示する場合にtrueを指定
	 * @returns {Promise<boolean>} ダウンロード成功の場合true
	 */
	static async #download(url, payload, timeout, interactive = true) {
		/*
		 * リクエストタイムアウト設定
		 */
		const abortController = new AbortController();
		const abortTimer = setTimeout(() => abortController.abort(), timeout);

		try {
			/*
			 * リクエスト処理
			 */
			const response = await fetch(url, {
				method: "POST",
				headers: { "Content-Type": "application/json" },
				body: JSON.stringify(payload ?? {}),
				signal: abortController.signal,
			});

			/*
			 * レスポンスステータス判定
			 */
			if (!response.ok) {
				const result = await response.json().catch(() => null);
				if (interactive) {
					WebUI.showError(result?.error || "ダウンロード処理でエラーが発生しました。");
				}
				return false;
			}

			/*
			 * ブラウザダウンロード処理
			 */
			const blob = await response.blob();
			const disposition = response.headers.get("Content-Disposition") || "";
			const match = disposition.match(/filename="?([^"]+)"?/);
			const fileName = match ? match[1] : "download";
			const objectUrl = URL.createObjectURL(blob);
			const linkEl = document.createElement("a");
			linkEl.href = objectUrl;
			linkEl.download = fileName;
			document.body.appendChild(linkEl);
			linkEl.click();
			setTimeout(() => {
				document.body.removeChild(linkEl);
				URL.revokeObjectURL(objectUrl);
			}, 500);

			return true;
		} catch (e) {
			if (interactive) {
				WebUI.catchFatal(e);
			} else {
				console.error(e);
			}
			return false;
		} finally {
			clearTimeout(abortTimer);
		}
	}

	/**
	 * 会話ログを取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getConversationLog(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_GET_CONVERSATION_LOG, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * プロジェクトのデフォルト値を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getDefaultProject(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_GET_DEFAULT_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * プロジェクト一覧を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getProjectList(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_GET_PROJECT_LIST, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * カレントプロジェクトを取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getCurrentProject(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_GET_CURRENT_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * オーケストレーターステータスを取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getOrchestratorStatus(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_GET_ORCHESTRATOR_STATUS, payload, Constants.POLLING_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * プロジェクト情報を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getProject(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_GET_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * プロジェクト情報を保存します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {string} payload.title - タイトル
	 * @param {string} payload.content - タスクプロンプト内容
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async saveProject(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_SAVE_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * プロジェクトを削除します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async deleteProject(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_DELETE_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * セッション情報をクリアします。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async clearSession(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_CLEAR_SESSION, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * ログデータを ZIP 形式でダウンロードします。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {boolean} [interactive=true] - エラーメッセージを表示する場合にtrueを指定
	 * @returns {Promise<boolean>} ダウンロード成功の場合 true
	 */
	static async downloadLogs(payload, interactive) {
		const result = await WebAPI.#download(Constants.API_DOWNLOAD_LOGS, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * バックグラウンドタスクのキャンセルを要求します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} [interactive=true] - エラーメッセージを表示する場合にtrueを指定
	 */
	static async cancelProcess(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_CANCEL_PROCESS, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * バックグラウンドタスクのステータスを取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} [interactive=false] - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getProcessStatus(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_GET_PROCESS_STATUS, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * 制御キーワード情報を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getControlKeywords(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_GET_CONTROL_KEYWORDS, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * エージェント一覧を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getAgentList(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_GET_AGENT_LIST, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
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
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async saveAgent(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_SAVE_AGENT, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * エージェントを削除します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.project - プロジェクト名
	 * @param {string} payload.name - エージェント名
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async deleteAgent(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_DELETE_AGENT, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * プロジェクトを選択します。<br>
	 * @param {Object} payload - リクエストパラメータ
	 * @param {string} payload.projectName - プロジェクト名
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async selectProject(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_SELECT_PROJECT, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * エージェント処理を開始します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async startOrchestrator(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_START_ORCHESTRATOR, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * エージェント処理を停止します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async stopOrchestrator(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_STOP_ORCHESTRATOR, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}

	/**
	 * 環境設定情報を取得します。<br>
	 * @param {Object} payload - リクエストパラメータ(キーなし)
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async getConfig(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_GET_CONFIG, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
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
	 * @param {string} payload.cliAntigravityCommand - Antigravity CLIコマンド
	 * @param {string} payload.cliCodexCommand - Codex CLIコマンド
	 * @param {string} payload.cliCopilotCommand - GitHub Copilot CLIコマンド
	 * @param {boolean} interactive - エラーメッセージを表示する場合にtrueを指定
	 */
	static async saveConfig(payload, interactive) {
		const result = await WebAPI.#post(Constants.API_SAVE_CONFIG, payload, Constants.WEBAPI_ABORT_TIMEOUT, interactive);
		return result;
	}
}
