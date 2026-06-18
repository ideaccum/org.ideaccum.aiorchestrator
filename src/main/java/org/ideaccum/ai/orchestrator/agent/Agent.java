package org.ideaccum.ai.orchestrator.agent;

import org.ideaccum.ai.orchestrator.Constants;

/**
 * アプリケーションで利用するエージェントクラスのインタフェースを提供します。<br>
 * <p>
 * エージェントクラスは、タスクの実行に必要な環境設定情報、エージェント会話ログ情報、セッションストア情報、エージェントインスタンスリスト、タスクプロンプトなどを保持します。<br>
 * エージェントクラスはこのインタフェースを実装することで、アプリケーションで利用可能なエージェントとして機能します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public interface Agent extends Constants {

	/**
	 * エージェント名を取得します。<br>
	 * @return エージェント名
	 */
	public String getName();

	/**
	 * エージェント種類を取得します。<br>
	 * @return エージェント種類
	 */
	public String getType();

	/**
	 * エージェントモデルを取得します。<br>
	 * @return エージェントモデル
	 */
	public String getModel();

	/**
	 * エージェント役割名を取得します。<br>
	 * @return エージェント役割名
	 */
	public String getRole();

	/**
	 * エージェント動作性質を取得します。<br>
	 * @return エージェント動作性質
	 */
	public String getPersonality();

	/**
	 * エージェントセッションIDを取得します。<br>
	 * @return エージェントセッションID
	 */
	public String getSessionId();

	/**
	 * エージェントがリーダーエージェントであるか判定します。<br>
	 * @return リーダーエージェントである場合にtrueを返却
	 */
	public boolean isLeader();

	/**
	 * エージェントに対してプロンプトを実行させます。<br>
	 * @param prompt プロンプト文字列
	 * @return プロンプト実行結果
	 * @throws Throwable プロンプト実行時に予期せぬエラーが発生した場合にスローされます
	 */
	public AgentResult execute(String prompt) throws Throwable;

	/**
	 * エージェントの実行中プロセスを強制終了します。<br>
	 */
	public void stop();
}
