/**
 * コンテンツコントローラースクリプトクラスです。<br>
 * <p>
 * SSE接続を管理し、受信したSSEイベントをカスタムイベント(sse-event)としてDocumentに配信します。<br>
 * サーバーサイドとの接続の切断・再接続もこのクラスが一元管理します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者		更新内容
 * 2026/05/08	Kitagawa		新規作成
 *-->
 */
class ContentController {
	/** EventSourceインスタンス */
	#eventSource;

	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
			this.#eventSource = null;
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
			 * SSEエンドポイント接続
			 */
			this.#connect();

			/*
			 * ページ遷移時EventSource切断・再開処理
			 */
			document.addEventListener("visibilitychange", () => {
				if (document.hidden) {
					this.#disconnect();
				} else if (!this.#eventSource) {
					this.#connect();
				}
			});

			/*
			 * コンテンツ共通の表示／非表示制御
			 */
			document.addEventListener(Constants.SSE_EVENT_NAME, async (event) => {
				const type = event.detail?.type;
				if (type === Constants.SSE_TYPE_CONNECTED) {
					// SSE接続時にプロジェクト状態を再評価
					await this.#controlDisplay();
				}
			});
			await this.#controlDisplay();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * コンテンツ共通の表示／非表示制御を行います。<br>
	 */
	async #controlDisplay() {
		const hasCurrentProject = await WebAPI.hasCurrentProject();
		document.body.classList.toggle("has-project", hasCurrentProject);
	}

	/**
	 * SSEエンドポイントに接続します。<br>
	 */
	#connect() {
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
			this.#eventSource.onopen = () => {
				try {
					document.dispatchEvent(
						new CustomEvent(Constants.SSE_EVENT_NAME, {
							detail: {
								type: Constants.SSE_TYPE_CONNECTED,
							},
						}),
					);
				} catch (e) {
					Utils.catchFatal(e);
				}
			};

			/*
			 * SSEメッセージ受信ハンドラ
			 */
			this.#eventSource.onmessage = (event) => {
				try {
					const data = JSON.parse(event.data);
					document.dispatchEvent(
						new CustomEvent(Constants.SSE_EVENT_NAME, {
							detail: data,
						}),
					);
				} catch (e) {
					Utils.catchFatal(e);
				}
			};

			/*
			 * SSEエラー(切断)ハンドラ
			 */
			this.#eventSource.onerror = () => {
				try {
					document.dispatchEvent(
						new CustomEvent(Constants.SSE_EVENT_NAME, {
							detail: {
								type: Constants.SSE_TYPE_DISCONNECTED,
							},
						}),
					);
					this.#disconnect();
					setTimeout(() => this.#connect(), Constants.SSE_RECONNECT_INTERVAL);
				} catch (e) {
					Utils.catchFatal(e);
				}
			};
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * SSE接続を切断します。<br>
	 */
	#disconnect() {
		try {
			if (this.#eventSource) {
				this.#eventSource.close();
				this.#eventSource = null;
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}
}
