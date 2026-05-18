/**
 * 共通ヘッダーコントローラースクリプトクラスです。<br>
 * <p>
 * documentのsse-eventを購読してヘッダーパネルの要素状態を管理します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/01	Kitagawa		新規作成
 *-->
 */
class HeaderController {
	/** アクティブページ */
	#activePageName;

	/** ステータス更新タイマー */
	#timerRefreshStatus;

	/** 切断状態フラグ */
	#disconnected;

	/** DOM参照(プロジェクト名) */
	#elProjectName;

	/** DOM参照(ノーティスバースピナー) */
	#elNoticeSpinner;

	/** DOM参照(ノーティスバーテキスト) */
	#elNoticeText;

	/** DOM参照(開始ボタン) */
	#elStartButton;

	/** DOM参照(停止ボタン) */
	#elStopButton;

	/**
	 * コンストラクタ<br>
	 * @param {string} activePageName - アクティブページ名
	 */
	constructor(activePageName) {
		try {
			this.#activePageName = activePageName;
			this.#timerRefreshStatus = null;
			this.#disconnected = false;
			this.#elProjectName = document.getElementById("header-project-name");
			this.#elNoticeSpinner = document.getElementById("notice-spinner");
			this.#elNoticeText = document.getElementById("notice-text");
			this.#elStartButton = document.getElementById("start-btn");
			this.#elStopButton = document.getElementById("stop-btn");
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
			 * SSEイベントハンドラ設定
			 */
			document.addEventListener(Constants.SSE_EVENT_NAME, (event) => this.#handleEvent(event));

			/*
			 * ページ復帰時にステータスを即時更新
			 */
			document.addEventListener("visibilitychange", () => {
				if (!document.hidden) {
					this.refresh(true);
				}
			});

			/*
			 * ヘッダ情報即時更新
			 */
			await this.refresh(true);
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * サーバー状態APIを取得してヘッダー表示を更新します。<br>
	 * @param {boolean} [force=false] - 定期実行ではなく即時強制実行する場合にtrueを指定
	 */
	async refresh(force = false) {
		try {
			/*
			 * 即時実行の場合はタイマー解除して実行
			 */
			if (force) {
				clearTimeout(this.#timerRefreshStatus);
				this.#timerRefreshStatus = null;
			}

			/*
			 * 実行中状態の場合はスケジュールのみで処理終了
			 */
			if (!force && document.hidden) {
				this.#scheduleRefresh();
				return;
			}

			/*
			 * サーバーサイド処理
			 */
			const result = await WebAPI.getOrchestratorStatus({
				_: null,
			});
			const data = result.data;

			/*
			 * 切断状態解除
			 */
			document.body.classList.remove("disconnected");

			/*
			 * プロジェクト名更新
			 */
			if (this.#elProjectName) {
				this.#elProjectName.textContent = data.projectName ? data.projectName : "";
			}

			/*
			 * ステータスピル／ノーティスバー／ボタン更新
			 */
			if (!data.projectSelected) {
				// プロジェクト未選択
				this.#updateStatusConnected(false);
			} else if (data.done) {
				// 完了済み
				this.#updateStatusDone();
			} else if (data.running) {
				// 実行中
				this.#updateStatusRunning();
			} else if (data.waitingStart) {
				// 開始待機中(リーダー数チェック)
				if (data.leaderCount === 0) {
					this.#updateStatusLeaderError("リーダーエージェントが設定されていません");
				} else if (data.leaderCount > 1) {
					this.#updateStatusLeaderError("リーダーエージェントが複数設定されています");
				} else {
					this.#updateStatusConnected(true);
				}
			} else {
				// 接続中済み
				this.#updateStatusConnected(false);
			}

			/*
			 * 切断状態フラグをクリア
			 */
			if (this.#disconnected) {
				this.#disconnected = false;
				window.location.reload();
			}
		} catch (e) {
			// サーバーシャットダウン後の更新でエラーが続くためエラー通知は行わない
			//Utils.catchFatal(e);
			this.#disconnected = true;
		} finally {
			this.#scheduleRefresh();
		}
	}

	/**
	 * サーバー状態APIを取得更新処理をスケジュールします。<br>
	 */
	#scheduleRefresh() {
		try {
			if (this.#disconnected) {
				return;
			}
			if (!this.#timerRefreshStatus) {
				this.#timerRefreshStatus = setTimeout(() => {
					this.#timerRefreshStatus = null;
					this.refresh();
				}, Constants.POLLING_INTERVAL);
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * sse-eventカスタムイベントのハンドル処理を行います。<br>
	 * @param {CustomEvent} event - sse-eventカスタムイベント
	 */
	#handleEvent(event) {
		try {
			const data = event.detail;
			if (data.type === Constants.SSE_TYPE_DISCONNECTED) {
				/*
				 * 切断イベントの場合は即時切断状態表示に更新
				 */
				this.#updateStatusDisconnected();
			} else if (data.type === Constants.SSE_TYPE_CONNECTED && this.#disconnected) {
				/*
				 * 切断状態からSSE再接続した場合はページリロードで完全復旧
				 */
				window.location.reload();
			} else {
				/*
				 * その他のイベントの場合はステータスを即時更新
				 */
				this.refresh(true);
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 開始ボタンを表示してクリックハンドラを設定します。<br>
	 */
	#showStartButton() {
		try {
			if (!this.#elStartButton) {
				return;
			}
			this.#elStartButton.style.display = "";
			this.#elStartButton.disabled = false;
			this.#elStartButton.onclick = async () => {
				this.#disableStartButton();
				this.#updateStatusStarting();
				try {
					/*
					 * サーバーサイド処理
					 */
					await WebAPI.startOrchestrator({
						_: null,
					});
				} catch (e) {
					Utils.catchFatal(e);
					this.#hideStartButton();
					this.#hideStopButton();
				} finally {
					this.refresh(true);
				}
			};
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 開始ボタンを非表示にします。<br>
	 */
	#hideStartButton() {
		try {
			if (!this.#elStartButton) {
				return;
			}
			this.#elStartButton.style.display = "none";
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 開始ボタンを非活性にします。<br>
	 */
	#disableStartButton() {
		try {
			if (!this.#elStartButton) {
				return;
			}
			this.#elStartButton.disabled = true;
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 停止ボタンを表示してクリックハンドラを設定します。<br>
	 */
	#showStopButton() {
		try {
			if (!this.#elStopButton) {
				return;
			}
			this.#elStopButton.style.display = "";
			this.#elStopButton.disabled = false;
			this.#elStopButton.onclick = async () => {
				this.#disableStopButton();
				this.#updateStatusStopping();
				try {
					/*
					 * サーバーサイド処理
					 */
					await WebAPI.stopOrchestrator({
						_: null,
					});
				} catch (e) {
					Utils.catchFatal(e);
				} finally {
					this.refresh(true);
				}
			};
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 停止ボタンを非表示にします。<br>
	 */
	#hideStopButton() {
		try {
			if (!this.#elStopButton) {
				return;
			}
			this.#elStopButton.style.display = "none";
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 停止ボタンを非活性にします。<br>
	 */
	#disableStopButton() {
		try {
			if (!this.#elStopButton) {
				return;
			}
			this.#elStopButton.disabled = true;
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * ノーティスバーの表示内容を更新します。<br>
	 * @param {object} status - 更新内容
	 * @param {boolean} status.spinner - スピナーを表示するか
	 * @param {string} status.notice - ノーティスバーの表示テキスト
	 */
	#updateStatus({ spinner, notice }) {
		try {
			if (this.#elNoticeSpinner) {
				this.#elNoticeSpinner.style.display = spinner ? "" : "none";
			}
			if (this.#elNoticeText) {
				this.#elNoticeText.textContent = notice;
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 切断状態のステータス表示を設定します。<br>
	 */
	#updateStatusDisconnected() {
		try {
			this.#disconnected = true;
			clearTimeout(this.#timerRefreshStatus);
			this.#timerRefreshStatus = null;
			document.body.classList.add("disconnected");
			this.#updateStatus({
				spinner: false,
				notice: "サーバーと接続されていません",
			});
			this.#hideStartButton();
			this.#hideStopButton();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * リーダーエージェント設定エラー状態のステータス表示を設定します。<br>
	 * @param {string} message - ノーティスバーに表示するエラーメッセージ
	 */
	#updateStatusLeaderError(message) {
		try {
			this.#updateStatus({
				spinner: false,
				notice: message,
			});
			this.#hideStartButton();
			this.#hideStopButton();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 接続済み状態のステータス表示を設定します。<br>
	 */
	#updateStatusConnected(waitingStart = false) {
		try {
			if (waitingStart) {
				this.#updateStatus({
					spinner: false,
					notice: "開始ボタンを押してエージェント処理を開始してください",
				});
				this.#showStartButton();
				this.#hideStopButton();
			} else {
				this.#updateStatus({
					spinner: false,
					notice: "エージェント処理を開始するには対象のプロジェクトを選択してください",
				});
				this.#hideStartButton();
				this.#hideStopButton();
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 開始中状態のステータス表示を設定します。<br>
	 */
	#updateStatusStarting() {
		try {
			this.#updateStatus({
				spinner: true,
				notice: "エージェント処理を開始しています...",
			});
			this.#disableStartButton();
			this.#hideStopButton();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 実行中状態のステータス表示を設定します。<br>
	 */
	#updateStatusRunning() {
		try {
			this.#updateStatus({
				spinner: true,
				notice: "エージェント処理を実行中です...",
			});
			this.#hideStartButton();
			this.#showStopButton();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 完了済み状態のステータス表示を設定します。<br>
	 */
	#updateStatusDone() {
		try {
			this.#updateStatus({
				spinner: false,
				notice: "エージェント処理は完了しています",
			});
			this.#showStartButton();
			this.#hideStopButton();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 停止中状態のステータス表示を設定します。<br>
	 */
	#updateStatusStopping() {
		try {
			this.#updateStatus({
				spinner: true,
				notice: "エージェント処理を停止中です...",
			});
			this.#hideStartButton();
			this.#disableStopButton();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}
}
