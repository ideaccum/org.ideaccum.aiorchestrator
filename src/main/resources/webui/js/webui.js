/**
 * WebUIユーティリティスクリプトクラスです。<br>
 * <p>
 * 汎用的なページUI操作処理メソッドを提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者	    	更新内容
 * 2026/06/17	Kitagawa		新規作成
 *-->
 */
class WebUI {
	/**
	 * トースト通知を使用するか(false=ノーティスバー上部表示)
	 */
	static USE_TOAST = true;

	/**
	 * UI共通の初期化処理を実行します。<br>
	 * Utils.init() から呼び出されます。<br>
	 */
	static init() {
		marked.setOptions({
			breaks: true, // GitHub Flavored Markdown を使用
			gfm: true, // 単一の改行(\n)を<br>に変換
		});
		WebUI.initNumberFields();
	}

	/**
	 * ページ内の数値入力フィールドにカスタムスピンボタンを適用します。<br>
	 */
	static initNumberFields() {
		document.querySelectorAll('input[type="number"].input-field').forEach((input) => {
			/*
			 * すでにラップ済みの場合はスキップ
			 */
			if (input.closest(".number-input-wrapper")) {
				return;
			}

			/*
			 * スピンボタンラッパー生成
			 */
			const wrapperEl = document.createElement("div");
			wrapperEl.className = "number-input-wrapper";
			input.parentNode.insertBefore(wrapperEl, input);
			wrapperEl.appendChild(input);

			/*
			 * スピンボタン生成
			 */
			const spinBtns = document.createElement("div");
			spinBtns.className = "number-spin-btns";
			spinBtns.innerHTML =
				'<button type="button" class="number-spin-btn number-spin-up" tabindex="-1"></button>' + //
				'<button type="button" class="number-spin-btn number-spin-down" tabindex="-1"></button>'; //
			wrapperEl.appendChild(spinBtns);
			spinBtns.querySelector(".number-spin-up").addEventListener("click", () => {
				input.stepUp();
				input.dispatchEvent(new Event("input", { bubbles: true }));
				input.dispatchEvent(new Event("change", { bubbles: true }));
				input.focus();
			});
			spinBtns.querySelector(".number-spin-down").addEventListener("click", () => {
				input.stepDown();
				input.dispatchEvent(new Event("input", { bubbles: true }));
				input.dispatchEvent(new Event("change", { bubbles: true }));
				input.focus();
			});
		});
	}

	/**
	 * コンボボックスを初期化します。<br>
	 * フォーカス・入力・キーボード操作・外クリックのイベントハンドラを登録します。<br>
	 * @param {HTMLElement} comboboxEl - .combobox ラッパー要素
	 */
	static initCombobox(comboboxEl) {
		const comboInputEl = comboboxEl.querySelector(".combobox-input");
		const comboListEl = comboboxEl.querySelector(".combobox-list");
		const comboToggleEl = comboboxEl.querySelector(".combobox-toggle");

		/*
		 * リスト開閉ボタンイベント
		 */
		comboToggleEl.addEventListener("click", () => {
			if (comboInputEl.disabled) {
				return;
			}
			if (comboListEl.classList.contains("hidden")) {
				WebUI.#comboboxShow(comboboxEl);
			} else {
				WebUI.#comboboxHide(comboboxEl);
			}
		});

		/*
		 * フォーカスイベント
		 */
		comboInputEl.addEventListener("focus", () => {
			if (!comboInputEl.disabled) {
				WebUI.#comboboxShow(comboboxEl);
			}
		});

		/*
		 * 入力イベント
		 */
		comboInputEl.addEventListener("input", () => {
			WebUI.#comboboxFilter(comboboxEl);
		});

		/*
		 * コンボボックス外クリックイベント
		 */
		document.addEventListener("click", (event) => {
			if (!comboboxEl.contains(event.target)) {
				WebUI.#comboboxHide(comboboxEl);
			}
		});

		/*
		 * キーボード操作イベント
		 */
		comboInputEl.addEventListener("keydown", (event) => {
			const items = Array.from(comboListEl.querySelectorAll("li:not(.hidden)"));
			const index = items.findIndex((li) => li.classList.contains("combobox-active"));
			if (event.key === "ArrowDown") {
				event.preventDefault();
				WebUI.#comboboxShow(comboboxEl);
				const next = index < items.length - 1 ? index + 1 : 0;
				items.forEach((li) => li.classList.remove("combobox-active"));
				if (items[next]) {
					items[next].classList.add("combobox-active");
					items[next].scrollIntoView({ block: "nearest" });
				}
			} else if (event.key === "ArrowUp") {
				event.preventDefault();
				WebUI.#comboboxShow(comboboxEl);
				const prev = index > 0 ? index - 1 : items.length - 1;
				items.forEach((li) => li.classList.remove("combobox-active"));
				if (items[prev]) {
					items[prev].classList.add("combobox-active");
					items[prev].scrollIntoView({ block: "nearest" });
				}
			} else if (event.key === "Enter") {
				const activeItem = comboListEl.querySelector("li.combobox-active");
				if (activeItem && !comboListEl.classList.contains("hidden")) {
					event.preventDefault();
					comboInputEl.value = activeItem.dataset.value;
					WebUI.#comboboxHide(comboboxEl);
				}
			} else if (event.key === "Escape") {
				WebUI.#comboboxHide(comboboxEl);
			}
		});
	}

