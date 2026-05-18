/**
 * アプリケーションユーティリティクラスです。<br>
 * <p>
 * 汎用的な処理メソッドを提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/01	Kitagawa		新規作成
 *-->
 */
class Utils {
	/**
	 * スクリプトエラーをコンソールに出力して警告通知します<br>
	 * @param {Error} error - エラーオブジェクト
	 */
	static catchFatal(error) {
		alert("エラーが発生しました。\nブラウザコンソールログとサーバーログを確認してください。");
		console.error(error);
	}

	/**
	 * フォームエラーメッセージを表示します。<br>
	 * @param {string} message - エラーメッセージ
	 */
	static showError(message) {
		const elError = document.getElementById("page-message-error");
		if (elError) {
			elError.textContent = message || "";
			elError.style.display = message ? "" : "none";
		} else {
			alert(message || "");
		}
	}

	/**
	 * フォームエラーメッセージをクリアします。<br>
	 */
	static clearError() {
		Utils.showError("");
	}

	/**
	 * フォーム警告メッセージを表示します。<br>
	 * @param {string} message - 警告メッセージ
	 */
	static showWarning(message) {
		const elWarning = document.getElementById("page-message-warn");
		if (elWarning) {
			elWarning.textContent = message || "";
			elWarning.style.display = message ? "" : "none";
		}
	}

	/**
	 * フォーム警告メッセージをクリアします。<br>
	 */
	static clearWarning() {
		Utils.showWarning("");
	}

	/**
	 * フォーム情報メッセージを表示します。<br>
	 * @param {string} message - 情報メッセージ
	 */
	static showInfo(message) {
		const elInfo = document.getElementById("page-message-info");
		if (elInfo) {
			elInfo.textContent = message || "";
			elInfo.style.display = message ? "" : "none";
		}
	}

	/**
	 * フォーム情報メッセージをクリアします。<br>
	 */
	static clearInfo() {
		Utils.showInfo("");
	}

	/**
	 * ブートオーバーレイを非表示にします。<br>
	 * すべてのコントローラー初期化完了後に呼び出してください。<br>
	 */
	static hideBootOverlay() {
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
					<div class="confirm-message">${Utils.esc(message)}</div>
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
	 * 指定名称から一貫したアバター背景色を返します。<br>
	 * @param {string} name - 指定名称
	 * @returns {string} CSS色文字列
	 */
	static avatarColor(name) {
		const palette = [
			"#1a73e8", // ブルー
			"#0f9d58", // グリーン
			"#f4511e", // ディープオレンジ
			"#9c27b0", // パープル
			"#00897b", // ティール
			"#e53935", // レッド
			"#0288d1", // ライトブルー
			"#f09300", // アンバー
			"#546e7a", // ブルーグレー
			"#6d4c41", // ブラウン
			"#43a047", // ライトグリーン
			"#d81b60", // ピンク
			"#3949ab", // インディゴ
			"#00acc1", // シアン
			"#7cb342", // ライム
			"#8e24aa", // ディープパープル
			"#fb8c00", // オレンジ
			"#039be5", // スカイブルー
			"#c0392b", // ダークレッド
			"#00796b", // ダークティール
		];
		let hash = 0;
		for (let i = 0; i < name.length; i++) {
			hash = (hash * 31 + name.charCodeAt(i)) & 0xffffffff;
		}
		return palette[Math.abs(hash) % palette.length];
	}

	/**
	 * 文字列をHTMLエスケープして提供します。<br>
	 * @param {string} string - エスケープ対象文字列
	 * @returns {string} エスケープ済み文字列
	 */
	static esc(string) {
		return (string || "") //
			.replace(/&/g, "&amp;") //
			.replace(/</g, "&lt;") //
			.replace(/>/g, "&gt;") //
			.replace(/"/g, "&quot;"); //
	}
}
