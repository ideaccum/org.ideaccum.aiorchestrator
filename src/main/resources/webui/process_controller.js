/**
 * エージェントコントローラーページスクリプトクラスです。<br>
 * <p>
 * HeaderControllerが配信するSSEカスタムイベント(sse-event)を受信して
 * エージェントカードグリッドの表示管理を担当します。<br>
 * ヘッダー領域の更新はHeaderControllerが行うため、このクラスはカード管理に専念します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/04/30	Kitagawa		新規作成
 *-->
 */
class ProcessController {
	/** カードペインオブジェクトマップ(キー:エージェント名) */
	#cards;

	/** オーナーカードオブジェクト */
	#ownerCard;

	/* DOM参照(エージェントカードグリッド要素) */
	#elAgentsGrid;

	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
			marked.setOptions({
				breaks: true, // GitHub Flavored Markdown を使用
				gfm: true, // 単一の改行(\n)を<br>に変換
			});
			this.#cards = {};
			this.#ownerCard = null;
			this.#elAgentsGrid = document.getElementById("agents-grid");
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * ページスクリプトを初期化します。<br>
	 */
	async init() {
		try {
			/*
			 * イベントハンドラ登録(非同期処理前に登録してSSEイベントを取りこぼさない)
			 */
			document.addEventListener(Constants.SSE_EVENT_NAME, (event) => this.#handleEvent(event.detail));

			/*
			 * オーナーカード初期化
			 */
			await this.#initOwnerCard();

			/*
			 * カレントプロジェクトエージェントカード初期表示
			 */
			await this.#loadInitialCards();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * オーナーカードを初期化して現在のオーケストレーターステータスに合わせて状態を設定します。<br>
	 */
	async #initOwnerCard() {
		try {
			this.#ownerCard = {
				el: document.getElementById("owner-card"),
				promptTextarea: document.getElementById("owner-prompt-input"),
				startBtn: document.getElementById("owner-start-btn"),
				stopBtn: document.getElementById("owner-stop-btn"),
				historyEl: document.getElementById("owner-history"),
				currentText: null,
				currentRaw: "",
				turnCount: 0,
			};
			this.#ownerCard.startBtn.onclick = () => this.#onOwnerStart();
			this.#ownerCard.stopBtn.onclick = () => this.#onOwnerStop();

			// プリセットボタン
			const setPreset = (id, prompt) => {
				const btn = document.getElementById(id);
				if (btn) btn.onclick = () => {
					if (!this.#ownerCard.promptTextarea.disabled) {
						this.#ownerCard.promptTextarea.value = prompt;
						this.#ownerCard.promptTextarea.focus();
					}
				};
			};
			setPreset("owner-preset-check",   Constants.PROMPT_CHECK_AGENTS);
			setPreset("owner-preset-summary",  Constants.PROMPT_SUMMARY);
			setPreset("owner-preset-next",     Constants.PROMPT_NEXT_ACTION);

			const status = await WebAPI.getOrchestratorStatus({});
			if (!status?.data?.projectSelected) {
				return;
			}
			this.#ownerCard.el.style.display = "";
			if (status.data.running) {
				this.#setOwnerRunning();
			} else {
				this.#setOwnerIdle();
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 実行ボタンクリック時の処理を実行します。<br>
	 */
	async #onOwnerStart() {
		try {
			const prompt = this.#ownerCard.promptTextarea.value.trim();
			if (!prompt) {
				Utils.showError("プロンプトを入力してください。");
				return;
			}
			// APIレスポンス待機中も即座に非活性化して二重クリックを防止
			this.#ownerCard.startBtn.disabled = true;
			this.#ownerCard.promptTextarea.disabled = true;
			const result = await WebAPI.startOrchestrator({ prompt });
			if (!result) {
				// エラー時はアイドル状態に戻す(成功時はSSEイベントで#setOwnerRunningが呼ばれる)
				this.#setOwnerIdle();
			} else {
				// 成功時はテキストエリアをクリア
				this.#ownerCard.promptTextarea.value = "";
			}
		} catch (e) {
			Utils.catchFatal(e);
			this.#setOwnerIdle();
		}
	}

	/**
	 * 停止ボタンクリック時の処理を実行します。<br>
	 */
	async #onOwnerStop() {
		try {
			this.#ownerCard.stopBtn.disabled = true;
			await WebAPI.stopOrchestrator({});
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * オーナーカードを実行中状態に設定します。<br>
	 */
	#setOwnerRunning() {
		if (!this.#ownerCard) {
			return;
		}
		this.#ownerCard.promptTextarea.disabled = true;
		this.#ownerCard.startBtn.style.display = "none";
		this.#ownerCard.stopBtn.style.display = "";
		this.#ownerCard.stopBtn.disabled = false;
		this.#ownerCard.el.querySelectorAll(".btn-preset").forEach((btn) => (btn.disabled = true));
	}

	/**
	 * オーナーカードをアイドル状態に設定します。<br>
	 */
	#setOwnerIdle() {
		if (!this.#ownerCard) {
			return;
		}
		this.#ownerCard.promptTextarea.disabled = false;
		this.#ownerCard.startBtn.style.display = "";
		this.#ownerCard.startBtn.disabled = false;
		this.#ownerCard.stopBtn.style.display = "none";
		this.#ownerCard.el.querySelectorAll(".btn-preset").forEach((btn) => (btn.disabled = false));
	}

	/**
	 * プロジェクトのエージェント一覧をAPIから取得してアイドル状態のカードを初期表示します。<br>
	 * 非同期処理中にSSEイベントでカードが生成された場合は上書きしません。<br>
	 */
	async #loadInitialCards() {
		try {
			/*
			 * カレントプロジェクト取得
			 */
			const projectResult = await WebAPI.getCurrentProject({
				_: null,
			});
			if (!projectResult) {
				return;
			}

			const projectName = projectResult.data?.project;
			if (!projectName) {
				return;
			}

			/*
			 * エージェント一覧取得
			 */
			const agentResult = await WebAPI.getAgentList({
				project: projectName,
			});
			if (!agentResult) {
				return;
			}
			const agents = agentResult.data;
			if (!agents || agents.length === 0) {
				return;
			}

			/*
			 * クライアント後処理
			 */
			// SSEイベントでカードが先に生成された場合はスキップ
			if (Object.keys(this.#cards).length > 0) {
				return;
			}
			// カードグリッド表示＆カード生成
			this.#elAgentsGrid.innerHTML = "";
			this.#elAgentsGrid.style.display = "grid";
			agents.forEach((agent) => {
				const card = this.#createCard(agent);
				this.#elAgentsGrid.appendChild(card.el);
				this.#cards[agent.name] = card;
			});
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * スクロール位置を最下部へ自動スクロールします。<br>
	 * @param {HTMLElement} element - スクロール対象要素
	 */
	#autoScroll(element) {
		try {
			element.scrollTop = element.scrollHeight;
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * エージェント情報からカード要素を生成して操作用の参照オブジェクトを提供します。<br>
	 * @param {{
	 *   name: string,
	 *   leader: boolean,
	 *   type: string,
	 *   model: string
	 * }} agent - サーバーから受け取ったinitイベントのagents配列要素
	 */
	#createCard(agent) {
		try {
			const initial = (agent.name || "?").charAt(0).toUpperCase();
			const avatarColor = Utils.avatarColor(agent.name || "");
			const elCard = document.createElement("div");
			elCard.className = "card agent-card";
			elCard.innerHTML = `
				<div class="card-header">
					<div class="card-avatar" style="background: ${avatarColor}">${initial}</div>
					<span class="agent-name">${Utils.esc(agent.name)}</span>
					${agent.leader ? '<span class="tag">LEADER</span>' : ""}
					<div class="agent-meta">
						<div class="meta-type">${Utils.esc(agent.type || "-")}</div>
						<div class="meta-model">${Utils.esc(agent.model || "-")}</div>
					</div>
				</div>
				<div class="card-body scrollbar-thin" data-body>
					<div class="idle-placeholder">リクエスト待ち</div>
				</div>
				<div class="card-message scrollbar-thin" data-message>リクエストはまだ受け取っていません。</div>
				<div class="card-footer" data-footer>
					<span data-tokens>Tokens: -</span>
					<span data-turn-elapsed>Time: -</span>
					<span class="footer-session" data-session>-</span>
				</div>
			`;
			return {
				el: elCard,
				body: elCard.querySelector("[data-body]"),
				footer: elCard.querySelector("[data-footer]"),
				message: elCard.querySelector("[data-message]"),
				tokensEl: elCard.querySelector("[data-tokens]"),
				turnElapsed: elCard.querySelector("[data-turn-elapsed]"),
				sessionEl: elCard.querySelector("[data-session]"),
				currentText: null,
				currentRaw: "",
				turnCount: 0,
				totalTokens: 0,
			};
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * SSEイベントをカード管理ハンドラへディスパッチします。<br>
	 * ヘッダー関連イベントはHeaderControllerが処理済みのためここでは無視します。<br>
	 * @param {{ type: string }} data - パース済みSSEイベント
	 */
	#handleEvent(data) {
		try {
			/*
			 * オーナーSSEイベント(agentName==="Owner"のagent_start/content/finish)を優先処理
			 */
			if (this.#ownerCard && data.agentName === Constants.OWNER_AGENT_NAME) {
				if (data.type === Constants.SSE_TYPE_AGENT_START) {
					this.#onOwnerTurnStart(data.timestamp);
					return;
				} else if (data.type === Constants.SSE_TYPE_AGENT_CONTENT) {
					this.#onOwnerTurnContent(data.content);
					return;
				} else if (data.type === Constants.SSE_TYPE_AGENT_FINISH) {
					this.#onOwnerTurnFinish();
					return;
				}
			}

			/*
			 * オーナーカード状態更新
			 */
			if (this.#ownerCard) {
				if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_STARTED) {
					this.#setOwnerRunning();
				} else if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_DONE || data.type === Constants.SSE_TYPE_ORCHESTRATOR_STOPPED || data.type === Constants.SSE_TYPE_DISCONNECTED) {
					this.#setOwnerIdle();
				} else if (data.type === Constants.SSE_TYPE_AGENT_INITIALIZED) {
					// 履歴リセット(preloadBuffer/executeがフル再生するため重複を防ぐ)
					// 実行中/アイドル状態は変更しない(orchestrator_startedの後にagent_initializedが届くため)
					this.#ownerCard.el.style.display = "";
					this.#ownerCard.historyEl.innerHTML = '<div class="idle-placeholder">プロンプト待ち</div>';
					this.#ownerCard.currentText = null;
					this.#ownerCard.currentRaw = "";
					this.#ownerCard.turnCount = 0;
				}
			}

			/*
			 * エージェントカード更新
			 */
			if (data.type === Constants.SSE_TYPE_AGENT_INITIALIZED) {
				this.#onSseAgentInitialized(data.agents);
			} else if (data.type === Constants.SSE_TYPE_AGENT_START) {
				this.#onSseAgentStart(data.agentName, data.sessionId, data.timestamp);
			} else if (data.type === Constants.SSE_TYPE_AGENT_CONTENT) {
				this.#onSseAgentContent(data.agentName, data.content);
			} else if (data.type === Constants.SSE_TYPE_AGENT_MESSAGE) {
				this.#onSseAgentMessage(data.agentName, data.message);
			} else if (data.type === Constants.SSE_TYPE_AGENT_FINISH) {
				this.#onSseAgentFinish(data.agentName, data.tokenUsage, data.elapsedTime, data.sessionId);
			} else if (data.type === Constants.SSE_TYPE_AGENT_ERROR) {
				this.#onSseAgentError(data.agentName, data.message);
			} else if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_DONE || data.type === Constants.SSE_TYPE_ORCHESTRATOR_STOPPED) {
				this.#onSseOrchestratorDone();
			} else if (data.type === Constants.SSE_TYPE_DISCONNECTED) {
				this.#onSseOrchestratorDone();
			}
			// connected はヘッダーが処理するため無視
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_initialized)イベントのカードグリッド処理を実行します。<br>
	 * @param {Array} agents - エージェント情報の配列
	 */
	#onSseAgentInitialized(agents) {
		try {
			/*
			 * エージェントカードレイアウト初期化
			 */
			this.#elAgentsGrid.style.display = "grid";
			this.#elAgentsGrid.innerHTML = "";
			Object.keys(this.#cards).forEach((agentName) => delete this.#cards[agentName]);

			/*
			 * エージェントカード生成
			 */
			agents.forEach((agent) => {
				const card = this.#createCard(agent);
				this.#elAgentsGrid.appendChild(card.el);
				this.#cards[agent.name] = card;
			});
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_start)イベントのカード処理を実行します。<br>
	 * @param {string} agentName - エージェント名
	 * @param {string} sessionId - セッションID
	 * @param {string} timestamp - タイムスタンプ
	 */
	#onSseAgentStart(agentName, sessionId, timestamp) {
		try {
			/*
			 * 対象カード取得
			 */
			const card = this.#cards[agentName];
			if (!card) {
				return;
			}

			/*
			 * カード状態更新＆ターンブロック追加
			 */
			card.el.classList.remove("is-done", "is-error");
			card.el.classList.add("is-processing");
			card.body.querySelector(".idle-placeholder")?.remove();

			/*
			 * ターンブロック生成
			 */
			card.turnCount++;
			const turnBlock = document.createElement("div");
			turnBlock.className = "turn-block";
			turnBlock.innerHTML = `
				<div class="turn-meta">
					<span class="turn-num">Turn ${card.turnCount}</span>
					<span>${Utils.esc(timestamp || "")}</span>
				</div>
				<div class="turn-text md-content streaming" data-turn-text></div>
			`;
			card.body.appendChild(turnBlock);
			card.currentText = turnBlock.querySelector("[data-turn-text]");
			card.currentRaw = "";

			/*
			 * セッションID設定
			 */
			if (sessionId) {
				card.sessionEl.textContent = sessionId;
			}
			this.#autoScroll(card.body);
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_content)イベントのカード処理を実行します。<br>
	 * @param {string} agentName - エージェント名
	 * @param {string} content - テキスト断片
	 */
	#onSseAgentContent(agentName, content) {
		try {
			/*
			 * 対象カード取得
			 */
			const card = this.#cards[agentName];
			if (!card || !card.currentText) {
				return;
			}

			/*
			 * カレントテキスト更新
			 */
			card.currentRaw += content;
			card.currentText.innerHTML = marked.parse(card.currentRaw);
			this.#autoScroll(card.body);
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_message)イベントのカード処理を実行します。<br>
	 * @param {string} agentName - エージェント名
	 * @param {string} message - メッセージ
	 */
	#onSseAgentMessage(agentName, message) {
		try {
			/*
			 * 対象カード取得
			 */
			const card = this.#cards[agentName];
			if (!card) {
				return;
			}

			/*
			 * メッセージ表示
			 */
			card.message.textContent = message || "";
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_finish)イベントのカード処理を実行します。<br>
	 * @param {string} agentName - エージェント名
	 * @param {number} tokenUsage - 消費トークン数
	 * @param {number} elapsedTime - ターン処理時間(ms)
	 * @param {string} sessionId - セッションID
	 */
	#onSseAgentFinish(agentName, tokenUsage, elapsedTime, sessionId) {
		try {
			/*
			 * 対象カード取得
			 */
			const card = this.#cards[agentName];
			if (!card) {
				return;
			}

			/*
			 * カード状態更新
			 */
			if (card.currentText) {
				card.currentText.classList.remove("streaming");
				card.currentText.innerHTML = card.currentRaw.trim() === "" ? '<em style="color:var(--color-text-muted)">(レスポンスなし)</em>' : marked.parse(card.currentRaw);
				card.currentText = null;
			}
			card.el.classList.remove("is-processing", "is-error");
			card.el.classList.add("is-done");
			if (tokenUsage != null) {
				card.totalTokens = Number(tokenUsage);
			}
			card.tokensEl.textContent = card.totalTokens > 0 ? "Tokens: " + card.totalTokens.toLocaleString("ja-JP") : "Tokens: -";
			card.turnElapsed.textContent = elapsedTime != null ? "Time: " + (elapsedTime / 1000).toFixed(1) + "s" : "Time: -";

			/*
			 * セッションID設定
			 */
			card.sessionEl.textContent = sessionId || "-";
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_error)イベントのカード処理を実行します。<br>
	 * @param {string} agentName - エージェント名
	 * @param {string} message - エラーメッセージ
	 */
	#onSseAgentError(agentName, message) {
		try {
			/*
			 * 対象カード取得
			 */
			const card = this.#cards[agentName];
			if (!card) {
				return;
			}

			/*
			 * カード状態更新
			 */
			if (card.currentText) {
				card.currentText.classList.remove("streaming");
				card.currentText = null;
			}
			card.el.classList.remove("is-processing", "is-done");
			card.el.classList.add("is-error");

			/*
			 * エラーメッセージ表示
			 */
			if (message) {
				const errorBlock = document.createElement("div");
				errorBlock.className = "turn-error";
				errorBlock.textContent = "⚠️" + " " + message;
				card.body.appendChild(errorBlock);
				this.#autoScroll(card.body);
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * SSE(orchestrator_done / orchestrator_stopped / disconnected)イベントのカード処理を実行します。<br>
	 * 全カードの状態をアイドル(グレー)に戻し、ストリーミング中のテキストを確定します。<br>
	 */
	#onSseOrchestratorDone() {
		try {
			Object.values(this.#cards).forEach((card) => {
				if (card.currentText) {
					card.currentText.classList.remove("streaming");
					card.currentText = null;
				}
				card.el.classList.remove("is-processing", "is-done", "is-error");
			});
			// オーナーストリーミング中テキスト確定
			if (this.#ownerCard?.currentText) {
				this.#onOwnerTurnFinish();
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * オーナーターンブロックのSSE(agent_start)処理を実行します。<br>
	 * @param {string} timestamp - タイムスタンプ
	 */
	#onOwnerTurnStart(timestamp) {
		try {
			if (!this.#ownerCard) {
				return;
			}
			this.#ownerCard.historyEl.querySelector(".idle-placeholder")?.remove();
			this.#ownerCard.turnCount++;
			const turnBlock = document.createElement("div");
			turnBlock.className = "turn-block";
			turnBlock.innerHTML = `
				<div class="turn-meta">
					<span class="turn-num">Turn ${this.#ownerCard.turnCount}</span>
					<span>${Utils.esc(timestamp || "")}</span>
				</div>
				<div class="turn-text md-content streaming" data-turn-text></div>
			`;
			this.#ownerCard.historyEl.appendChild(turnBlock);
			this.#ownerCard.currentText = turnBlock.querySelector("[data-turn-text]");
			this.#ownerCard.currentRaw = "";
			this.#autoScroll(this.#ownerCard.historyEl);
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * オーナーターンブロックのSSE(agent_content)処理を実行します。<br>
	 * @param {string} content - テキスト断片
	 */
	#onOwnerTurnContent(content) {
		try {
			if (!this.#ownerCard?.currentText) {
				return
			};
			this.#ownerCard.currentRaw += content;
			this.#ownerCard.currentText.innerHTML = marked.parse(this.#ownerCard.currentRaw);
			this.#autoScroll(this.#ownerCard.historyEl);
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * OwnerターンブロックのSSE(agent_finish)処理を実行します。<br>
	 */
	#onOwnerTurnFinish() {
		try {
			if (!this.#ownerCard?.currentText) {
				return;
			}
			this.#ownerCard.currentText.classList.remove("streaming");
			this.#ownerCard.currentText.innerHTML = this.#ownerCard.currentRaw.trim() === "" ? '<em style="color:var(--color-text-muted)">(内容なし)</em>' : marked.parse(this.#ownerCard.currentRaw);
			this.#ownerCard.currentText = null;
		} catch (e) {
			Utils.catchFatal(e);
		}
	}
}