	/**
	 * コンボボックスの候補リストを更新します。<br>
	 * @param {HTMLElement} comboboxEl - .combobox ラッパー要素
	 * @param {string[]} options - 候補文字列の配列
	 */
	static updateComboboxOptions(comboboxEl, options) {
		const inputEl = comboboxEl.querySelector(".combobox-input");
		const listEl = comboboxEl.querySelector(".combobox-list");
		listEl.innerHTML = "";
		(options || []).forEach((opt) => {
			const li = document.createElement("li");
			li.dataset.value = opt;
			li.textContent = opt;
			li.addEventListener("click", () => {
				inputEl.value = opt;
				WebUI.#comboboxHide(comboboxEl);
				inputEl.focus();
			});
			listEl.appendChild(li);
		});
	}

	/**
	 * コンボボックスの候補リストを表示します。<br>
	 */
	static #comboboxShow(comboboxEl) {
		const listEl = comboboxEl.querySelector(".combobox-list");
		listEl.querySelectorAll("li").forEach((li) => li.classList.remove("hidden", "combobox-active"));
		listEl.classList.remove("hidden");
	}

	/**
	 * コンボボックスの候補リストを非表示にします。<br>
	 */
	static #comboboxHide(comboboxEl) {
		const listEl = comboboxEl.querySelector(".combobox-list");
		listEl.classList.add("hidden");
		listEl.querySelectorAll("li.combobox-active").forEach((li) => li.classList.remove("combobox-active"));
	}

	/**
	 * 入力テキストでコンボボックスの候補リストをフィルタします。<br>
	 */
	static #comboboxFilter(comboboxEl) {
		const inputEl = comboboxEl.querySelector(".combobox-input");
		const listEl = comboboxEl.querySelector(".combobox-list");
		const filter = inputEl.value.toLowerCase();
		let hasVisible = false;
		listEl.querySelectorAll("li").forEach((li) => {
			const match = !filter || li.dataset.value.toLowerCase().includes(filter);
			li.classList.toggle("hidden", !match);
			if (match) {
				hasVisible = true;
			}
		});
		listEl.querySelectorAll("li.combobox-active").forEach((li) => li.classList.remove("combobox-active"));
		listEl.classList.toggle("hidden", !hasVisible);
	}

	/**
	 * スクリプトエラーをコンソールに出力して警告通知します。<br>
	 * @param {Error} error - エラーオブジェクト
	 */
	static catchFatal(error) {
		alert("エラーが発生しました。\nブラウザコンソールログとサーバーログを確認してください。");
		console.error(error);
	}

	/**
	 * ノーティスバーにメッセージを表示します。<br>
	 * @param {string} message - メッセージ
	 * @param {"error"|"warn"|"info"} type - 通知種別
	 */
	static #showNoticebar(message, type) {
		const idMap = {
			error: "page-message-error",
			warn: "page-message-warn",
			info: "page-message-info",
		};
		const el = document.getElementById(idMap[type]);
		if (el) {
			el.textContent = message || "";
			el.classList.toggle("hidden", !message);
		}
	}

	/**
	 * ノーティスバーのメッセージを非表示にします。<br>
	 * @param {"error"|"warn"|"info"} type - 通知種別
	 */
	static #hideNoticebar(type) {
		const idMap = {
			error: "page-message-error",
			warn: "page-message-warn",
			info: "page-message-info",
		};
		const el = document.getElementById(idMap[type]);
		if (el) {
			el.textContent = "";
			el.classList.add("hidden");
		}
	}

	/**
	 * トースト通知を表示します。<br>
	 * @param {string} message - メッセージ
	 * @param {"error"|"warn"|"info"} type - 通知種別
	 * @param {number} duration - 自動消去ミリ秒(0=消去しない)
	 */
	static #showToast(message, type, duration) {
		if (!message) {
			return;
		}
		const icons = {
			error: '<span class="icon icon-error"></span>',
			warn: '<span class="icon icon-warn"></span>',
			info: '<span class="icon icon-info"></span>',
		};
		const container = WebUI.#getToastContainer();
		const toast = document.createElement("div");
		toast.className = `toast toast-${type}`;
		toast.innerHTML = `
			<div class="toast-icon">${icons[type]}</div>
			<div class="toast-body">${Utils.esc(message).replace(/\n/g, "<br>")}</div>
			<button class="toast-close" aria-label="閉じる">✕</button>
		`;
		if (duration > 0) {
			toast.dataset.duration = duration;
			toast.style.setProperty("--toast-duration", duration + "ms");
		}
		const dismiss = () => {
			toast.style.transition = "opacity 0.5s, transform 0.5s";
			toast.style.opacity = "0";
			toast.style.transform = "translateX(1rem)";
			setTimeout(() => toast.remove(), 500);
		};
		toast.querySelector(".toast-close").onclick = dismiss;
		if (duration > 0) {
			setTimeout(dismiss, duration);
		}
		container.appendChild(toast);
	}

	/**
	 * 指定タイプのトーストをすべて非表示にします。<br>
	 * @param {"error"|"warn"|"info"} type - 消去対象の通知種別
	 */
	static #hideToast(type) {
		const container = document.getElementById("toast-container");
		if (!container) {
			return;
		}
		container.querySelectorAll(`.toast-${type}`).forEach((toast) => {
			setTimeout(() => {
				toast.style.transition = "opacity 0.5s, transform 0.5s";
				toast.style.opacity = "0";
				toast.style.transform = "translateX(1rem)";
				setTimeout(() => toast.remove(), 500);
			}, 300);
		});
	}

	/**
	 * トースト通知コンテナを取得または生成します。<br>
	 * @returns {HTMLElement}
	 */
	static #getToastContainer() {
		let el = document.getElementById("toast-container");
		if (!el) {
			el = document.createElement("div");
			el.id = "toast-container";
			el.className = "toast-container";
			document.body.appendChild(el);
		}
		return el;
	}

	/**
	 * エラーメッセージを表示します。<br>
	 * @param {string} message - エラーメッセージ
	 */
	static showError(message) {
		if (WebUI.USE_TOAST) {
			WebUI.#showToast(message, "error", 8000);
		} else {
			WebUI.#showNoticebar(message, "error");
		}
	}

	/**
	 * エラーメッセージをクリアします。<br>
	 */
	static clearError() {
		if (WebUI.USE_TOAST) {
			WebUI.#hideToast("error");
		} else {
			WebUI.#hideNoticebar("error");
		}
	}

	/**
	 * 警告メッセージを表示します。<br>
	 * @param {string} message - 警告メッセージ
	 */
	static showWarning(message) {
		if (WebUI.USE_TOAST) {
			WebUI.#showToast(message, "warn", 8000);
		} else {
			WebUI.#showNoticebar(message, "warn");
		}
	}

	/**
	 * 警告メッセージをクリアします。<br>
	 */
	static clearWarning() {
		if (WebUI.USE_TOAST) {
			WebUI.#hideToast("warn");
		} else {
			WebUI.#hideNoticebar("warn");
		}
	}

	/**
	 * 情報メッセージを表示します。<br>
	 * @param {string} message - 情報メッセージ
	 */
	static showInfo(message) {
		if (WebUI.USE_TOAST) {
			WebUI.#showToast(message, "info", 5000);
		} else {
			WebUI.#showNoticebar(message, "info");
		}
	}

	/**
	 * 情報メッセージをクリアします。<br>
	 */
	static clearInfo() {
		if (WebUI.USE_TOAST) {
			WebUI.#hideToast("info");
		} else {
			WebUI.#hideNoticebar("info");
		}
	}

	/**
	 * ブートオーバーレイを非表示にします。<br>
	 * すべてのコントローラー初期化完了後に呼び出してください。<br>
	 */
	static hideOverlay() {
		const elOverlay = document.getElementById("boot-overlay");
		if (elOverlay) {
			elOverlay.classList.add("hidden");
		}
	}

	/**
	 * カスタム確認ダイアログを表示し、ユーザーの選択結果をPromiseで返します。<br>
	 * @param {string} message - 確認メッセージ
	 * @param {string} [confirmLabel="OK"] - 実行ボタンのラベル
	 * @param {string} [cancelLabel="キャンセル"] - キャンセルボタンのラベル
	 * @returns {Promise<boolean>} 実行ボタンが押された場合true、キャンセルの場合false
	 */
	static confirm(message, confirmLabel = "OK", cancelLabel = "キャンセル") {
		return new Promise((resolve) => {
			const backdrop = document.createElement("div");
			backdrop.className = "confirm-backdrop";
			backdrop.innerHTML = `
				<div class="confirm-dialog">
					<div class="confirm-message">${Utils.esc(message).replace(/\n/g, "<br>")}</div>
					<div class="confirm-actions btn-row">
						<button class="btn confirm-ok-btn">${Utils.esc(confirmLabel)}</button>
						<button class="btn confirm-cancel-btn">${Utils.esc(cancelLabel)}</button>
					</div>
				</div>
			`;
			document.body.appendChild(backdrop);

			const cleanup = (result) => {
				document.removeEventListener("keydown", onKeyDown);
				backdrop.remove();
				resolve(result);
			};

			const onKeyDown = (event) => {
				if (event.key === "Escape") {
					cleanup(false);
				}
				if (event.key === "Enter") {
					cleanup(true);
				}
				if (event.key === " ") {
					cleanup(true);
				}
			};

			backdrop.querySelector(".confirm-ok-btn").addEventListener("click", () => cleanup(true));
			backdrop.querySelector(".confirm-cancel-btn").addEventListener("click", () => cleanup(false));
			backdrop.addEventListener("click", (event) => {
				if (event.target === backdrop) {
					cleanup(false);
				}
			});
			document.addEventListener("keydown", onKeyDown);
			backdrop.querySelector(".confirm-cancel-btn").focus();
		});
	}

	/**
	 * 指定要素を最下部へスクロールします。<br>
	 * @param {HTMLElement} element - スクロール対象要素
	 */
	static scrollBottom(element) {
		element.scrollTop = element.scrollHeight;
	}

	/**
	 * 指定要素のスクロール位置が最下部付近かを判定します。<br>
	 * @param {HTMLElement} element - 判定対象要素
	 * @param {number} [threshold=80] - 最下部とみなす余白(px)
	 * @returns {boolean} 最下部付近の場合にtrue
	 */
	static isNearBottom(element, threshold = 80) {
		return element.scrollHeight - element.scrollTop - element.clientHeight < threshold;
	}

	/**
	 * 指定要素が最下部付近の場合のみ最下部へスクロールします。<br>
	 * @param {HTMLElement} element - スクロール対象要素
	 * @param {number} [threshold=80] - 最下部とみなす余白(px)
	 */
	static scrollBottomIfNear(element, threshold = 80) {
		if (WebUI.isNearBottom(element, threshold)) {
			element.scrollTop = element.scrollHeight;
		}
	}

	/**
	 * クリップボードコピーボタンを生成して返却します。<br>
	 * @param {() => string} getTextFn - クリック時にコピーするテキストを返す関数
	 * @returns {HTMLButtonElement} コピーボタン要素
	 */
	static createCopyButton(getTextFn) {
		const copyBtn = document.createElement("button");
		copyBtn.className = "btn-clip-copy";
		copyBtn.type = "button";
		copyBtn.title = "クリップボードにコピー";
		copyBtn.innerHTML = Constants.ICON_COPY;
		copyBtn.addEventListener("click", (event) => {
			event.stopPropagation();
			const text = getTextFn() || "";
			navigator.clipboard.writeText(text).then(() => {
				const preview = text.replace(/[\r\n]/g, "").slice(0, 36);
				const message = "クリップボードにコピーしました" + (preview ? "\n「" + preview + "...」" : "");
				WebUI.showInfo(message);
			});
		});
		return copyBtn;
	}
}
