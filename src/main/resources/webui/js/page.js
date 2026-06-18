/**
 * コンテンツコントローラースクリプトクラスです。<br>
 * <p>
 * SSE接続を管理し、受信したSSEイベントをカスタムイベント(sse-event)としてDocumentに配信します。<br>
 * サーバーサイドとの接続の切断・再接続の管理、ヘッダーステータス表示の更新も担当します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者		更新内容
 * 2026/05/08	Kitagawa		新規作成
 *-->
 */
class PageController {
	/** EventSourceインスタンス */
	#eventSource;

	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
			this.#eventSource = null;
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
			 * SSEエンドポイント接続
			 */
			await this.#connect();

			/*
			 * イベントハンドラ登録
			 */
			document.addEventListener("visibilitychange", async () => await this.#onVisibilityChange());
			document.addEventListener(Constants.SSE_EVENT_NAME, async (event) => await this.#handleEvent(event.detail));

			/*
			 * 初期表示
			 */
			await this.#display();
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * ページ表示状態変化時の処理を実行します。<br>
	 */
	async #onVisibilityChange() {
		try {
			if (document.hidden) {
				await this.#disconnect();
			} else if (!this.#eventSource) {
				await this.#connect();
			}
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSEイベントをハンドラへディスパッチします。<br>
	 * @param {{ type: string }} data - パース済みSSEイベント
	 */
	async #handleEvent(data) {
		try {
			if (data.type === Constants.SSE_TYPE_CONNECTED) {
				this.#onSseConnected(data);
			} else if (data.type === Constants.SSE_TYPE_DISCONNECTED) {
				this.#onSseDisconnected(data);
			} else if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_STARTED) {
				this.#onSseOrchestratorStarted(data);
			} else if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_DONE) {
				this.#onSseOrchestratorDone(data);
			} else if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_STOPPED) {
				this.#onSseOrchestratorStopped(data);
			}
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(connected)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	async #onSseConnected(data) {
		try {
			/*
			 * 切断から復旧した場合はリロード、それ以外はプロジェクト状態を再評価
			 */
			if (document.body.classList.contains("disconnected")) {
				window.location.reload();
				return;
			}
			await this.#display();
			this.#notice({ spinner: false, notice: "サーバーと接続されました。" });
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(disconnected)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	async #onSseDisconnected(data) {
		try {
			document.body.classList.add("disconnected");
			this.#notice({ spinner: false, notice: "サーバーと接続されていません。" });
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(orchestrator_started)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	async #onSseOrchestratorStarted(data) {
		try {
			this.#notice({ spinner: true, notice: "エージェント処理を実行中です..." });
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(orchestrator_done)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	async #onSseOrchestratorDone(data) {
		try {
			this.#notice({ spinner: false, notice: "エージェント処理を開始できる状態です。" });
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(orchestrator_stopped)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	async #onSseOrchestratorStopped(data) {
		try {
			this.#notice({ spinner: false, notice: "エージェント処理を開始できる状態です。" });
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSEエンドポイントに接続します。<br>
	 */
	async #connect() {
		try {
			/*
			 * SSE接続先エンドポイント接続
			 */
			if (this.#eventSource) {
				this.#eventSource.close();
				this.#eventSource = null;
			}
			this.#eventSource = new EventSource(Constants.SSE_CONNECT_URL);

			/*
			 * SSEイベントハンドラ登録
			 */
			this.#eventSource.onopen = async () => {
				try {
					document.dispatchEvent(
						new CustomEvent(Constants.SSE_EVENT_NAME, {
							detail: {
								type: Constants.SSE_TYPE_CONNECTED,
							},
						}),
					);
				} catch (e) {
					WebUI.catchFatal(e);
				}
			};

			/*
			 * SSEメッセージ受信ハンドラ
			 */
			this.#eventSource.onmessage = async (event) => {
				try {
					const data = JSON.parse(event.data);
					document.dispatchEvent(
						new CustomEvent(Constants.SSE_EVENT_NAME, {
							detail: data,
						}),
					);
				} catch (e) {
					WebUI.catchFatal(e);
				}
			};

			/*
			 * SSEエラー(切断)ハンドラ
			 */
			this.#eventSource.onerror = async () => {
				try {
					document.dispatchEvent(
						new CustomEvent(Constants.SSE_EVENT_NAME, {
							detail: {
								type: Constants.SSE_TYPE_DISCONNECTED,
							},
						}),
					);
					await this.#disconnect();
					setTimeout(() => this.#connect(), Constants.SSE_RECONNECT_INTERVAL);
				} catch (e) {
					WebUI.catchFatal(e);
				}
			};
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE接続を切断します。<br>
	 */
	async #disconnect() {
		try {
			if (this.#eventSource) {
				this.#eventSource.close();
				this.#eventSource = null;
			}
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * ノーティス表示内容を更新します。<br>
	 * @param {object} status - 更新内容
	 * @param {boolean} status.spinner - スピナーを表示するか
	 * @param {string} status.notice - ノーティスバーの表示テキスト
	 */
	#notice({ spinner, notice }) {
		try {
			document.getElementById("notice-spinner")?.classList.toggle("hidden", !spinner);
			const noticeTextEl = document.getElementById("notice-text");
			if (noticeTextEl) {
				noticeTextEl.textContent = notice;
			}
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * コンテンツ共通の表示/非表示制御を行います。<br>
	 */
	async #display() {
		await WebCtrl.doAwait(WebAPI.getCurrentProject({}, false), {
			onDone: async (result) => {
				/*
				 * プロジェクト存在状態制御
				 */
				const projectNameEl = document.getElementById("header-project-name");
				if (result.data?.project) {
					document.body.classList.add("has-project");
					if (projectNameEl) {
						projectNameEl.innerHTML = Constants.ICON_PROJECT + Utils.esc(result.data.project);
					}
				} else {
					document.body.classList.remove("has-project");
					if (projectNameEl) {
						projectNameEl.innerHTML = "";
					}
				}
			},
			onError: async () => {
				/*
				 * エラー時はプロジェクト画面遷移
				 */
				document.body.classList.remove("has-project");
				if (document.querySelector(".header-nav-active.header-nav-requires-project")) {
					window.location.href = "/webui/project_setting.html";
				}
			},
		});
	}
}
