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
			if (input.closest(".number-input-wrapper")) {
				return;
			}
			const wrapper = document.createElement("div");
			wrapper.className = "number-input-wrapper";
			input.parentNode.insertBefore(wrapper, input);
			wrapper.appendChild(input);

			const btns = document.createElement("div");
			btns.className = "number-spin-btns";
			btns.innerHTML =
				'<button type="button" class="number-spin-btn number-spin-up" tabindex="-1"></button>' + //
				'<button type="button" class="number-spin-btn number-spin-down" tabindex="-1"></button>'; //
			wrapper.appendChild(btns);
			btns.querySelector(".number-spin-up").addEventListener("click", () => {
				input.stepUp();
				input.dispatchEvent(new Event("input", { bubbles: true }));
				input.dispatchEvent(new Event("change", { bubbles: true }));
				input.focus();
			});
			btns.querySelector(".number-spin-down").addEventListener("click", () => {
				input.stepDown();
				input.dispatchEvent(new Event("input", { bubbles: true }));
				input.dispatchEvent(new Event("change", { bubbles: true }));
				input.focus();
			});
		});
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
}
