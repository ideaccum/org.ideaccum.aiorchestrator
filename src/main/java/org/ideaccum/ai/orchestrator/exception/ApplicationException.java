package org.ideaccum.ai.orchestrator.exception;

import org.ideaccum.ai.orchestrator.Constants;

/**
 * アプリケーションで発生する例外ラッパークラスです。<br>
 * 
 * @author Kitagawa<br>
 * 
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public class ApplicationException extends RuntimeException implements Constants {

	/**
	 * コンストラクタ<br>
	 * @param message 例外メッセージ
	 * @param cause ルート例外
	 */
	public ApplicationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * コンストラクタ<br>
	 * @param message 例外メッセージ
	 */
	public ApplicationException(String message) {
		super(message);
	}
}
