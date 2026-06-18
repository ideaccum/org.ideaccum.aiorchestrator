/**
 * WebUIプロセス制御クラスです。<br>
 * <p>
 * バックグラウンドプロセスの実行・ポーリング・オーバーレイなどのロジック実行管理します。<br>
 * オーバーレイ要素とタイマーをここで一元管理します。<br>
 * WebAPI呼び出し時のUIロック制御もこのクラスに集約しており、doAwait/doAsync経由で利用します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者		更新内容
 * 2026/06/16	Kitagawa	新規作成
 *-->
 */
class WebCtrl {
	/** オーバーレイ要素 */
	static #overlayEl = null;

	/** オーバーレイ遅延表示タイマー */
	static #overlayTimer = null;

	/** オーバーレイ表示カウント */
	static #overlayCount = 0;

	/**
	 * オーバーレイ要素を生成します。<br>
	 * @param {boolean} [cancellable=false] - キャンセルボタンを表示する場合にtrueを指定
	 */
	static #createOverlay(cancellable = false) {
		/*
		 * オーバーレイ要素生成
		 */
		if (!WebCtrl.#overlayEl) {
			// オーバーレイ要素
			const overlay = document.createElement("div");
			overlay.className = "api-processing-overlay";

			// スピナー要素
			const spinner = document.createElement("div");
			spinner.className = "loading-spinner";

			// DOM構築
			overlay.appendChild(spinner);
			document.body.appendChild(overlay);

			// オーバーレイ要素保持
			WebCtrl.#overlayEl = overlay;
		}

		/*
		 * キャンセルボタン生成
		 */
		if (cancellable && !WebCtrl.#overlayEl.querySelector(".overlay-cancel-btn")) {
			// キャンセルボタン要素
			const cancelBtn = document.createElement("button");
			cancelBtn.type = "button";
			cancelBtn.className = "btn overlay-cancel-btn";
			cancelBtn.textContent = "キャンセル";

			// キャンセルボタンクリックイベント
			cancelBtn.onclick = async () => {
				cancelBtn.disabled = true;
				await WebAPI.cancelProcess({});
			};

			// DOM構築
			WebCtrl.#overlayEl.appendChild(cancelBtn);
		}
	}

	/**
	 * バックグラウンドプロセスのポーリングを開始します。<br>
	 * @param {object} [options] - オプション
	 * @param {Function} [options.onDone] - 完了時コールバック(processResult を受け取る)
	 * @param {Function} [options.onCancelled] - キャンセル時コールバック
	 * @param {Function} [options.onError] - エラー時コールバック(message を受け取る)
	 * @param {boolean} [options.cancellable=true] - 中断ボタンを表示するか
	 */
	static #pollingAsyc({
		onDone, //
		onError, //
		onCancelled, //
		cancellable = true, //
	} = {}) {
		WebCtrl.lock(cancellable);
		const doPolling = async () => {
			try {
				/*
				 * サーバー処理プロセス状態取得
				 */
				const status = await WebAPI.getProcessStatus({}, false);
				if (!status) {
					WebCtrl.unlock();
					return;
				}
				const processStatus = status.data?.status;

				/*
				 * 正常完了時
				 */
				if (processStatus === "done") {
					WebCtrl.unlock();
					if (onDone) {
						await onDone(status.data?.result);
					}
					return;
				}

				/*
				 * キャンセル時
				 */
				if (processStatus === "cancelled") {
					WebCtrl.unlock();
					if (onCancelled) {
						await onCancelled();
					}
					return;
				}

				/*
				 * エラー発生時
				 */
				if (processStatus === "error") {
					WebCtrl.unlock();
					if (onError) {
						onError(status.data?.message);
					}
					return;
				}

				/*
				 * 処理中の場合はポーリング再起処理
				 */
				setTimeout(doPolling, Constants.POLLING_INTERVAL);
			} catch (e) {
				WebCtrl.unlock();
				WebUI.catchFatal(e);
			}
		};
		doPolling();
	}

	/**
	 * UIをロックしてオーバーレイを表示します。<br>
	 * このメソッドを複数回呼び出した場合、オーバーレイは1つだけ表示され、unlock()を同じ回数呼び出すまで維持されます。<br>
	 * @param {boolean} [cancellable=false] - キャンセルボタンを表示する場合にtrueを指定
	 */
	static lock(cancellable = false) {
		if (++WebCtrl.#overlayCount === 1) {
			document.body.classList.add("procesing");
			WebCtrl.#overlayTimer = setTimeout(() => {
				WebCtrl.#overlayTimer = null;
				if (WebCtrl.#overlayCount > 0) {
					WebCtrl.#createOverlay(cancellable);
				}
			}, Constants.PROCESSING_OVERLAY_DELAY);
		}
	}

	/**
	 * UIロックを解除してオーバーレイを非表示にします。<br>
	 * lock()を複数回呼び出した場合は、unlock()も同じ回数呼び出す必要があります。<br>
	 */
	static unlock() {
		if (--WebCtrl.#overlayCount <= 0) {
			WebCtrl.#overlayCount = 0;
			document.body.classList.remove("procesing");
			if (WebCtrl.#overlayTimer !== null) {
				clearTimeout(WebCtrl.#overlayTimer);
				WebCtrl.#overlayTimer = null;
			}
			if (WebCtrl.#overlayEl) {
				WebCtrl.#overlayEl.remove();
				WebCtrl.#overlayEl = null;
			}
		}
	}

	/**
	 * WebAPIを呼び出して即時レスポンスを受け取ります。<br>
	 * サーバーが即座に結果を返す場合に使用します。<br>
	 * @param {Promise} webApi - WebAPIメソッド呼び出しの Promise
	 * @param {object} [options] - オプション
	 * @param {Function} [options.onDone] - 成功時コールバック(result を受け取る)
	 * @param {Function} [options.onError] - エラー時コールバック
	 */
	static async doAwait(
		webApi,
		{
			onDone, //
			onError, //
		} = {},
	) {
		WebCtrl.lock();
		try {
			const result = await webApi;
			if (!result) {
				if (onError) {
					await onError();
				}
			} else {
				if (onDone) {
					await onDone(result);
				}
			}
		} finally {
			WebCtrl.unlock();
		}
	}

	/**
	 * WebAPIを呼び出してバックグラウンドプロセスのポーリングを開始します。<br>
	 * サーバーがバックグラウンドプロセスを起動してポーリングで完了を待つ場合に使用します。<br>
	 * @param {Promise} webApi - WebAPIメソッド呼び出しの Promise
	 * @param {object} [options] - ポーリング処理オプション(#pollingAsyc()参照)
	 */
	static async doAsync(
		webApi,
		{
			onDone, //
			onError, //
			onCancelled, //
			cancellable = true, //
		} = {},
	) {
		/*
		 * APIリクエスト中はキャンセル不可でロック(サーバー受付前にキャンセルボタンを出さない)
		 * レスポンス後にいったん解除し、#pollingAsyc内でcancellable付きの再ロックに引き継ぐ
		 */
		let result;
		WebCtrl.lock();
		try {
			result = await webApi;
		} finally {
			WebCtrl.unlock();
		}
		if (!result) {
			return;
		}
		WebCtrl.#pollingAsyc({
			onDone, //
			onError, //
			onCancelled, //
			cancellable, //
		});
	}
}
