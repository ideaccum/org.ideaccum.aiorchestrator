/**
 * ログビューページコントローラースクリプトクラスです。<br>
 * <p>
 * アクティブプロジェクトの会話ログを一覧表示する機能を提供します。<br>
 * オーケストレーター実行中はSSEイベントを受信してリアルタイムに会話ターンを追記します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/15	Kitagawa		新規作成
 * 2026/06/10	Kitagawa		SSEリアルタイム更新・ストリーミング表示対応
 *-->
 */
class LogViewController {
	/** ストリーミングエージェントマップ(キー:エージェント名) */
	#liveAgents;

	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
			this.#liveAgents = {};
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
			 * SSEイベントハンドラ登録(非同期処理前に登録してイベントを取りこぼさない)
			 */
			document.addEventListener(Constants.SSE_EVENT_NAME, (event) => this.#handleEvent(event.detail));

			/*
			 * 会話ログ初期表示
			 */
			await this.#loadLog();
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 会話ログをサーバーから取得して画面に表示します。<br>
	 * ストリーミング中のライブターンは末尾に再アタッチします。<br>
	 */
	async #loadLog() {
		try {
			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAwait(WebAPI.getConversationLog({}), {
				onDone: async (result) => {
					/*
					 * 表示エリア初期化
					 */
					const entiriesEl = document.getElementById("log-entries");
					const emptyEntryEl = document.getElementById("log-empty");
					entiriesEl.innerHTML = "";

					/*
					 * 会話ログ取得
					 */
					const conversations = result.data?.conversations || [];
					if (conversations.length === 0 && Object.keys(this.#liveAgents).length === 0) {
						emptyEntryEl.classList.remove("hidden");
						entiriesEl.classList.add("hidden");
						return;
					}
					emptyEntryEl.classList.add("hidden");
					entiriesEl.classList.remove("hidden");

					/*
					 * 静的ログターン描画
					 */
					conversations.forEach((conversation, index) => {
						/*
						 * ライブ状態エージェント同一ターン静的描画をスキップ
						 */
						const liveAgent = this.#liveAgents[conversation.agentName];
						if (liveAgent && liveAgent.timestamp === conversation.timestamp) {
							return;
						}

						/*
						 * ターン描画情報取得
						 */
						const prevConversation = index > 0 ? conversations[index - 1] : null;
						const elapsed = Utils.calcElapsed(prevConversation?.timestamp, conversation.timestamp);
						const isOwner = conversation.agentName === Constants.OWNER_AGENT_NAME;
						const tokenText = Utils.formatTokenText(conversation.agentName, conversation.tokenUsage?.inputTokens, conversation.tokenUsage?.outputTokens);
						const agentInitial = (conversation.agentName || "?").charAt(0).toUpperCase();
						const agentColor = Utils.avatarColor(conversation.agentName || "");

						/*
						 * ターンパネル要素
						 */
						const turnEl = document.createElement("div");
						turnEl.className = "log-turn" + (isOwner ? " is-owner" : "");

						/*
						 * アバター要素
						 */
						const avatarEl = document.createElement("div");
						avatarEl.className = "log-turn-avatar";
						avatarEl.style.background = agentColor;
						avatarEl.textContent = agentInitial;

						/*
						 * ターンタイトル要素
						 */
						const titleEl = document.createElement("div");
						titleEl.className = "log-turn-title";
						titleEl.innerHTML = '<span class="log-agent-name">' + Utils.esc(conversation.agentName || "") + "</span>" + '<span class="log-turn-timestamp">' + Utils.esc(conversation.timestamp || "") + "</span>";

						/*
						 * ターン本文要素
						 */
						const bodyEl = document.createElement("div");
						bodyEl.className = "log-turn-body md-content";
						bodyEl.innerHTML = marked.parse(Utils.filterKeywordLines(conversation.content || ""));

						/*
						 * トークン情報要素
						 */
						const tokensEl = document.createElement("div");
						tokensEl.className = "log-turn-tokens";
						tokensEl.textContent = tokenText + (elapsed != null ? "　" + elapsed : "");

						/*
						 * DOM構築
						 */
						turnEl.appendChild(avatarEl);
						turnEl.appendChild(titleEl);
						turnEl.appendChild(bodyEl);
						turnEl.appendChild(tokensEl);
						entiriesEl.appendChild(turnEl);
					});

					/*
					 * ライブターンを末尾に再アタッチ
					 */
					Object.values(this.#liveAgents).forEach((liveAgent) => {
						entiriesEl.appendChild(liveAgent.turnEl);
					});

					/*
					 * 自動スクロール
					 */
					WebUI.scrollBottom(document.querySelector(".main"));
				},
				onError: () => {
					/*
					 * エラー表示
					 */
					WebUI.showError("会話ログの取得に失敗しました。");
				},
			});
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSEイベントをハンドラへディスパッチします。<br>
	 * @param {{ type: string }} data - パース済みSSEイベント
	 */
	#handleEvent(data) {
		try {
			/*
			 * ログコンテンツ更新
			 */
			if (data.type === Constants.SSE_TYPE_AGENT_INITIALIZED) {
				this.#onSseAgentInitialized(data);
			} else if (data.type === Constants.SSE_TYPE_AGENT_START) {
				this.#onSseAgentStart(data);
			} else if (data.type === Constants.SSE_TYPE_AGENT_CONTENT) {
				this.#onSseAgentContent(data);
			} else if (data.type === Constants.SSE_TYPE_AGENT_FINISH) {
				this.#onSseAgentFinish(data);
			} else if (data.type === Constants.SSE_TYPE_AGENT_ERROR) {
				this.#onSseAgentError(data);
			} else if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_DONE || data.type === Constants.SSE_TYPE_ORCHESTRATOR_STOPPED) {
				this.#onSseOrchestratorDone(data);
			} else if (data.type === Constants.SSE_TYPE_DISCONNECTED) {
				this.#onSseDisconnected(data);
			}
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_initialized)イベント処理を実行します。<br>
	 */
	async #onSseAgentInitialized(data) {
		try {
			/*
			 * ライブターン削除
			 */
			Object.values(this.#liveAgents).forEach((liveAgent) => {
				liveAgent.turnEl.remove();
			});
			this.#liveAgents = {};

			/*
			 * ログ再読み込み
			 */
			await this.#loadLog();
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_start)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	#onSseAgentStart(data) {
		try {
			/*
			 * イベントデータ展開
			 */
			const {
				agentName, //
				timestamp, //
			} = data;

			/*
			 * 描画情報取得
			 */
			const isOwner = agentName === Constants.OWNER_AGENT_NAME;
			const agentInitial = (agentName || "?").charAt(0).toUpperCase();
			const agentColor = Utils.avatarColor(agentName || "");

			/*
			 * ターン要素構築
			 */
			const turnEl = document.createElement("div");
			turnEl.className = "log-turn is-streaming" + (isOwner ? " is-owner" : "");

			/*
			 * アバター要素
			 */
			const avatarEl = document.createElement("div");
			avatarEl.className = "log-turn-avatar";
			avatarEl.style.background = agentColor;
			avatarEl.textContent = agentInitial;

			/*
			 * ターンタイトル要素
			 */
			const titleEl = document.createElement("div");
			titleEl.className = "log-turn-title";
			titleEl.innerHTML = '<span class="log-agent-name">' + Utils.esc(agentName || "") + "</span>" + '<span class="log-turn-timestamp">' + Utils.esc(timestamp || "") + "</span>";

			/*
			 * ターン本文要素
			 */
			const bodyEl = document.createElement("div");
			bodyEl.className = "log-turn-body md-content streaming";

			/*
			 * トークン要素
			 */
			const tokensEl = document.createElement("div");
			tokensEl.className = "log-turn-tokens";
			tokensEl.textContent = "";

			/*
			 * DOM構築
			 */
			turnEl.appendChild(avatarEl);
			turnEl.appendChild(titleEl);
			turnEl.appendChild(bodyEl);
			turnEl.appendChild(tokensEl);

			/*
			 * DOM追加
			 */
			const entriesEl = document.getElementById("log-entries");
			document.getElementById("log-empty").classList.add("hidden");
			entriesEl.classList.remove("hidden");
			entriesEl.appendChild(turnEl);

			/*
			 * ライブターン情報保持
			 */
			this.#liveAgents[agentName] = {
				turnEl: turnEl, //
				bodyEl: bodyEl, //
				tokensEl: tokensEl, //
				currentRaw: "", //
				timestamp, //
			};

