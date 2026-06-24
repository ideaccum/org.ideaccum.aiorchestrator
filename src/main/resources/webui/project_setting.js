/**
 * プロジェクト選択ページコントローラースクリプトクラスです。<br>
 * <p>
 * プロジェクトの一覧表示・選択・新規作成およびタスクプロンプトの設定を提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/01	Kitagawa		新規作成
 *-->
 */
class ProjectSettingController {
	/** カレントプロジェクト名 */
	#currentProject;

	/** プロジェクト一覧 */
	#projects;

	/** 編集中プロジェクト情報(null=新規作成モード) */
	#editProject;

	/** 複写元プロジェクト情報(null=複写でない) */
	#copyProject;

	/** プロジェクト実行中フラグ */
	#projectRunning;

	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
			this.#currentProject = null;
			this.#projects = [];
			this.#editProject = null;
			this.#copyProject = null;
			this.#projectRunning = false;
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
			 * プロジェクト実行状態の初期取得
			 */
			await this.#initRunningStatus();

			/*
			 * SSEイベントハンドラ登録
			 */
			document.addEventListener(Constants.SSE_EVENT_NAME, (event) => this.#handleEvent(event.detail));

			/*
			 * カレントプロジェクトエージェント取得
			 */
			await this.#loadCurrentProject();

			/*
			 * プロジェクト一覧取得
			 */
			await this.#loadProjectList();

			/*
			 * イベントハンドラ登録
			 */
			document.getElementById("btn-new").onclick = (event) => this.#onClickNew(event);
			document.getElementById("btn-save-project").onclick = (event) => this.#onClickSave(event);
			document.getElementById("btn-select-project").onclick = (event) => this.#onClickSelect(event);
			document.getElementById("btn-clear-session").onclick = (event) => this.#onClickClearSession(event);
			document.getElementById("btn-download-logs").onclick = (event) => this.#onClickDownloadLogs(event);
			document.getElementById("project-external-enabled").onchange = (event) => this.#onChangeExternalEnabled(event);
			document.getElementById("project-list").onclick = (event) => this.#onListPanelClick(event);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * プロジェクト設定フォームパネルを表示します。<br>
	 * @param {Object|null} project - プロジェクト情報(null=新規作成モード)
	 */
	#showForm(project) {
		try {
			/*
			 * 編集対象設定
			 */
			this.#editProject = project ?? null;

			/*
			 * パネル切り替え
			 */
			document.getElementById("project-detail-empty").classList.add("hidden");
			document.getElementById("project-form-header").classList.remove("hidden");
			document.getElementById("project-detail-panel").classList.remove("hidden");

			/*
			 * フォーム要素設定
			 */
			if (this.#editProject) {
				/*
				 * モード時エージェント情報設定
				 */
				const externalEnabled = this.#editProject.externalEnabled ?? false;
				const turnCount = this.#editProject.turnCount ?? 0;
				document.querySelectorAll(".project-edit-section").forEach((el) => el.classList.remove("hidden"));
				document.querySelectorAll(".project-template-section").forEach((el) => el.classList.add("hidden"));
				document.getElementById("btn-select-project").classList.toggle("hidden", this.#editProject.name === this.#currentProject);
				document.getElementById("btn-select-project").disabled = this.#projectRunning;
				document.getElementById("btn-clear-session").disabled = this.#projectRunning && this.#editProject.name === this.#currentProject;
				document.getElementById("btn-download-logs").disabled = this.#projectRunning && this.#editProject.name === this.#currentProject;
				document.getElementById("project-name-input").value = this.#editProject.name;
				document.getElementById("project-title-input").value = this.#editProject.title || "";
				document.getElementById("project-title-input").focus();
				document.getElementById("project-external-enabled").checked = externalEnabled;
				document.getElementById("project-external-path-input").value = this.#editProject.externalPath || "";
				document.getElementById("form-row-external-path").classList.toggle("hidden", !externalEnabled);
				document.getElementById("project-stat-duration").textContent = Utils.formatDurationSec(this.#editProject.durationSec ?? 0);
				document.getElementById("project-stat-turns").textContent = `${turnCount} ターン`;
				document.getElementById("project-stat-tokens").textContent = (this.#editProject.totalTokens ?? 0).toLocaleString() + " トークン";

				/*
				 * エージェント別トークン内訳要素構築
				 */
				const agentTokens = this.#editProject.agentTokens ?? [];
				const agentRowsEl = document.getElementById("project-stat-agent-tokens-rows");
				agentRowsEl.innerHTML =
					agentTokens.length > 0
						? agentTokens
								.map((agent) => {
									const parts = [agent.type, agent.model].filter(Boolean).join(" ");
									const label = parts ? `${Utils.esc(agent.name)} （${Utils.esc(parts)}）` : Utils.esc(agent.name);
									return `
						<div class="form-row">
							<label class="form-label">${label}</label>
							<span class="input-field-readonly">${Number(agent.tokens).toLocaleString()} トークン</span>
						</div>`;
								})
								.join("")
						: `<div class="form-row"><span class="input-field-readonly">（なし）</span></div>`;

				/*
				 * 処理中のアクティブプロジェクトはフォームを読み取り専用にする
				 */
				const isActiveLocked = this.#projectRunning && this.#editProject.name === this.#currentProject;
				document.getElementById("btn-save-project").disabled = isActiveLocked;
				document.getElementById("project-name-input").disabled = isActiveLocked;
				document.getElementById("project-title-input").disabled = isActiveLocked;
				document.getElementById("project-external-enabled").disabled = isActiveLocked;
				document.getElementById("project-external-path-input").disabled = isActiveLocked;
			} else {
				/*
				 * 新規/複写モード時初期値設定
				 */
				const showTemplate = !this.#copyProject;
				document.querySelectorAll(".project-edit-section").forEach((el) => el.classList.add("hidden"));
				document.querySelectorAll(".project-template-section").forEach((el) => el.classList.toggle("hidden", !showTemplate));
				document.getElementById("btn-select-project").classList.add("hidden");
				document.getElementById("project-name-input").value = "";
				document.getElementById("project-title-input").value = "";
				document.getElementById("project-external-enabled").checked = false;
				document.getElementById("project-external-path-input").value = "";
				document.getElementById("form-row-external-path").classList.add("hidden");
				if (showTemplate) {
					document.getElementById("project-agent-template-input").value = "";
				}
				document.getElementById("project-name-input").focus();
			}
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * プロジェクト設定フォームパネルを非表示にします。<br>
	 */
	#hideForm() {
		try {
			document.getElementById("project-detail-empty").classList.remove("hidden");
			document.getElementById("project-form-header").classList.add("hidden");
			document.getElementById("project-detail-panel").classList.add("hidden");
			this.#editProject = null;
			this.#copyProject = null;
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
					// プロジェクト画面ではカレントプロジェクトが未選択の状態があるためエラーとしない
					//WebUI.showError("カレントプロジェクト情報の取得に失敗しました。");
				},
			});
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * プロジェクト一覧をサーバーから取得して表示更新します。<br>
	 * @param {string|null} [selectName=this.#currentProject] - 一覧再描画後に選択するプロジェクト名(nullで選択しない)
	 */
	async #loadProjectList(selectName = this.#currentProject) {
		try {
			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAwait(
				WebAPI.getProjectList({
					//
				}),
				{
					onDone: async (result) => {
						/*
						 * プロジェクト一覧情報設定
						 */
						this.#projects = result.data || [];

						/*
						 * プロジェクトリスト要素取得
						 */
						const projectListEl = document.getElementById("project-list");
						projectListEl.innerHTML = "";

						/*
						 * プロジェクト一覧描画
						 */
						if (this.#projects.length === 0) {
							// プロジェクトが存在しない場合
							const emptyProjectEl = document.createElement("div");
							emptyProjectEl.className = "empty-msg list-item-ali-empty";
							emptyProjectEl.textContent = "プロジェクトがありません";
							projectListEl.appendChild(emptyProjectEl);
							this.#hideForm();
						} else {
							// プロジェクトが存在する場合
							this.#projects.forEach((project) => {
								const projectItemEl = document.createElement("div");
								projectItemEl.className = "list-item";
								projectItemEl.dataset.project = project.name;
								projectItemEl.innerHTML = `
									<div class="list-item-icon">${Constants.ICON_PROJECT}</div>
									<div class="list-item-content">
										<div class="list-item-ali-info">
											<span class="list-item-ali-label">${Utils.esc(project.name)}</span>
											<span class="list-item-ali-caption">${Utils.esc(project.title || "")}</span>
											${project.name === this.#currentProject ? '<span class="tag">ACTIVE</span>' : ""}
										</div>
										<div class="list-item-ali-meta">
											<span>${Utils.formatDurationSec(project.durationSec ?? 0)}</span>・
											<span>${project.turnCount ?? 0} ターン</span>・
											<span>${(project.totalTokens ?? 0) > 0 ? project.totalTokens.toLocaleString() : "0"} トークン</span>
										</div>
									</div>
									<button class="list-item-copy-btn" data-project="${Utils.esc(project.name)}" tabindex="-1" title="複写" ${this.#projectRunning && project.name === this.#currentProject ? "disabled" : ""}>
										${Constants.ICON_COPY}
									</button>
									<button class="list-item-delete-btn" data-project="${Utils.esc(project.name)}" tabindex="-1" title="削除" ${this.#projectRunning && project.name === this.#currentProject ? "disabled" : ""}>
										${Constants.ICON_TRASH}
									</button>
								`;
								projectListEl.appendChild(projectItemEl);
							});

							/*
							 * 指定プロジェクト自動選択
							 */
							if (selectName) {
								const selectProject = this.#projects.find((project) => project.name === selectName) ?? null;
								if (selectProject) {
									this.#onClickProject(null, selectProject);
								}
							}
						}
					},
					onError: () => {
						WebUI.showError("プロジェクトリストの取得に失敗しました。");
					},
				},
			);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 新規作成プレースホルダーをリスト先頭へ挿入します。<br>
	 * @param {boolean} [isCopy=false] - 複写モードの場合にtrueを指定
	 */
	#insertNewPlaceholder(isCopy = false) {
		try {
			/*
			 * 既存のプレースホルダー削除
			 */
			const projectListEl = document.getElementById("project-list");
			document.getElementById("project-new-placeholder")?.remove();
			projectListEl.querySelector(".empty-msg")?.remove();

			/*
			 * 新規追加プレースホルダー挿入
			 */
			const newItemEl = document.createElement("div");
			newItemEl.id = "project-new-placeholder";
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
			projectListEl.prepend(newItemEl);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * オーケストレーター実行状態を初期取得します。<br>
	 */
	async #initRunningStatus() {
		try {
			await WebCtrl.doAwait(WebAPI.getOrchestratorStatus({}, false), {
				onDone: async (result) => {
					this.#projectRunning = !!result.data?.running;
				},
				onError: () => {},
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
			if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_STARTED) {
				this.#onSseOrchestratorStarted(data);
			} else if (data.type === Constants.SSE_TYPE_ORCHESTRATOR_DONE || data.type === Constants.SSE_TYPE_ORCHESTRATOR_STOPPED) {
				this.#onSseOrchestratorDone(data);
			}
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
			/*
			 * オーケストレーター実行状態更新
			 */
			this.#projectRunning = true;

			/*
			 * プロジェクトリスト再読み込み
			 */
			await this.#loadProjectList(this.#editProject?.name ?? null);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * SSE(orchestrator_done/stopped)イベント処理を実行します。<br>
	 * @param {object} data - SSEイベントデータ
	 */
	async #onSseOrchestratorDone(data) {
		try {
			/*
			 * オーケストレーター実行状態更新
			 */
			this.#projectRunning = false;

			/*
			 * プロジェクトリスト再読み込み
			 */
			await this.#loadProjectList(this.#editProject?.name ?? null);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * プロジェクトリストパネルクリック時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	#onListPanelClick(event) {
		try {
			/*
			 * 複写ボタン要素クリック時
			 */
			const copyBtn = event.target.closest(".list-item-copy-btn");
			if (copyBtn?.dataset.project) {
				const project = this.#projects.find((project) => project.name === copyBtn.dataset.project);
				if (project) {
					this.#onClickCopy(project);
				}
				return;
			}

			/*
			 * 削除ボタン要素クリック時
			 */
			const deleteBtn = event.target.closest(".list-item-delete-btn");
			if (deleteBtn?.dataset.project) {
				const project = this.#projects.find((project) => project.name === deleteBtn.dataset.project);
				if (project) {
					this.#onClickDelete(project);
				}
				return;
			}

			/*
			 * プロジェクトリストアイテムクリック時
			 */
			const projectListItem = event.target.closest(".list-item");
			if (!projectListItem || !projectListItem.dataset.project) {
				return;
			}
			const project = this.#projects.find((project) => project.name === projectListItem.dataset.project);
			if (!project) {
				return;
			}
			this.#onClickProject(event, project);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * プロジェクトリストアイテム選択時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 * @param {Object} project - 選択するプロジェクト情報
	 */
	#onClickProject(event, project) {
		try {
			/*
			 * プレースホルダー削除
			 */
			document.getElementById("project-new-placeholder")?.remove();

			/*
			 * 選択状態切り替え
			 */
			document.querySelectorAll(".list-item").forEach((element) => {
				if (element.dataset.project === project?.name) {
					element.classList.add("selected");
					this.#showForm(project);
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
			this.#copyProject = null;
			document.querySelectorAll(".list-item").forEach((projectItemEl) => {
				projectItemEl.classList.remove("selected");
			});

			/*
			 * 新規追加プレースホルダー挿入
			 */
			this.#insertNewPlaceholder();

			/*
			 * フォーム表示
			 */
			this.#showForm(null);

			/*
			 * デフォルト値をサーバーから取得してフォームに適用
			 */
			await WebCtrl.doAwait(WebAPI.getDefaultProject({}), {
				onDone: async (result) => {
					const defaultConfig = result.data;
					if (defaultConfig) {
						document.getElementById("project-name-input").value = defaultConfig.name || "";
						document.getElementById("project-title-input").value = defaultConfig.title || "";
						document.getElementById("project-name-input").focus();
					}
				},
				onError: () => {
					WebUI.showError("デフォルトプロジェクト情報の取得に失敗しました。");
				},
			});
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 複写ボタンクリック時の処理を実行します。<br>
	 * @param {Object} project - 複写元プロジェクト情報
	 */
	async #onClickCopy(project) {
		try {
			/*
			 * 既存選択状態解除
			 */
			document.querySelectorAll(".list-item").forEach((projectItemEl) => {
				projectItemEl.classList.remove("selected");
			});

			/*
			 * 新規追加プレースホルダー挿入
			 */
			this.#insertNewPlaceholder(true);

			/*
			 * フォーム表示(複写元プロジェクト情報設定)
			 */
			this.#copyProject = project;
			this.#showForm(null);

			/*
			 * 複写元の内容をフォームに適用
			 */
			document.getElementById("project-name-input").value = "";
			document.getElementById("project-title-input").value = "";
			document.getElementById("project-name-input").select();
			document.getElementById("project-name-input").focus();
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 保存ボタンクリック時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	async #onClickSave(event) {
		try {
			/*
			 * 保存情報取得(入力値を優先してプロジェクト名変更に対応)
			 */
			const savedName = document.getElementById("project-name-input").value.trim();
			const originalProject = this.#editProject?.name ?? savedName;

			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAsync(
				WebAPI.saveProject({
					project: savedName, // 保存対象プロジェクト名
					originalProject: originalProject, // 変更前プロジェクト名(新規時は同値)
					isNew: this.#editProject === null, // 新規作成フラグ
					copyFromProject: this.#copyProject?.name ?? "", // 複写元プロジェクト名(複写でない場合は空)
					agentTemplate: document.getElementById("project-agent-template-input").value, // エージェントテンプレートID
					title: document.getElementById("project-title-input").value.trim(), // プロジェクトタイトル
					externalEnabled: document.getElementById("project-external-enabled").checked, // 外部パス使用フラグ
					externalPath: document.getElementById("project-external-path-input").value.trim(), // 外部パス
				}),
				{
					cancellable: !!this.#copyProject,
					onDone: async (result) => {
						/*
						 * カレントプロジェクト再読み込み
						 */
						await this.#loadCurrentProject();

						/*
						 * プロジェクトリスト再読み込み
						 */
						await this.#loadProjectList(result?.savedName ?? savedName);

						/*
						 * メッセージ表示
						 */
						if (result?.message) {
							WebUI.showInfo(result.message);
						}
					},
					onError: (message) => {
						/*
						 * エラー表示
						 */
						WebUI.showError(message || "プロジェクトの保存に失敗しました。");
					},
					onCancelled: async () => {
						/*
						 * フォーム非表示
						 */
						this.#hideForm();

						/*
						 * プロジェクトリスト再読み込み(複写前の状態に戻す)
						 */
						await this.#loadProjectList(null);

						/*
						 * メッセージ表示
						 */
						WebUI.showInfo("プロジェクトの保存をキャンセルしました。");
					},
				},
			);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 削除ボタンクリック時の処理を実行します。<br>
	 * @param {Object} project - 削除するプロジェクト情報
	 */
	async #onClickDelete(project) {
		try {
			/*
			 * 確認ダイアログ
			 */
			if (!(await WebUI.confirm(`「${project.name}」を削除しますか？`))) {
				return;
			}

			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAsync(
				WebAPI.deleteProject({
					project: project.name, // 削除対象プロジェクト名
				}),
				{
					cancellable: true,
					onDone: async (result) => {
						/*
						 * 削除対象がアクティブだった場合はページリロード(カレントプロジェクトが存在しない状態になるため)
						 */
						if (result?.wasActive) {
							window.location.reload();
							return;
						}

						/*
						 * 編集中=削除対象の場合はフォーム非表示
						 */
						if (this.#editProject?.name === project.name) {
							this.#hideForm();
						}

						/*
						 * プロジェクトリスト再読み込み
						 */
						await this.#loadProjectList(null);
					},
					onError: (message) => {
						/*
						 * エラー表示
						 */
						WebUI.showError(message || "プロジェクトの削除に失敗しました。");
					},
					onCancelled: async () => {
						/*
						 * プロジェクトリスト再読み込み(削除前の状態に戻す)
						 */
						await this.#loadProjectList(this.#editProject?.name);

						/*
						 * メッセージ表示
						 */
						WebUI.showWarning("プロジェクトの削除をキャンセルしました。\n一部のファイルが残存している可能性があります。");
					},
				},
			);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 選択ボタンクリック時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	async #onClickSelect(event) {
		try {
			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAsync(
				WebAPI.selectProject({
					projectName: this.#editProject?.name, // 選択するプロジェクト名
				}),
				{
					cancellable: false,
					onDone: async () => {
						/*
						 * カレントプロジェクト情報更新
						 */
						document.body.classList.add("has-project");

						/*
						 * カレントプロジェクト再読み込み
						 */
						await this.#loadCurrentProject();

						/*
						 * プロジェクトリスト再読み込み(選択状態更新のため)
						 */
						await this.#loadProjectList();
					},
					onError: (message) => {
						WebUI.showError(message || "プロジェクトの選択に失敗しました。");
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
	 * セッションクリアボタンクリック時の処理を行います。<br>
	 * @param {Event} event - クリックイベント
	 */
	async #onClickClearSession(event) {
		try {
			/*
			 * 確認ダイアログ
			 */
			const confirmed = await WebUI.confirm(`プロジェクト「${this.#editProject?.name}」のセッション情報をクリアします。\nログ・会話・セッションが削除され、元に戻すことはできません。\nクリアしてよろしいですか？`);
			if (!confirmed) {
				return;
			}

			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAsync(
				WebAPI.clearSession({
					project: this.#editProject?.name, // セッションクリア対象プロジェクト名
				}),
				{
					cancellable: false,
					onDone: async (result) => {
						/*
						 * カレントプロジェクト再読み込み
						 */
						await this.#loadProjectList(this.#editProject?.name);

						/*
						 * メッセージ表示
						 */
						if (result?.message) {
							WebUI.showInfo(result.message);
						}
					},
					onError: (message) => {
						WebUI.showError(message || "セッションクリアに失敗しました。");
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
	 * ログダウンロードボタンクリック時の処理を行います。<br>
	 * @param {Event} event - クリックイベント
	 */
	async #onClickDownloadLogs(event) {
		try {
			/*
			 * サーバーサイド処理
			 */
			await WebCtrl.doAwait(
				WebAPI.downloadLogs({
					project: this.#editProject?.name, // ダウンロード対象プロジェクト名
				}),
				{
					onDone: async () => {
						//
					},
					onError: () => {
						WebUI.showError("ログのダウンロードに失敗しました。");
					},
				},
			);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}

	/**
	 * 外部パス使用チェックボックス変更時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	#onChangeExternalEnabled(event) {
		try {
			document.getElementById("form-row-external-path").classList.toggle("hidden", !event.target.checked);
		} catch (e) {
			WebUI.catchFatal(e);
		}
	}
}
