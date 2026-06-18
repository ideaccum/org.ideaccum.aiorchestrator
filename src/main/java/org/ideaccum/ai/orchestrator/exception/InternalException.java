package org.ideaccum.ai.orchestrator.exception;

import org.ideaccum.ai.orchestrator.Constants;

/**
 * アプリケーションで発生する例外ラッパークラスです。<br>
 * <p>
 * この例外クラスはアプリケーション内部で発生したユーザー側には詳細提供不要な内部エラー発生時に利用されます。<br>
 * </p>
 * 
 * @author Kitagawa<br>
 * 
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public class InternalException extends RuntimeException implements Constants {

	/**
	 * コンストラクタ<br>
	 * @param message 例外メッセージ
	 * @param cause ルート例外
	 */
	public InternalException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * コンストラクタ<br>
	 * @param message 例外メッセージ
	 */
	public InternalException(String message) {
		super(message);
	}
}
