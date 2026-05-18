package org.ideaccum.ai.orchestrator.agent;

/**
 * エージェント実行レスポンス種類を提供する列挙型クラスです。<br>
 * <p>
 * エージェント実行レスポンスの種類を定義する列挙型クラスで、正常レスポンス、エラーレスポンス、処理終了レスポンス等を提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/31	Kitagawa		新規作成
 *-->
 */
public enum AgentResultType {

	/** 正常レスポンス */
	SUCCESS,

	/** エラーレスポンス */
	ERROR,

	/** 処理終了レスポンス */
	FINISH,
}