			/*
			 * 自動スクロール
			 */
			WebUI.scrollBottom(document.querySelector(".main"));
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_content)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	#onSseAgentContent(data) {
		try {
			/*
			 * イベントデータ展開
			 */
			const {
				agentName, //
				content, //
			} = data;

			/*
			 * ライブターン取得
			 */
			const liveAgent = this.#liveAgents[agentName];
			if (!liveAgent) {
				return;
			}

			/*
			 * ターン本文追記
			 */
			liveAgent.currentRaw += content;
			liveAgent.bodyEl.innerHTML = marked.parse(Utils.filterKeywordLines(liveAgent.currentRaw));

			/*
			 * 自動スクロール
			 */
			WebUI.scrollBottom(document.querySelector(".main"));
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_finish)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	#onSseAgentFinish(data) {
		try {
			/*
			 * イベントデータ展開
			 */
			const {
				agentName, //
				inputTokens, //
				outputTokens, //
				elapsedTime, //
			} = data;

			/*
			 * ライブターン取得
			 */
			const liveAgent = this.#liveAgents[agentName];
			if (!liveAgent) {
				return;
			}

			/*
			 * ストリーミング解除
			 */
			liveAgent.turnEl.classList.remove("is-streaming");
			liveAgent.bodyEl.classList.remove("streaming");

			/*
			 * ターン本文確定
			 */
			const filtered = Utils.filterKeywordLines(liveAgent.currentRaw);
			liveAgent.bodyEl.innerHTML = filtered.trim() === "" ? '<em style="color:var(--color-text-muted)">(レスポンスなし)</em>' : marked.parse(filtered);

			/*
			 * トークン/時間情報
			 */
			const elapsedText = elapsedTime ? Utils.formatDurationMs(elapsedTime) : null;
			liveAgent.tokensEl.textContent = Utils.formatTokenText(agentName, inputTokens, outputTokens) + (elapsedText ? "　" + elapsedText : "");

			/*
			 * ライブエージェント削除(確定済みターンはloadLog再描画時に静的ターンとして表示される)
			 */
			delete this.#liveAgents[agentName];
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_error)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	#onSseAgentError(data) {
		try {
			/*
			 * イベントデータ展開
			 */
			const {
				agentName, //
				message, //
			} = data;

			/*
			 * ライブターン取得
			 */
			const liveAgent = this.#liveAgents[agentName];
			if (!liveAgent) {
				return;
			}

			/*
			 * ストリーミング解除
			 */
			liveAgent.turnEl.classList.remove("is-streaming");
			liveAgent.bodyEl.classList.remove("streaming");

			/*
			 * エラーパネル表示
			 */
			if (message) {
				liveAgent.bodyEl.innerHTML = '<em style="color:var(--color-error)">⚠️ ' + Utils.esc(message) + "</em>";
			}
			liveAgent.tokensEl.textContent = "エラー";

			/*
			 * ライブエージェント削除(確定済みターンはloadLog再描画時に静的ターンとして表示される)
			 */
			delete this.#liveAgents[agentName];
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(orchestrator_done/stopped)イベント処理を実行します。<br>
	 */
	async #onSseOrchestratorDone(data) {
		try {
			/*
			 * イベントデータ展開
			 */
			const {} = data;

			/*
			 * 全ライブターンストリーミング解除
			 */
			Object.values(this.#liveAgents).forEach((liveAgent) => {
				liveAgent.turnEl.classList.remove("is-streaming");
				liveAgent.bodyEl.classList.remove("streaming");
			});
			this.#liveAgents = {};

			/*
			 * ログ再読み込み
			 */
			await this.#loadLog();
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(disconnected)イベント処理を実行します。<br>
	 */
	#onSseDisconnected(data) {
		try {
			/*
			 * イベントデータ展開
			 */
			const {} = data;

			/*
			 * 全ライブターンストリーミング解除
			 */
			Object.values(this.#liveAgents).forEach((liveAgent) => {
				liveAgent.turnEl.classList.remove("is-streaming");
				liveAgent.bodyEl.classList.remove("streaming");
			});
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}
}
