package org.ideaccum.ai.orchestrator.webui;

/**
 * WebAPI共通レスポンスクラスです。<br>
 * <p>
 * APIレスポンスの成否・エラーメッセージ・データを統一的に表現します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/10	Kitagawa		新規作成
 *-->
 */
public class AgentWebUIApiResponse {

	/** 処理成否 */
	private final boolean success;

	/** レスポンスデータ(成功時のみ) */
	private final Object data;

	/** エラーメッセージ(失敗時のみ) */
	private final String error;

	/** 情報メッセージ(成功時のみ) */
	private final String message;

	/** 警告メッセージ(成功時のみ) */
	private final String warning;

	/**
	 * コンストラクタ<br>
	 */
	private AgentWebUIApiResponse(boolean success, Object data, String error, String message, String warning) {
		this.success = success;
		this.data = data;
		this.error = error;
		this.message = message;
		this.warning = warning;
	}

	/**
	 * 成功レスポンスを生成します。<br>
	 * @return 成功レスポンスオブジェクト
	 */
	public static AgentWebUIApiResponse ok() {
		return new AgentWebUIApiResponse(true, null, null, null, null);
	}

	/**
	 * データ付き成功レスポンスを生成します。<br>
	 * @param data レスポンスデータ
	 * @return 成功レスポンスオブジェクト
	 */
	public static AgentWebUIApiResponse ok(Object data) {
		return new AgentWebUIApiResponse(true, data, null, null, null);
	}

	/**
	 * データ・情報メッセージ付き成功レスポンスを生成します。<br>
	 * @param data レスポンスデータ
	 * @param message 情報メッセージ
	 * @return 成功レスポンスオブジェクト
	 */
	public static AgentWebUIApiResponse ok(Object data, String message) {
		return new AgentWebUIApiResponse(true, data, null, message, null);
	}

	/**
	 * データ・警告メッセージ付き成功レスポンスを生成します。<br>
	 * @param data レスポンスデータ
	 * @param warning 警告メッセージ
	 * @return 成功レスポンスオブジェクト
	 */
	public static AgentWebUIApiResponse warn(Object data, String warning) {
		return new AgentWebUIApiResponse(true, data, null, null, warning);
	}

	/**
	 * エラーレスポンスを生成します。<br>
	 * @param message エラーメッセージ
	 * @return エラーレスポンスオブジェクト
	 */
	public static AgentWebUIApiResponse error(String message) {
		return new AgentWebUIApiResponse(false, null, message, null, null);
	}

	/**
	 * 処理成否を提供します。<br>
	 * @return 処理成否
	 */
	public boolean isSuccess() {
		return success;
	}

	/**
	 * レスポンスデータを提供します。<br>
	 * @return レスポンスデータ
	 */
	public Object getData() {
		return data;
	}

	/**
	 * エラーメッセージを提供します。<br>
	 * @return エラーメッセージ
	 */
	public String getError() {
		return error;
	}

	/**
	 * 情報メッセージを提供します。<br>
	 * @return 情報メッセージ
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * 警告メッセージを提供します。<br>
	 * @return 警告メッセージ
	 */
	public String getWarning() {
		return warning;
	}
}
