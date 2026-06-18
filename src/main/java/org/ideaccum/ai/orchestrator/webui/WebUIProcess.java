package org.ideaccum.ai.orchestrator.webui;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * エージェント処理モニタリングWebインタフェースサーバープロセス管理クラスです。<br>
 * <p>
 * 処理プロセスは非同期処理で実行し、キャンセル可否フラグで呼び出し側が中断対応を制御するための情報管理を提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/06/16	Kitagawa		新規作成
 *-->
 */
public class WebUIProcess {

	/** タスクステータス: 実行中 */
	public static final String STATUS_RUNNING = "running";

	/** タスクステータス: 完了 */
	public static final String STATUS_DONE = "done";

	/** タスクステータス: キャンセル済み */
	public static final String STATUS_CANCELLED = "cancelled";

	/** タスクステータス: エラー */
	public static final String STATUS_ERROR = "error";

	/** キャンセルフラグ */
	private final AtomicBoolean cancelFlag = new AtomicBoolean(false);

	/** キャンセル可否(false の場合は中断ボタン無効化用途に限定) */
	private final boolean cancellable;

	/** タスクステータス */
	private volatile String status = STATUS_RUNNING;

	/** メッセージ(エラー・キャンセル時の説明) */
	private volatile String message = null;

	/** タスク完了結果データ */
	private volatile Map<String, Object> result = null;

	/**
	 * コンストラクタ<br>
	 * @param cancellable キャンセル可否フラグ
	 */
	public WebUIProcess(boolean cancellable) {
		this.cancellable = cancellable;
	}

	/**
	 * 処理結果の説明メッセージを取得します。<br>
	 * @return 処理結果の説明メッセージ
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * 処理結果の説明メッセージを設定します。<br>
	 * @param message 処理結果の説明メッセージ
	 */
	public void setMessage(String message) {
		this.message = message;
	}

	/**
	 * タスクステータスを取得します。<br>
	 * @return タスクステータス
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * タスクステータスを設定します。<br>
	 * @param status タスクステータス
	 */
	public void setStatus(String status) {
		this.status = status;
	}

	/**
	 * タスク完了結果データを取得します。<br>
	 * @return タスク完了結果データ
	 */
	public Map<String, Object> getResult() {
		return result;
	}

	/**
	 * タスク完了結果データを設定します。<br>
	 * @param result タスク完了結果データ
	 */
	public void setResult(Map<String, Object> result) {
		this.result = result;
	}

	/**
	 * キャンセル可否を取得します。<br>
	 * @return キャンセル可否
	 */
	public boolean isCancellable() {
		return cancellable;
	}

	/**
	 * キャンセルフラグを取得します。<br>
	 * @return キャンセルフラグ
	 */
	public boolean isCancelled() {
		return cancelFlag.get();
	}

	/**
	 * 処理をキャンセルします。<br>
	 * <p>
	 * キャンセル可否が true の場合、キャンセルフラグを立てます。<br>
	 * 呼び出し側はこのフラグを監視して処理中断対応を行います。<br>
	 * </p>
	 */
	public void cancel() {
		if (cancellable) {
			cancelFlag.set(true);
		}
	}
}
