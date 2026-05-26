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
	/** サーバー上のカレントプロジェクト名 */
	#currentProject;

	/** プロジェクト一覧 */
	#projects;

	/** 編集中プロジェクト名(null=新規作成モード) */
	#editingName;

	/** 複写元プロジェクト名(null=複写でない) */
	#copyFromProject;

	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
			this.#currentProject = null;
			this.#projects = [];
			this.#editingName = null;
			this.#copyFromProject = null;
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
			 * プロジェクト情報取得
			 */
			await this.#loadCurrentProject();
			await this.#loadProjectList();

			/*
			 * イベントハンドラ登録
			 */
			document.getElementById("btn-new").onclick = (event) => this.#onClickNew(event);
			document.getElementById("btn-save-project").onclick = (event) => this.#onClickSave(event);
			document.getElementById("btn-select-project").onclick = (event) => this.#onClickSelect(event);
			document.getElementById("project-list").onclick = (event) => this.#onListPanelClick(event);
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * プロジェクト設定フォームパネルを表示します。<br>
	 * @param {string|null} name - プロジェクト名(null=新規作成モード)
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
			document.getElementById("project-detail-empty").style.display = "none";
			document.getElementById("project-form-header").style.display = "";
			document.getElementById("project-detail-panel").style.display = "";

			/*
			 * フォーム要素設定
			 */
			if (this.#editingName) {
				const project = this.#projects.find((project) => project.name === this.#editingName) ?? null;
				document.getElementById("btn-select-project").style.display = this.#editingName === this.#currentProject ? "none" : "";
				document.getElementById("form-row-agent-template").style.display = "none";
				document.getElementById("project-title-input").value = project?.title || "";
				document.getElementById("project-detail-name").style.display = "none";
				document.getElementById("project-name-input").value = this.#editingName;
				document.getElementById("project-name-input").style.display = "";
				document.getElementById("project-title-input").focus();

				/*
				 * 統計情報表示(常時表示・会話なし時はデフォルト値)
				 */
				document.getElementById("form-row-stat-tokens").style.display = "";
				document.getElementById("form-row-stat-turns").style.display = "";
				document.getElementById("form-row-stat-duration").style.display = "";
				const turnCount = project?.turnCount ?? 0;
				document.getElementById("project-stat-duration").textContent = Utils.formatDurationSec(project?.durationSec ?? 0);
				document.getElementById("project-stat-turns").textContent = `${turnCount} ターン`;
				document.getElementById("project-stat-tokens").textContent = (project?.totalTokens ?? 0).toLocaleString() + " トークン";

				/*
				 * エージェント別トークン内訳
				 */
				const agentTokens = project?.agentTokens ?? [];
				document.getElementById("form-section-agent-tokens").style.display = "";
				const agentRowsEl = document.getElementById("project-stat-agent-tokens-rows");
				agentRowsEl.innerHTML = agentTokens.length > 0
					? agentTokens.map((agent) => {
						const parts = [agent.type, agent.model].filter(Boolean).join(" ");
						const label = parts ? `${Utils.esc(agent.name)} （${Utils.esc(parts)}）` : Utils.esc(agent.name);
						return `
						<div class="form-row">
							<label class="form-label">${label}</label>
							<span class="input-field-readonly">${Number(agent.tokens).toLocaleString()} トークン</span>
						</div>`;
					}).join("")
					: `<div class="form-row"><span class="input-field-readonly">（なし）</span></div>`;
			} else {
				document.getElementById("btn-select-project").style.display = "none";
				document.getElementById("project-title-input").value = "";
				document.getElementById("project-detail-name").textContent = "";
				document.getElementById("project-detail-name").style.display = "none";
				document.getElementById("project-name-input").value = "";
				document.getElementById("project-name-input").style.display = "";
				document.getElementById("form-row-stat-duration").style.display = "";
				document.getElementById("form-row-stat-turns").style.display = "";
				document.getElementById("form-row-stat-tokens").style.display = "";
				document.getElementById("form-section-agent-tokens").style.display = "";
				document.getElementById("project-stat-duration").textContent = "0分0秒";
				document.getElementById("project-stat-turns").textContent = "0 ターン";
				document.getElementById("project-stat-tokens").textContent = "0 トークン";
				document.getElementById("project-stat-agent-tokens-rows").innerHTML = `<div class="form-row"><span class="input-field-readonly">（なし）</span></div>`;
				/*
				 * エージェントテンプレート選択行は新規作成時のみ表示(複写時は非表示)
				 */
				const showTemplate = !this.#copyFromProject;
				document.getElementById("form-row-agent-template").style.display = showTemplate ? "" : "none";
				if (showTemplate) {
					document.getElementById("project-agent-template-input").value = "";
				}
				document.getElementById("project-name-input").focus();
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * プロジェクト設定フォームパネルを非表示にします。<br>
	 */
	#hideForm() {
		try {
			/*
			 * パネル切り替え
			 */
			document.getElementById("project-detail-empty").style.display = "";
			document.getElementById("project-form-header").style.display = "none";
			document.getElementById("project-detail-panel").style.display = "none";
			this.#editingName = null;
			this.#copyFromProject = null;
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * カレントプロジェクトをサーバーから取得して表示更新します。<br>
	 */
	async #loadCurrentProject() {
		try {
			/*
			 * サーバーサイド処理
			 */
			const result = await WebAPI.getCurrentProject(
				{
					_: null,
				},
				false,
			);
			if (!result) {
				return;
			}

			/*
			 * カレントプロジェクト設定
			 */
			this.#currentProject = result.data?.project || null;
		} catch (e) {
			Utils.catchFatal(e);
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
			const result = await WebAPI.getProjectList({
				_: null,
			});
			if (!result) {
				return;
			}

			/*
			 * プロジェクト一覧情報設定
			 */
			this.#projects = result.data || [];

			/*
			 * プロジェクトリスト要素取得
			 */
			const elProjectList = document.getElementById("project-list");
			elProjectList.innerHTML = "";

			/*
			 * プロジェクト一覧描画
			 */
			if (this.#projects.length === 0) {
				// プロジェクトが存在しない場合
				const elEmpty = document.createElement("div");
				elEmpty.className = "empty-msg list-item-ali-empty";
				elEmpty.textContent = "既存プロジェクトは存在しません";
				elProjectList.appendChild(elEmpty);
				this.#hideForm();
			} else {
				// プロジェクトが存在する場合
				this.#projects.forEach((project) => {
					const elProjectItem = document.createElement("div");
					elProjectItem.className = "list-item";
					elProjectItem.dataset.project = project.name;
					elProjectItem.innerHTML = `
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
								<span>${(project.totalTokens ?? 0) > 0 ? (project.totalTokens).toLocaleString() : "0"} トークン</span>
							</div>
							</div>
						<button class="list-item-copy-btn" data-project="${Utils.esc(project.name)}" tabindex="-1" title="複写">
							${Constants.ICON_COPY}
						</button>
						<button class="list-item-delete-btn" data-project="${Utils.esc(project.name)}" tabindex="-1" title="削除">
							${Constants.ICON_TRASH}
						</button>
					`;
					elProjectList.appendChild(elProjectItem);
				});

				/*
				 * 指定プロジェクトを自動選択
				 */
				if (selectName) {
					this.#onClickProject(null, selectName);
				}
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * プロジェクトリストパネルクリック時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	#onListPanelClick(event) {
		try {
			const copyBtn = event.target.closest(".list-item-copy-btn");
			if (copyBtn?.dataset.project) {
				this.#onClickCopy(copyBtn.dataset.project);
				return;
			}
			const deleteBtn = event.target.closest(".list-item-delete-btn");
			if (deleteBtn?.dataset.project) {
				this.#onClickDelete(deleteBtn.dataset.project);
				return;
			}
			const projectListItem = event.target.closest(".list-item");
			if (!projectListItem || !projectListItem.dataset.project) {
				return;
			}
			this.#onClickProject(event, projectListItem.dataset.project);
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * プロジェクトリストアイテム選択時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 * @param {string} name - 選択するプロジェクト名
	 */
	#onClickProject(event, name) {
		try {
			document.getElementById("project-new-placeholder")?.remove();
			document.querySelectorAll(".list-item").forEach((element) => {
				if (element.dataset.project === name) {
					element.classList.add("selected");
					this.#showForm(name);
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
		try {
			document.querySelectorAll(".list-item").forEach((el) => el.classList.remove("selected"));
			this.#insertNewPlaceholder();
			this.#showForm(null);

			/*
			 * デフォルト値をサーバーから取得してフォームに適用
			 */
			const result = await WebAPI.getDefaultProject({ _: null }, false);
			if (result?.data) {
				const defaultConfig = result.data;
				document.getElementById("project-name-input").value = defaultConfig.name || "";
				document.getElementById("project-title-input").value = defaultConfig.title || "";
				document.getElementById("project-name-input").focus();
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 複写ボタンクリック時の処理を実行します。<br>
	 * @param {string} name - 複写元プロジェクト名
	 */
	async #onClickCopy(name) {
		try {
			const source = this.#projects.find((p) => p.name === name);
			if (!source) {
				return;
			}
			document.querySelectorAll(".list-item").forEach((el) => el.classList.remove("selected"));
			this.#insertNewPlaceholder(true);
			this.#copyFromProject = name;
			this.#showForm(null);

			/*
			 * 複写元の内容をフォームに適用
			 */
			document.getElementById("project-name-input").value = source.name;
			document.getElementById("project-title-input").value = source.title || "";
			document.getElementById("project-name-input").select();
			document.getElementById("project-name-input").focus();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 新規作成プレースホルダーをリスト先頭へ挿入します。<br>
	 * @param {boolean} [isCopy=false] - 複写モードの場合にtrueを指定
	 */
	#insertNewPlaceholder(isCopy = false) {
		const elList = document.getElementById("project-list");
		document.getElementById("project-new-placeholder")?.remove();
		elList.querySelector(".empty-msg")?.remove();
		const el = document.createElement("div");
		el.id = "project-new-placeholder";
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
	 * 保存ボタンクリック時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	async #onClickSave(event) {
		try {
			/*
			 * 保存情報取得(入力値を優先してプロジェクト名変更に対応)
			 */
			const savedName = document.getElementById("project-name-input").value.trim();
			const originalProject = this.#editingName ?? savedName;

			/*
			 * サーバーサイド処理
			 */
			const result = await WebAPI.saveProject({
				project: savedName,
				originalProject: originalProject,
				isNew: this.#editingName === null,
				copyFromProject: this.#copyFromProject ?? "",
				agentTemplate: document.getElementById("project-agent-template-input").value,
				title: document.getElementById("project-title-input").value.trim(),
			});
			if (!result) {
				return;
			}

			/*
			 * クライアント後処理(保存対象プロジェクトを選択状態で再描画)
			 * ※loadProjectList内でclearInfoが呼ばれるため、再描画完了後にメッセージを表示する
			 */
			await this.#loadCurrentProject();
			await this.#loadProjectList(savedName);
			if (result.message) {
				Utils.showInfo(result.message);
			}
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 削除ボタンクリック時の処理を実行します。<br>
	 * @param {Event} event - イベントオブジェクト
	 */
	async #onClickDelete(name) {
		try {
			/*
			 * 確認ダイアログ
			 */
			if (!await Utils.confirm(`「${name}」を削除しますか？`)) {
				return;
			}

			/*
			 * サーバーサイド処理
			 */
			const result = await WebAPI.deleteProject({
				project: name,
			});
			if (!result) {
				return;
			}

			/*
			 * クライアント後処理(削除対象がACTIVEプロジェクトの場合はページリロード)
			 */
			if (this.#currentProject === name) {
				window.location.reload();
				return;
			}
			if (this.#editingName === name) {
				this.#hideForm();
			}
			await this.#loadProjectList(null);
		} catch (e) {
			Utils.catchFatal(e);
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
			const result = await WebAPI.selectProject({
				projectName: this.#editingName,
			});
			if (!result) {
				return;
			}

			/*
			 * クライアント後処理
			 */
			document.body.classList.add("has-project");
			await this.#loadCurrentProject();
			await this.#loadProjectList();
			this.#hideForm();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}
}
