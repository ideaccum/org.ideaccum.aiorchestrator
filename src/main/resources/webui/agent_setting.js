/**
 * エージェント設定管理クラスです。<br>
 * <p>
 * プロジェクトに配置するエージェントの一覧表示・作成・編集・削除を提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/01	Kitagawa		新規作成
 *-->
 */
class AgentSettingController {
	/** カレントプロジェクト名 */
	#currentProject;

	/** 現在ロード済みのエージェント一覧 */
	#agents;

	/** 編集中エージェント情報(null=新規作成モード) */
	#editAgent;

	/** 複写元エージェント情報(null=複写でない) */
	#copyAgent;

	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
			this.#currentProject = null;
			this.#agents = [];
			this.#editAgent = null;
			this.#copyAgent = null;
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
			 * カレントプロジェクトエージェント取得
			 */
			await this.#loadCurrentProject();

			/*
			 * エージェント一覧取得
			 */
			await this.#loadAgentList();

			/*
			 * イベントハンドラ登録
			 */
			document.getElementById("btn-new").onclick = (event) => this.#onClickNew(event);
			document.getElementById("btn-save").onclick = (event) => this.#onClickSave(event);
			document.getElementById("agents-list").onclick = (event) => this.#onListPanelClick(event);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * エージェント設定フォームパネルを表示します。<br>
	 * @param {Object|null} agent - 編集対象エージェント情報(null=新規作成モード)
	 */
	#showForm(agent) {
		try {
			/*
			 * 編集対象設定
			 */
			this.#editAgent = agent ?? null;

			/*
			 * パネル切り替え
			 */
			document.getElementById("agent-form-header").classList.remove("hidden");
			document.getElementById("agent-form-panel").classList.remove("hidden");
			document.getElementById("agent-form-empty").classList.add("hidden");

			/*
			 * フォーム要素設定
			 */
			if (this.#editAgent) {
				/*
				 * モード時エージェント情報設定
				 */
				const inConversation = !!agent.inConversation;
				const hasSession = !!agent.sessionId;
				document.getElementById("btn-save").classList.toggle("hidden", inConversation);
				document.getElementById("field-name").value = agent.name || "";
				document.getElementById("field-name").disabled = inConversation;
				document.getElementById("field-type").value = agent.type || Constants.DEFAULT_AGENT;
				document.getElementById("field-type").disabled = inConversation || hasSession;
				document.getElementById("field-model").value = agent.model || "";
				document.getElementById("field-model").disabled = inConversation || hasSession;
				document.getElementById("field-extra-args").value = agent.extraArgs || "";
				document.getElementById("field-extra-args").disabled = inConversation || hasSession;
				document.getElementById("field-leader").checked = !!agent.leader;
				document.getElementById("field-leader").disabled = inConversation || hasSession;
				document.getElementById("field-role").value = agent.role || "";
				document.getElementById("field-role").disabled = inConversation || hasSession;
				document.getElementById("field-personality").value = agent.personality || "";
				document.getElementById("field-personality").disabled = inConversation || hasSession;
				document.getElementById("field-session-id").value = agent.sessionId || "";
				if (inConversation) {
					document.getElementById("session-exists-note").textContent = "* 既に会話に参加しているため、変更することはできません";
					document.getElementById("session-exists-note").classList.remove("hidden");
				} else if (hasSession) {
					document.getElementById("session-exists-note").textContent = "* 既にセッションが存在するため、一部の項目は変更することができません";
					document.getElementById("session-exists-note").classList.remove("hidden");
				} else {
					document.getElementById("session-exists-note").classList.add("hidden");
				}
				document.getElementById("field-name").focus();
			} else {
				/*
				 * 新規/複写モード時初期値設定
				 */
				document.getElementById("btn-save").classList.remove("hidden");
				document.getElementById("field-name").value = "";
				document.getElementById("field-name").disabled = false;
				document.getElementById("field-type").value = Constants.DEFAULT_AGENT;
				document.getElementById("field-type").disabled = false;
				document.getElementById("field-model").value = "";
				document.getElementById("field-model").disabled = false;
				document.getElementById("field-extra-args").value = "";
				document.getElementById("field-extra-args").disabled = false;
				document.getElementById("field-leader").checked = false;
				document.getElementById("field-leader").disabled = false;
				document.getElementById("field-role").value = "";
				document.getElementById("field-role").disabled = false;
				document.getElementById("field-personality").value = "";
				document.getElementById("field-personality").disabled = false;
				document.getElementById("field-session-id").value = "";
				document.getElementById("session-exists-note").classList.add("hidden");
				document.getElementById("field-name").focus();
			}
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * エージェント設定フォームパネルを非表示にします。<br>
	 */
	#hideForm() {
		try {
			document.getElementById("agent-form-header").classList.add("hidden");
			document.getElementById("agent-form-panel").classList.add("hidden");
			document.getElementById("agent-form-empty").classList.remove("hidden");
			this.#editAgent = null;
			this.#copyAgent = null;
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 現在選択中のプロジェクトをサーバーから取得して表示します。<br>
	 */
	async #loadCurrentProject() {
		try {
			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAwait(WebAPI.getCurrentProject({}), {
				onDone: async (result) => {
					this.#currentProject = result.data?.project || null;
				},
				onError: () => {
					WebUI.showError("カレントプロジェクト情報の取得に失敗しました。");
				},
			});
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 選択中プロジェクトのエージェント一覧をサーバーから取得して表示します。<br>
	 * @param {string|null|undefined} [selectName=undefined] - 再描画後に選択するエージェント名(undefinedでLEADER自動選択、nullで選択しない)
	 */
	async #loadAgentList(selectName = undefined) {
		try {
			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAwait(
				WebAPI.getAgentList({
					project: this.#currentProject, // プロジェクト名
				}),
				{
					onDone: async (result) => {
						/*
						 * エージェント一覧取得
						 */
						this.#agents = result.data || [];

						/*
						 * エージェントリスト要素取得
						 */
						const agentListEl = document.getElementById("agents-list");
						agentListEl.innerHTML = "";

						/*
						 * エージェント一覧描画
						 */
						if (this.#agents.length === 0) {
							// エージェントが存在しない場合
							const emptyAgentEl = document.createElement("div");
							emptyAgentEl.className = "empty-msg list-item-ali-empty";
							emptyAgentEl.textContent = "エージェントがありません";
							agentListEl.appendChild(emptyAgentEl);
							this.#hideForm();
						} else {
							// エージェントが存在する場合
							this.#agents.forEach((agent) => {
								const agentItemEl = document.createElement("div");
								agentItemEl.className = "list-item";
								agentItemEl.dataset.name = agent.name;
								agentItemEl.innerHTML = `
									<div class="list-item-icon">${Constants.ICON_AGENT}</div>
									<div class="list-item-content">
										<div class="list-item-ali-info">
											<span class="list-item-ali-label">${Utils.esc(agent.name)}</span>
											<span class="list-item-ali-caption">${Utils.esc(agent.role)}</span>
											${agent.leader ? '<span class="tag">LEADER</span>' : ""}
										</div>
										<div class="list-item-ali-meta">
											<span>${Utils.esc(agent.type)}</span>
											${agent.model ? "<span>" + Utils.esc(agent.model) + "</span>" : ""}
										</div>
									</div>
									<button class="list-item-copy-btn" data-name="${Utils.esc(agent.name)}" tabindex="-1" title="複写">
										${Constants.ICON_COPY}
									</button>
									<button class="list-item-delete-btn" data-name="${Utils.esc(agent.name)}" tabindex="-1" title="削除" ${agent.inConversation ? "disabled" : ""}>
										${Constants.ICON_TRASH}
									</button>
								`;
								agentListEl.appendChild(agentItemEl);
							});

							/*
							 * 指定エージェント自動選択(未指定の場合はLEADERを選択)
							 */
							selectName = selectName === undefined ? (this.#agents.find((agent) => agent.leader)?.name ?? null) : selectName;
							if (selectName) {
								const selectAgent = this.#agents.find((agent) => agent.name === selectName);
								if (selectAgent) {
									this.#onClickAgent(null, selectAgent);
								}
							}
						}
					},
					onError: () => {
						WebUI.showError("エージェントリストの取得に失敗しました。");
					},
				},
			);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 新規追加プレースホルダーをリスト先頭へ挿入します。<br>
	 * @param {boolean} [isCopy=false] - 複写モードの場合にtrueを指定
	 */
	#insertNewPlaceholder(isCopy = false) {
		try {
			/*
			 * 既存のプレースホルダー削除
			 */
			const agentListEl = document.getElementById("agents-list");
			document.getElementById("agent-new-placeholder")?.remove();
			agentListEl.querySelector(".empty-msg")?.remove();

			/*
			 * 新規追加プレースホルダー挿入
			 */
			const newItemEl = document.createElement("div");
			newItemEl.id = "agent-new-placeholder";
			newItemEl.className = "list-item new-item";
			newItemEl.innerHTML = `
				<div class="list-item-content">
					<div class="list-item-ali-info">
						<span class="list-item-ali-label">${isCopy ? "Copy" : "New"}</span>
						<span class="list-item-ali-caption">${isCopy ? "複写作成" : "新規作成"}</span>
					</div>
					<div class="list-item-ali-meta">
						<span>${isCopy ? "複写作成中です" : "新規作成中です"}</span>
					</div>
				</div>
			`;
			agentListEl.prepend(newItemEl);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * エージェント一覧パネルクリック時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	#onListPanelClick(event) {
		try {
			/*
			 * 複写ボタン要素クリック時
			 */
			const copyBtn = event.target.closest(".list-item-copy-btn");
			if (copyBtn?.dataset.name) {
				const agent = this.#agents.find((agent) => agent.name === copyBtn.dataset.name);
				if (agent) {
					this.#onClickCopy(agent);
				}
				return;
			}

			/*
			 * 削除ボタン要素クリック時
			 */
			const deleteBtn = event.target.closest(".list-item-delete-btn");
			if (deleteBtn?.dataset.name) {
				const agent = this.#agents.find((agent) => agent.name === deleteBtn.dataset.name);
				if (agent) {
					this.#onClickDelete(agent);
				}
				return;
			}

			/*
			 * エージェントリストアイテムクリック時
			 */
			const agentListItem = event.target.closest(".list-item");
			if (!agentListItem || !agentListItem.dataset.name) {
				return;
			}
			const agent = this.#agents.find((agent) => agent.name === agentListItem.dataset.name);
			if (!agent) {
				return;
			}
			this.#onClickAgent(event, agent);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * エージェントリストアイテム選択時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 * @param {Object} agent - エージェント情報
	 */
	#onClickAgent(event, agent) {
		try {
			/*
			 * プレースホルダー削除
			 */
			document.getElementById("agent-new-placeholder")?.remove();

			/*
			 * 選択状態切り替え
			 */
			document.querySelectorAll(".list-item").forEach((element) => {
				if (element.dataset.name === agent.name) {
					element.classList.add("selected");
					this.#showForm(agent);
				} else {
					element.classList.remove("selected");
				}
			});
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 新規作成ボタンクリック時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	async #onClickNew(event) {
		try {
			/*
			 * 既存選択状態解除
			 */
			this.#copyAgent = null;
			document.querySelectorAll(".list-item").forEach((agentItemEl) => {
				agentItemEl.classList.remove("selected");
			});

			/*
			 * 新規追加プレースホルダー挿入
			 */
			this.#insertNewPlaceholder();

			/*
			 * フォーム表示
			 */
			this.#showForm(null);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 複写ボタンクリック時の処理を実行します。<br>
	 * @param {Object} agent - 複写元エージェント情報
	 */
	#onClickCopy(agent) {
		try {
			/*
			 * 既存選択状態解除
			 */
			document.querySelectorAll(".list-item").forEach((agentItemEl) => {
				agentItemEl.classList.remove("selected");
			});

			/*
			 * 新規追加プレースホルダー挿入
			 */
			this.#insertNewPlaceholder(true);

			/*
			 * フォーム表示(複写元エージェント情報設定)
			 */
			this.#copyAgent = agent;
			this.#showForm(null);

			/*
			 * 複写元の内容をフォームに適用
			 */
			document.getElementById("field-name").value = agent.name;
			document.getElementById("field-type").value = agent.type || Constants.DEFAULT_AGENT;
			document.getElementById("field-model").value = agent.model || "";
			document.getElementById("field-extra-args").value = agent.extraArgs || "";
			document.getElementById("field-leader").checked = !!agent.leader;
			document.getElementById("field-role").value = agent.role || "";
			document.getElementById("field-personality").value = agent.personality || "";
			document.getElementById("field-session-id").value = ""; // セッションIDは複写しない
			document.getElementById("field-name").select();
			document.getElementById("field-name").focus();
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 保存ボタン押下時処理を行います。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	async #onClickSave(event) {
		try {
			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAsync(
				WebAPI.saveAgent({
					project: this.#currentProject, // プロジェクト名
					originalName: this.#editAgent?.name ?? "", // 変更前エージェント名(新規時は空)
					isNew: this.#editAgent === null, // 新規作成フラグ
					name: document.getElementById("field-name").value.trim(), // エージェント名
					type: document.getElementById("field-type").value, // エージェントタイプ
					model: document.getElementById("field-model").value.trim(), // モデル名
					extraArgs: document.getElementById("field-extra-args").value.trim(), // 追加引数
					leader: document.getElementById("field-leader").checked, // リーダーフラグ
					role: document.getElementById("field-role").value.trim(), // 役割名
					personality: document.getElementById("field-personality").value, // 性質
				}),
				{
					cancellable: false,
					onDone: async (result) => {
						/*
						 * エージェントリスト再読み込み
						 */
						await this.#loadAgentList(result?.name);
					},
					onError: (message) => {
						/*
						 * エラー表示
						 */
						WebUI.showError(message || "エージェントの保存に失敗しました。");
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

	/**
	 * 削除ボタン押下時処理を行います。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	async #onClickDelete(agent) {
		try {
			/*
			 * 確認ダイアログ
			 */
			if (!(await WebUI.confirm(`「${agent.name}」を削除しますか？`))) {
				return;
			}

			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAsync(
				WebAPI.deleteAgent({
					project: this.#currentProject, // プロジェクト名
					name: agent.name, // 削除対象エージェント名
				}),
				{
					cancellable: false,
					onDone: async (result) => {
						/*
						 * 編集中=削除対象の場合はフォーム非表示
						 */
						if (this.#editAgent?.name === agent.name) {
							this.#hideForm();
						}

						/*
						 * エージェントリスト再読み込み
						 */
						await this.#loadAgentList(null);
					},
					onError: (message) => {
						/*
						 * エラー表示
						 */
						WebUI.showError(message || "エージェントの削除に失敗しました。");
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
