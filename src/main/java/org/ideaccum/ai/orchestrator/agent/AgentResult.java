package org.ideaccum.ai.orchestrator.agent;

import java.io.Serializable;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.context.TokenUsage;

/**
 * エージェントに対するプロンプト実行時のレスポンス内容を管理するクラスです。<br>
 * <p>
 * エージェントの実行結果を表すレコードクラスで、エージェント名、レスポンス内容、実行時間、正常実行状態フラグを保持します。<br>
 * </p>
 *
 * @author Kitagawabr>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public class AgentResult implements Constants, Serializable {

	/** エージェント名 */
	private String agentName;

	/** レスポンス内容 */
	private String response;

	/** 実行時間 */
	private long elapsedTime;

	/** トークン使用量(取得できない場合はnull) */
	private TokenUsage usage;

	/** 結果種類 */
	private AgentResultType type;

	/** エラー発生時例外 */
	private Throwable exception;

	/**
	 * コンストラクタ<br>
	 * @param agentName エージェント名
	 * @param response レスポンス内容
	 * @param elapsedTime 実行時間
	 * @param usage トークン使用量(取得できない場合はnull)
	 * @param type 結果種類
	 * @param exception エラー発生時例外
	 */
	protected AgentResult(String agentName, String response, long elapsedTime, TokenUsage usage, AgentResultType type, Throwable exception) {
		this.agentName = agentName;
		this.response = response;
		this.elapsedTime = elapsedTime;
		this.usage = usage;
		this.type = type;
		this.exception = exception;
	}

	/**
	 * プロンプト正常実行結果レコードを生成します。<br>
	 * @param agentName エージェント名
	 * @param response レスポンス内容
	 * @param elapsedTime 実行時間
	 * @param usage トークン使用量
	 * @return プロンプト正常実行結果レコード
	 */
	public static AgentResult success(String agentName, String response, long elapsedTime, TokenUsage usage) {
		return new AgentResult(agentName, response, elapsedTime, usage, AgentResultType.SUCCESS, null);
	}

	/**
	 * プロンプト以上実行時の結果レコードを生成します。<br>
	 * @param agentName エージェント名
	 * @param message エラーメッセージ
	 * @return プロンプト以上実行時の結果レコード
	 */
	public static AgentResult error(String agentName, String message) {
		return new AgentResult(agentName, message, 0, null, AgentResultType.ERROR, null);
	}

	/**
	 * プロンプト以上実行時の結果レコードを生成します。<br>
	 * @param agentName エージェント名
	 * @param message エラーメッセージ
	 * @param exception エラー発生時例外
	 * @return プロンプト以上実行時の結果レコード
	 */
	public static AgentResult error(String agentName, String message, Throwable exception) {
		return new AgentResult(agentName, message, 0, null, AgentResultType.ERROR, exception);
	}

	/**
	 * プロンプト処理終了実行結果レコードを生成します。<br>
	 * @param agentName エージェント名
	 * @param response レスポンス内容
	 * @param elapsedTime 実行時間
	 * @param usage トークン使用量
	 * @return プロンプト正常実行結果レコード
	 */
	public static AgentResult finish(String agentName, String response, long elapsedTime, TokenUsage usage) {
		return new AgentResult(agentName, response, elapsedTime, usage, AgentResultType.FINISH, null);
	}

	/**
	 * エージェント名を取得します。<br>
	 * @return エージェント名
	 */
	public String getAgentName() {
		return agentName;
	}

	/**
	 * レスポンス内容を取得します。<br>
	 * @return レスポンス内容
	 */
	public String getResponse() {
		return response;
	}

	/**
	 * 実行時間を取得します。<br>
	 * @return 実行時間
	 */
	public long getElapsedTime() {
		return elapsedTime;
	}

	/**
	 * トークン使用量を取得します。<br>
	 * @return トークン使用量(取得できない場合はnull)
	 */
	public TokenUsage getUsage() {
		return usage;
	}

	/**
	 * 結果種類を取得します<br>
	 * @return 結果種類
	 */
	public AgentResultType getType() {
		return type;
	}

	/**
	 * エラー発生時例外を取得します。<br>
	 * @return エラー発生時例外(エラーが発生していない場合はnull)
	 */
	public Throwable getException() {
		return exception;
	}
}
