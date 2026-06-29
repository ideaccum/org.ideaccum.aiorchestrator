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
class ProcessMonitorController {
	/** カードペインオブジェクトマップ(キー:エージェント名) */
	#cards;

	/** オーナーカードオブジェクト */
	#ownerCard;

	/* DOM参照(エージェントカードグリッド要素) */
	#agentsGridEl;

	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
			this.#cards = {};
			this.#ownerCard = null;
			this.#agentsGridEl = document.getElementById("agents-grid");
		} catch (e) {
			WebUI.catchFatal(e);
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
			WebUI.catchFatal(e);
		}
	}

	/**
	 * プロンプトプリセットボタンをサーバーから取得して描画します。<br>
	 */
	async #initPresetButtons() {
		try {
			/*
			 * プロンプトプリセット取得
			 */
			await WebCtrl.doAwait(WebAPI.getPresetPrompts({}, false), {
				onDone: (result) => {
					const presetRowEl = this.#ownerCard.cardEl.querySelector(".preset-row");
					const presets = result.data || [];
					presets.forEach((preset) => {
						const presetBtn = document.createElement("button");
						presetBtn.className = "btn btn-preset";
						presetBtn.type = "button";
						presetBtn.textContent = preset.label || "";
						presetBtn.addEventListener("click", () => {
							if (!this.#ownerCard.promptTextarea.disabled) {
								this.#ownerCard.promptTextarea.value = (preset.prompt || "").trimEnd();
								this.#ownerCard.promptTextarea.focus();
							}
						});
						presetRowEl.appendChild(presetBtn);
					});
				},
			});
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * オーナーカードを初期化して現在のオーケストレーターステータスに合わせて状態を設定します。<br>
	 */
	async #initOwnerCard() {
		try {
			/*
			 * オーナーカード要素生成
			 */
			this.#ownerCard = {
				cardEl: document.getElementById("owner-card"),
				promptTextarea: document.getElementById("owner-prompt-input"),
				startBtn: document.getElementById("owner-start-btn"),
				stopBtn: document.getElementById("owner-stop-btn"),
				historyEl: document.getElementById("owner-history"),
				currentText: null,
				currentRaw: "",
				turnCount: 0,
			};
			this.#ownerCard.startBtn.onclick = () => this.#onClickStart();
			this.#ownerCard.stopBtn.onclick = () => this.#onClickStop();

			/*
			 * プロンプトプリセットボタン読み込み
			 */
			await this.#initPresetButtons();

			/*
			 * オーケストレーターステータス取得＆オーナーカード状態設定
			 */
			await WebCtrl.doAwait(WebAPI.getOrchestratorStatus({}, false), {
				onDone: async (status) => {
					/*
					 * プロジェクト未選択時(通常はコントローラー画面は表示されないがリスクヘッジ)
					 */
					if (!status?.data?.projectSelected) {
						return;
					}

					/*
					 * オーナーカード表示/状態設定
					 */
					this.#ownerCard.cardEl.classList.remove("hidden");
					if (status.data.running) {
						this.#setOwnerRunning();
					} else {
						this.#setOwnerIdle();
					}
				},
				onError: () => {
					WebUI.showError("オーケストレーターステータスの取得に失敗しました。");
				},
			});
		} catch (e) {
			WebUI.catchFatal(e);
		}
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
			await WebCtrl.doAwait(WebAPI.getCurrentProject({}), {
				onDone: async (projectResult) => {
					/*
					 * プロジェクト名取得
					 */
					const projectName = projectResult.data?.project;
					if (!projectName) {
						return;
					}

					/*
					 * エージェント一覧取得
					 */
					await WebCtrl.doAwait(
						WebAPI.getAgentList({
							project: projectName, // プロジェクト名
						}),
						{
							onDone: async (agentResult) => {
								/*
								 * エージェント情報取得
								 */
								const agents = agentResult.data;
								if (!agents || agents.length === 0) {
									return;
								}

								/*
								 * SSEイベント先行でカード生成されている場合は後続処理スキップ
								 */
								if (Object.keys(this.#cards).length > 0) {
									return;
								}

								/*
								 * カードグリッド表示/カード生成
								 */
								this.#agentsGridEl.innerHTML = "";
								this.#agentsGridEl.classList.remove("hidden");
								agents.forEach((agent) => {
									const card = this.#createCard(agent);
									this.#agentsGridEl.appendChild(card.cardEl);
									this.#cards[agent.name] = card;
								});
							},
							onError: () => {
								WebUI.showError("エージェント一覧の取得に失敗しました。");
							},
						},
					);
				},
				onError: () => {
					WebUI.showError("カレントプロジェクトの取得に失敗しました。");
				},
			});
		} catch (e) {
			WebUI.catchFatal(e);
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
	 * @returns {{
	 *   cardEl: HTMLElement,
	 *   bodyEl: HTMLElement,
	 *   footerEl: HTMLElement,
	 *   messageEl: HTMLElement,
	 *   tokensEl: HTMLElement,
	 *   turnElapsedEl: HTMLElement,
	 *   sessionEl: HTMLElement,
	 *   spinnerEl: HTMLElement,
	 *   currentText: string | null,
	 *   currentRaw: string,
	 *   turnCount: number,
	 *   totalTokens: number,
	 *   totalElapsedMs: number,
	 *   startTimeMs: number,
	 *   elapsedTimer: ReturnType<typeof setInterval> | null
	 * }} - カード要素と操作用の参照オブジェクト
	 */
	#createCard(agent) {
		try {
			const agentInitial = (agent.name || "?").charAt(0).toUpperCase();
			const agentColor = Utils.avatarColor(agent.name || "");
			const cardEl = document.createElement("div");
			cardEl.className = "card agent-card";
			cardEl.innerHTML = `
				<div class="card-header">
					<div class="card-avatar" style="background: ${agentColor}">${agentInitial}</div>
					<span class="agent-name">${Utils.esc(agent.name)}</span>
					${agent.leader ? '<span class="tag">LEADER</span>' : ""}
					<div class="loading-spinner loading-spinner-sm hidden" data-spinner></div>
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
					<span data-tokens>0トークン</span>
					<span data-turn-elapsed>0秒</span>
					<span class="footer-session" data-session>-</span>
				</div>
			`;
			return {
				cardEl: cardEl,
				bodyEl: cardEl.querySelector("[data-body]"),
				footerEl: cardEl.querySelector("[data-footer]"),
				messageEl: cardEl.querySelector("[data-message]"),
				tokensEl: cardEl.querySelector("[data-tokens]"),
				turnElapsedEl: cardEl.querySelector("[data-turn-elapsed]"),
				sessionEl: cardEl.querySelector("[data-session]"),
				spinnerEl: cardEl.querySelector("[data-spinner]"),
				currentText: null,
				currentRaw: "",
				turnCount: 0,
				totalTokens: 0,
				totalElapsedMs: 0,
				startTimeMs: 0,
				elapsedTimer: null,
			};
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * オーナーカードを実行中状態に設定します。<br>
	 */
	#setOwnerRunning() {
		this.#ownerCard.promptTextarea.disabled = true;
		this.#ownerCard.startBtn.classList.add("hidden");
		this.#ownerCard.stopBtn.classList.remove("hidden");
		this.#ownerCard.stopBtn.disabled = false;
		this.#ownerCard.cardEl.querySelectorAll(".btn-preset").forEach((presetBtn) => {
			presetBtn.disabled = true;
		});
	}

	/**
	 * オーナーカードをアイドル状態に設定します。<br>
	 */
	#setOwnerIdle() {
		this.#ownerCard.promptTextarea.disabled = false;
		this.#ownerCard.startBtn.classList.remove("hidden");
		this.#ownerCard.startBtn.disabled = false;
		this.#ownerCard.stopBtn.classList.add("hidden");
		this.#ownerCard.cardEl.querySelectorAll(".btn-preset").forEach((presetBtn) => {
			presetBtn.disabled = false;
		});
	}

	/**
	 * 実行ボタンクリック時の処理を実行します。<br>
	 */
	async #onClickStart() {
		try {
			/*
			 * プロンプト入力チェック
			 */
			const prompt = this.#ownerCard.promptTextarea.value.trim();
			if (!prompt) {
				WebUI.showError("プロンプトを入力してください。");
				return;
			}

			/*
			 * 誤操作抑止のため先行非活性
			 */
			this.#ownerCard.startBtn.disabled = true;
			this.#ownerCard.promptTextarea.disabled = true;

			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAwait(
				WebAPI.startOrchestrator({
					prompt, // 実行プロンプト
				}),
				{
					onDone: async () => {
						///*
						// * メッセージ表示
						// */
						//WebUI.showInfo("エージェント処理を開始しました。");

						/*
						 * プロンプトエリアクリア
						 */
						this.#ownerCard.promptTextarea.value = "";
					},
					onError: () => {
						/*
						 * エラー表示
						 */
						WebUI.showError("エージェント処理の開始に失敗しました。");

						/*
						 * アイドル状態設定
						 */
						this.#setOwnerIdle();
					},
				},
			);
		} catch (e) {
			WebUI.catchFatal(e);
			this.#setOwnerIdle();
		}
	}

	/**
	 * 停止ボタンクリック時の処理を実行します。<br>
	 */
	async #onClickStop() {
		try {
			/*
			 * 誤操作抑止のため先行非活性
			 */
			this.#ownerCard.stopBtn.disabled = true;

			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAwait(WebAPI.stopOrchestrator({}), {
				onDone: async () => {
					///*
					// * メッセージ表示
					// */
					//WebUI.showInfo("エージェント処理を停止します。\n完全に停止するまでに時間がかかる場合があります。");
				},
				onError: () => {
					/*
					 * エラー表示
					 */
					WebUI.showError("エージェント処理の停止に失敗しました。");
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
			if (data.type === Constants.SSE_TYPE_AGENT_INITIALIZED) {
				this.#onSseAgentInitialized(data);
			} else if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_STARTED) {
				this.#onSseOrchestratorStarted(data);
			} else if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_DONE || data.type === Constants.SSE_TYPE_ORCHESTRATOR_STOPPED || data.type === Constants.SSE_TYPE_DISCONNECTED) {
				this.#onSseOrchestratorDone(data);
			} else if (data.type === Constants.SSE_TYPE_AGENT_START) {
				this.#onSseAgentStart(data);
			} else if (data.type === Constants.SSE_TYPE_AGENT_CONTENT) {
				this.#onSseAgentContent(data);
			} else if (data.type === Constants.SSE_TYPE_AGENT_MESSAGE) {
				this.#onSseAgentMessage(data);
			} else if (data.type === Constants.SSE_TYPE_AGENT_FINISH) {
				this.#onSseAgentFinish(data);
			} else if (data.type === Constants.SSE_TYPE_AGENT_ERROR) {
				this.#onSseAgentError(data);
			} else if (data.type === Constants.SSE_TYPE_AGENT_SESSION_UPDATED) {
				this.#onSseAgentSessionUpdated(data);
			}
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(orchestrator_started)イベント処理を実行します。<br>
	 */
	async #onSseOrchestratorStarted(data) {
		try {
			/*
			 * オーナーカードを実行中状態に設定
			 */
			this.#setOwnerRunning();
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_initialized)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	#onSseAgentInitialized(data) {
		try {
			const { agents } = data;

			/*
			 * オーナーカード初期化
			 */
			this.#ownerCard.cardEl.classList.remove("hidden");
			this.#ownerCard.historyEl.innerHTML = '<div class="idle-placeholder">プロンプト待ち</div>';
			this.#ownerCard.currentText = null;
			this.#ownerCard.currentRaw = "";
			this.#ownerCard.turnCount = 0;

			/*
			 * エージェントカードレイアウト初期化
			 */
			this.#agentsGridEl.classList.remove("hidden");
			this.#agentsGridEl.innerHTML = "";
			Object.keys(this.#cards).forEach((agentName) => {
				delete this.#cards[agentName];
			});

			/*
			 * エージェントカード生成
			 */
			agents.forEach((agent) => {
				const card = this.#createCard(agent);
				this.#agentsGridEl.appendChild(card.cardEl);
				this.#cards[agent.name] = card;
			});
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
			const { agentName, sessionId, timestamp } = data;

			/*
			 * オーナーエージェントの場合はオーナーカード履歴を更新して処理終了
			 */
			if (agentName === Constants.OWNER_AGENT_NAME) {
				this.#ownerCard.historyEl.querySelector(".idle-placeholder")?.remove();
				this.#ownerCard.turnCount++;
				const turnBlockEl = document.createElement("div");
				turnBlockEl.className = "turn-block clip-copy-block";
				turnBlockEl.innerHTML = `
					<div class="turn-meta">
						<span class="turn-num">Turn ${this.#ownerCard.turnCount}</span>
						<span>${Utils.esc(timestamp || "")}</span>
					</div>
					<div class="turn-text md-content streaming" data-turn-text></div>
				`;
				turnBlockEl.querySelector(".turn-meta").appendChild(WebUI.createCopyButton(() => turnBlockEl._rawText || ""));
				this.#ownerCard.historyEl.appendChild(turnBlockEl);
				this.#ownerCard.currentText = turnBlockEl.querySelector("[data-turn-text]");
				this.#ownerCard.currentRaw = "";
				WebUI.scrollBottom(this.#ownerCard.historyEl);
				return;
			}

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
			card.cardEl.classList.remove("is-done", "is-error");
			card.cardEl.classList.add("is-processing");
			if (card.spinnerEl) {
				card.spinnerEl.classList.remove("hidden");
			}
			card.bodyEl.querySelector(".idle-placeholder")?.remove();

			/*
			 * ストリーミング中の場合はターン確定(リトライ時等)
			 */
			if (card.currentText) {
				card.currentText.classList.remove("streaming");
				card.currentText = null;
			}

			/*
			 * ターンブロック生成
			 */
			card.turnCount++;
			const turnBlockEl = document.createElement("div");
			turnBlockEl.className = "turn-block clip-copy-block";
			turnBlockEl.innerHTML = `
				<div class="turn-meta">
					<span class="turn-num">Turn ${card.turnCount}</span>
					<span>${Utils.esc(timestamp || "")}</span>
				</div>
				<div class="turn-text md-content streaming" data-turn-text></div>
			`;
			turnBlockEl.querySelector(".turn-meta").appendChild(WebUI.createCopyButton(() => turnBlockEl._rawText || ""));
			card.bodyEl.appendChild(turnBlockEl);
			card.currentText = turnBlockEl.querySelector("[data-turn-text]");
			card.currentRaw = "";

			/*
			 * セッションID設定
			 */
			if (sessionId) {
				card.sessionEl.textContent = sessionId;
			}

			/*
			 * 所要時間タイマー開始(サーバー計測はプロセス完了後のため、クライアント側で1秒ごとに表示更新する)
			 * リトライ時等で既存タイマーが残っている場合は停止してから再起動する
			 * ページ再表示時はtimestampから実際のターン開始時刻を復元して経過時間を引き継ぐ
			 */
			if (card.elapsedTimer !== null) {
				clearInterval(card.elapsedTimer);
			}
			const parsedStartMs = (() => {
				if (!timestamp) {
					return null;
				}
				const matcher = timestamp.match(/(\d{4})\/(\d{2})\/(\d{2}) (\d{2}):(\d{2}):(\d{2})/);
				if (!matcher) {
					return null;
				}
				return new Date(parseInt(matcher[1]), parseInt(matcher[2]) - 1, parseInt(matcher[3]), parseInt(matcher[4]), parseInt(matcher[5]), parseInt(matcher[6])).getTime();
			})();
			card.startTimeMs = parsedStartMs ?? Date.now();
			card.turnElapsedEl.textContent = Utils.formatDurationMs(card.totalElapsedMs + (Date.now() - card.startTimeMs));
			card.elapsedTimer = setInterval(() => {
				card.turnElapsedEl.textContent = Utils.formatDurationMs(card.totalElapsedMs + (Date.now() - card.startTimeMs));
			}, 1000);

			/*
			 * 自動スクロール
			 */
			WebUI.scrollBottom(card.bodyEl);
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
			const { agentName, content } = data;

			/*
			 * オーナーエージェントの場合はオーナーカードのテキストを更新して処理終了
			 */
			if (agentName === Constants.OWNER_AGENT_NAME) {
				if (!this.#ownerCard?.currentText) {
					return;
				}
				this.#ownerCard.currentRaw += content;
				this.#ownerCard.currentText.innerHTML = marked.parse(this.#ownerCard.currentRaw);
				WebUI.scrollBottom(this.#ownerCard.historyEl);
				return;
			}

			/*
			 * 対象カード取得
			 */
			const card = this.#cards[agentName];
			if (!card || !card.currentText) {
				return;
			}

			/*
			 * カレントテキスト更新(キーワード行を除外して描画)
			 */
			card.currentRaw += content;
			card.currentText.innerHTML = marked.parse(Utils.filterKeywordLines(card.currentRaw));

			/*
			 * 自動スクロール
			 */
			WebUI.scrollBottom(card.bodyEl);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_message)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	#onSseAgentMessage(data) {
		try {
			const { agentName, message } = data;

			/*
			 * 対象カード取得
			 */
			const card = this.#cards[agentName];
			if (!card) {
				return;
			}

			/*
			 * メッセージ表示(制御キーワード行を除外)
			 */
			card.messageEl.textContent = Utils.filterKeywordLines(message || "レスポンス待機中です。");
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
			const { agentName, tokenUsage, inputTokens, outputTokens, elapsedTime, sessionId } = data;

			/*
			 * オーナーエージェントの場合はオーナーカードのターンを確定して処理終了
			 */
			if (agentName === Constants.OWNER_AGENT_NAME) {
				if (!this.#ownerCard?.currentText) {
					return;
				}
				this.#ownerCard.currentText.classList.remove("streaming");
				this.#ownerCard.currentText.innerHTML = this.#ownerCard.currentRaw.trim() === "" ? '<em style="color:var(--color-text-muted)">(内容なし)</em>' : marked.parse(this.#ownerCard.currentRaw);
				const ownerTurnBlock = this.#ownerCard.currentText.closest(".turn-block");
				if (ownerTurnBlock) {
					ownerTurnBlock._rawText = this.#ownerCard.currentRaw;
				}
				this.#ownerCard.currentText = null;
				return;
			}

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
				const filtered = Utils.filterKeywordLines(card.currentRaw);
				card.currentText.innerHTML = filtered.trim() === "" ? '<em style="color:var(--color-text-muted)">(レスポンスなし)</em>' : marked.parse(filtered);
				const agentTurnBlock = card.currentText.closest(".turn-block");
				if (agentTurnBlock) {
					agentTurnBlock._rawText = filtered;
				}
				card.currentText = null;
			}
			card.cardEl.classList.remove("is-processing", "is-error");
			card.cardEl.classList.add("is-done");
			if (card.spinnerEl) {
				card.spinnerEl.classList.add("hidden");
			}
			card.totalTokens = Number(tokenUsage || 0);
			if (card.elapsedTimer !== null) {
				clearInterval(card.elapsedTimer);
				card.elapsedTimer = null;
			}
			card.totalElapsedMs += Number(elapsedTime || 0);
			const totalTokens = Number(inputTokens || 0) + Number(outputTokens || 0);
			card.tokensEl.textContent = totalTokens > 0 ? totalTokens.toLocaleString() + "トークン" : "";
			card.turnElapsedEl.textContent = card.totalElapsedMs > 0 ? Utils.formatDurationMs(card.totalElapsedMs) : "-";

			/*
			 * セッションID設定
			 */
			card.sessionEl.textContent = sessionId || "-";
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
			const { agentName, message } = data;

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
			card.cardEl.classList.remove("is-processing", "is-done");
			card.cardEl.classList.add("is-error");

			/*
			 * エラーメッセージ表示
			 */
			if (message) {
				const errorBlockEl = document.createElement("div");
				errorBlockEl.className = "turn-error";
				errorBlockEl.textContent = "⚠️" + " " + message;
				card.bodyEl.appendChild(errorBlockEl);
			}

			/*
			 * 自動スクロール
			 */
			WebUI.scrollBottom(card.bodyEl);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(agent_session_updated)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	#onSseAgentSessionUpdated(data) {
		try {
			const { agentName, sessionId } = data;
			if (agentName === Constants.OWNER_AGENT_NAME) {
				return;
			}
			const card = this.#cards[agentName];
			if (!card) {
				return;
			}
			card.sessionEl.textContent = sessionId || "-";
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(orchestrator_done / orchestrator_stopped / disconnected)イベント処理を実行します。<br>
	 */
	#onSseOrchestratorDone(data) {
		try {
			/*
			 * オーナーカードをアイドル状態に設定
			 */
			this.#setOwnerIdle();

			/*
			 * 全カード状態更新
			 */
			Object.values(this.#cards).forEach((card) => {
				if (card.currentText) {
					card.currentText.classList.remove("streaming");
					card.currentText = null;
				}
				card.cardEl.classList.remove("is-processing", "is-done", "is-error");
				if (card.spinnerEl) {
					card.spinnerEl.classList.add("hidden");
				}
			});

			/*
			 * オーナーストリーミング中テキスト確定
			 */
			if (this.#ownerCard?.currentText) {
				this.#onSseAgentFinish({ agentName: Constants.OWNER_AGENT_NAME });
			}
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}
}
