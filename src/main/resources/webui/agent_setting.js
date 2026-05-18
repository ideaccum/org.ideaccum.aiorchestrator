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
	/** 現在選択中のプロジェクト名 */
	#currentProject;

	/** 現在ロード済みのエージェント一覧 */
	#agents;

	/** 編集中のエージェント名(null=新規作成モード) */
	#editingName;

	/** 複写元エージェント情報(null=複写でない) */
	#copySourceAgent;

	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
			this.#currentProject = null;
			this.#agents = [];
			this.#editingName = null;
			this.#copySourceAgent = null;
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
			 * カレントプロジェクトエージェントロード
			 */
			await this.#loadCurrentProject();

			/*
			 * イベントハンドラ登録
			 */
			document.getElementById("btn-new").onclick = (event) => this.#onClickNew(event);
			document.getElementById("btn-save").onclick = (event) => this.#onClickSave(event);
			document.getElementById("agents-list").onclick = (event) => this.#onListPanelClick(event);
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * エージェント設定フォームパネルを表示します。<br>
	 * @param {string|null} name - エージェント名(null=新規作成モード)
	 */
	#showForm(name) {
		try {
			/*
			 * 編集対象設定
			 */
			this.#editingName = name ?? null;

			/*
			 * パネル切り替え
			 */
			document.getElementById("agent-form-header").style.display = "";
			document.getElementById("agent-form-panel").style.display = "flex";
			document.getElementById("agent-form-empty").style.display = "none";

			/*
			 * フォーム要素設定
			 */
			if (this.#editingName) {
				const agent = this.#agents.find((a) => a.name === this.#editingName) ?? null;
				const inConversation = !!(agent?.inConversation);
				const hasSession = !!(agent?.sessionId);
				document.getElementById("btn-save").style.display = inConversation ? "none" : "";
				document.getElementById("field-name").value = agent?.name || "";
				document.getElementById("field-name").disabled = inConversation;
				document.getElementById("field-type").value = agent?.type || Constants.DEFAULT_AGENT;
				document.getElementById("field-type").disabled = inConversation || hasSession;
				document.getElementById("field-model").value = agent?.model || "";
				document.getElementById("field-model").disabled = inConversation || hasSession;
				document.getElementById("field-extra-args").value = agent?.extraArgs || "";
				document.getElementById("field-extra-args").disabled = inConversation || hasSession;
				document.getElementById("field-leader").checked = !!agent?.leader;
				document.getElementById("field-leader").disabled = inConversation || hasSession;
				document.getElementById("field-role").value = agent?.role || "";
				document.getElementById("field-role").disabled = inConversation || hasSession;
				document.getElementById("field-personality").value = agent?.personality || "";
				document.getElementById("field-personality").disabled = inConversation || hasSession;
				document.getElementById("field-session-id").value = agent?.sessionId || "";
				if (inConversation) {
					document.getElementById("session-exists-note").textContent = "* 既に会話に参加しているため、変更することはできません";
					document.getElementById("session-exists-note").style.display = "";
				} else if (hasSession) {
					document.getElementById("session-exists-note").textContent = "* 既にセッションが存在するため、一部の項目は変更することができません";
					document.getElementById("session-exists-note").style.display = "";
				} else {
					document.getElementById("session-exists-note").style.display = "none";
				}
				document.getElementById("field-name").focus();
			} else {
					document.getElementById("btn-save").style.display = "";
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
				document.getElementById("session-exists-note").style.display = "none";
				document.getElementById("field-name").focus();
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * エージェント設定フォームパネルを非表示にします。<br>
	 */
	#hideForm() {
		try {
			document.getElementById("agent-form-header").style.display = "none";
			document.getElementById("agent-form-panel").style.display = "none";
			document.getElementById("agent-form-empty").style.display = "flex";
			this.#editingName = null;
			this.#copySourceAgent = null;
		} catch (e) {
			Utils.catchFatal(e);
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
			const result = await WebAPI.getCurrentProject({
				_: null,
			});
			if (!result) {
				return;
			}

			/*
			 * カレントプロジェクト設定
			 */
			this.#currentProject = result.data?.project || null;
			await this.#loadAgentList();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 選択中プロジェクトのエージェント一覧をサーバーから取得して表示します。<br>
	 * @param {string|null|undefined} [selectFilename=undefined] - 再描画後に選択するエージェントファイル名(undefinedでLEADER自動選択、nullで選択しない)
	 */
	async #loadAgentList(selectFilename = undefined) {
		try {
			/*
			 * サーバーサイド処理
			 */
			const result = await WebAPI.getAgentList({
				project: this.#currentProject,
			});
			this.#agents = result.data || [];

			/*
			 * エージェントリスト要素取得
			 */
			const elAgentList = document.getElementById("agents-list");
			elAgentList.innerHTML = "";

			/*
			 * エージェント一覧描画
			 */
			if (this.#agents.length === 0) {
				// エージェントが存在しない場合
				const elEmpty = document.createElement("div");
				elEmpty.className = "empty-msg list-item-ali-empty";
				elEmpty.textContent = "エージェントがありません";
				elAgentList.appendChild(elEmpty);
				this.#hideForm();
			} else {
				// エージェントが存在する場合
				this.#agents.forEach((agent) => {
					const item = document.createElement("div");
					item.className = "list-item";
					item.dataset.filename = agent.filename;
					item.innerHTML = `
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
						<button class="list-item-copy-btn" data-filename="${Utils.esc(agent.filename)}" tabindex="-1" title="複写" ${agent.inConversation ? "disabled" : ""}>
							${Constants.ICON_COPY}
						</button>
						<button class="list-item-delete-btn" data-filename="${Utils.esc(agent.filename)}" tabindex="-1" title="削除" ${agent.inConversation ? "disabled" : ""}>
							${Constants.ICON_TRASH}
						</button>
					`;
					elAgentList.appendChild(item);
				});

				/*
				 * 指定エージェントを自動選択(未指定の場合はLEADERを選択)
				 */
				const filenameToSelect = selectFilename === undefined
					? (this.#agents.find((a) => a.leader)?.filename ?? null)
					: selectFilename;
				if (filenameToSelect) {
					const agent = this.#agents.find((a) => a.filename === filenameToSelect);
					if (agent) {
						this.#onClickAgent(null, agent);
					}
				}
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * エージェント一覧パネルクリック時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	#onListPanelClick(event) {
		try {
			const copyBtn = event.target.closest(".list-item-copy-btn");
			if (copyBtn?.dataset.filename) {
				const agent = this.#agents.find((a) => a.filename === copyBtn.dataset.filename);
				if (agent) {
					this.#onClickCopy(agent);
				}
				return;
			}
			const deleteBtn = event.target.closest(".list-item-delete-btn");
			if (deleteBtn?.dataset.filename) {
				const agent = this.#agents.find((a) => a.filename === deleteBtn.dataset.filename);
				if (agent) {
					this.#onClickDelete(agent);
				}
				return;
			}
			const agentListItem = event.target.closest(".list-item");
			if (!agentListItem || !agentListItem.dataset.filename) {
				return;
			}
			const agent = this.#agents.find((agent) => agent.filename === agentListItem.dataset.filename);
			if (!agent) {
				return;
			}
			this.#onClickAgent(event, agent);
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * エージェントリストアイテム選択時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 * @param {Object} agent - エージェント情報
	 */
	#onClickAgent(event, agent) {
		try {
			document.getElementById("agent-new-placeholder")?.remove();
			document.querySelectorAll(".list-item").forEach((element) => {
				if (element.dataset.filename === agent.filename) {
					element.classList.add("selected");
					this.#showForm(agent.name);
				} else {
					element.classList.remove("selected");
				}
			});
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 新規作成ボタンクリック時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	async #onClickNew(event) {
		document.querySelectorAll(".list-item").forEach((el) => el.classList.remove("selected"));
		this.#insertNewPlaceholder();
		this.#showForm(null);
	}

	/**
	 * 複写ボタンクリック時の処理を実行します。<br>
	 * @param {Object} agent - 複写元エージェント情報
	 */
	#onClickCopy(agent) {
		try {
			document.querySelectorAll(".list-item").forEach((el) => el.classList.remove("selected"));
			this.#insertNewPlaceholder(true);
			this.#copySourceAgent = agent;
			this.#showForm(null);

			/*
			 * 複写元の内容をフォームに適用(セッションIDは複写しない)
			 */
			document.getElementById("field-name").value = agent.name;
			document.getElementById("field-type").value = agent.type || Constants.DEFAULT_AGENT;
			document.getElementById("field-model").value = agent.model || "";
			document.getElementById("field-extra-args").value = agent.extraArgs || "";
			document.getElementById("field-leader").checked = !!agent.leader;
			document.getElementById("field-role").value = agent.role || "";
			document.getElementById("field-personality").value = agent.personality || "";
			document.getElementById("field-session-id").value = "";
			document.getElementById("field-name").select();
			document.getElementById("field-name").focus();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 新規追加プレースホルダーをリスト先頭へ挿入します。<br>
	 * @param {boolean} [isCopy=false] - 複写モードの場合にtrueを指定
	 */
	#insertNewPlaceholder(isCopy = false) {
		const elList = document.getElementById("agents-list");
		document.getElementById("agent-new-placeholder")?.remove();
		elList.querySelector(".empty-msg")?.remove();
		const el = document.createElement("div");
		el.id = "agent-new-placeholder";
		el.className = "list-item new-item";
		el.innerHTML = `
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
		elList.prepend(el);
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
			const result = await WebAPI.saveAgent({
				project: this.#currentProject,
				originalName: this.#editingName ?? "",
				isNew: this.#editingName === null,
				name: document.getElementById("field-name").value.trim(),
				type: document.getElementById("field-type").value,
				model: document.getElementById("field-model").value.trim(),
				extraArgs: document.getElementById("field-extra-args").value.trim(),
				leader: document.getElementById("field-leader").checked,
				role: document.getElementById("field-role").value.trim(),
				personality: document.getElementById("field-personality").value,
			});
			if (!result) {
				return;
			}

			/*
			 * クライアント後処理(保存対象エージェントを選択状態で再描画)
			 * ※loadAgentList内でclearInfoが呼ばれるため、再描画完了後にメッセージを表示する
			 */
			const savedFilename = result.data?.filename;
			await this.#loadAgentList(savedFilename);
		} catch (e) {
			Utils.catchFatal(e);
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
			if (!await Utils.confirm(`「${agent.name}」を削除しますか？`)) {
				return;
			}

			/*
			 * サーバーサイド処理
			 */
			const result = await WebAPI.deleteAgent({
				project: this.#currentProject,
				name: agent.name,
			});
			if (!result) {
				return;
			}

			/*
			 * クライアント後処理(削除対象が編集中の場合はフォームを閉じる)
			 */
			if (this.#editingName === agent.name) {
				this.#hideForm();
			}
			await this.#loadAgentList(null);
		} catch (e) {
			Utils.catchFatal(e);
		}
	}
}
