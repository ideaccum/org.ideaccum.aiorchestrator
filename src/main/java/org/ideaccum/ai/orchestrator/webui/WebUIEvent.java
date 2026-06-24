package org.ideaccum.ai.orchestrator.webui;

import java.util.List;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agent.Agent;
import org.ideaccum.ai.orchestrator.context.TokenUsage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * エージェント処理モニタリングイベントのレコードクラスです。<br>
 * <p>
 * ブラウザとのSSEイベントにおける情報レコードを管理するクラスです。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/04/30	Kitagawa		新規作成
 *-->
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebUIEvent implements Constants {

	/** イベントタイプ */
	@JsonProperty("type")
	private String type;

	/** エージェント情報 */
	@JsonProperty("agents")
	private List<Agent> agents;

	/** エージェント名 */
	@JsonProperty("agentName")
	private String agentName;

	/** コンテンツ */
	@JsonProperty("content")
	private String content;

	/** タイムスタンプ */
	@JsonProperty("timestamp")
	private String timestamp;

	/** トークン使用量(合計) */
	@JsonProperty("tokenUsage")
	private Long tokenUsage;

	/** 入力トークン数 */
	@JsonProperty("inputTokens")
	private Long inputTokens;

	/** 出力トークン数 */
	@JsonProperty("outputTokens")
	private Long outputTokens;

	/** 処理時間 */
	@JsonProperty("elapsedTime")
	private Long elapsedTime;

	/** セッションID */
	@JsonProperty("sessionId")
	private String sessionId;

	/** メッセージ */
	@JsonProperty("message")
	private String message;

	/** プロジェクト名 */
	@JsonProperty("projectName")
	private String projectName;

	/**
	 * コンストラクタ<br>
	 */
	private WebUIEvent() {
	}

	/**
	 * イベントタイプを取得します。<br>
	 * @return イベントタイプ
	 */
	public String getType() {
		return type;
	}

	/**
	 * エージェント情報リストを取得します。<br>
	 * @return エージェント情報リスト
	 */
	public List<Agent> getAgents() {
		return agents;
	}

	/**
	 * エージェント名を取得します。<br>
	 * @return エージェント名
	 */
	public String getAgentName() {
		return agentName;
	}

	/**
	 * コンテンツを取得します。<br>
	 * @return コンテンツ
	 */
	public String getContent() {
		return content;
	}

	/**
	 * タイムスタンプを取得します。<br>
	 * @return タイムスタンプ
	 */
	public String getTimestamp() {
		return timestamp;
	}

	/**
	 * トークン使用量(合計)を取得します。<br>
	 * @return トークン使用量(合計)
	 */
	public Long getTokenUsage() {
		return tokenUsage;
	}

	/**
	 * 入力トークン数を取得します。<br>
	 * @return 入力トークン数
	 */
	public Long getInputTokens() {
		return inputTokens;
	}

	/**
	 * 出力トークン数を取得します。<br>
	 * @return 出力トークン数
	 */
	public Long getOutputTokens() {
		return outputTokens;
	}

	/**
	 * 処理時間を取得します。<br>
	 * @return 処理時間
	 */
	public Long getElapsedTime() {
		return elapsedTime;
	}

	/**
	 * セッションIDを取得します。<br>
	 * @return セッションID
	 */
	public String getSessionId() {
		return sessionId;
	}

	/**
	 * メッセージを取得します。<br>
	 * @return メッセージ
	 */
	public String getMessage() {
		return message;
	}

	/**
	 * エージェント初期化完了イベント(agent_initialized)を生成します。<br>
	 * @param agents エージェントリスト
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createAgentInitialized(List<Agent> agents) {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_AGENT_INITIALIZED;
		e.agents = agents;
		return e;
	}

	/**
	 * エージェント処理開始イベント(agent_start)を生成します。<br>
	 * @param agentName エージェント名
	 * @param sessionId セッションID
	 * @param timestamp タイムスタンプ
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createAgentStart(String agentName, String sessionId, String timestamp) {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_AGENT_START;
		e.agentName = agentName;
		e.sessionId = sessionId;
		e.timestamp = timestamp;
		return e;
	}

	/**
	 * エージェントコンテンツイベント(agent_content)を生成します。<br>
	 * @param agentName エージェント名
	 * @param content コンテンツ
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createAgentContent(String agentName, String content) {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_AGENT_CONTENT;
		e.agentName = agentName;
		e.content = content;
		return e;
	}

	/**
	 * エージェント一般メッセージイベント(agent_message)を生成します。<br>
	 * @param agentName エージェント名
	 * @param message メッセージ
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createAgentMessage(String agentName, String message) {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_AGENT_MESSAGE;
		e.agentName = agentName;
		e.message = message;
		return e;
	}

	/**
	 * エージェント処理完了イベント(agent_finish)を生成します。<br>
	 * @param agentName エージェント名
	 * @param sessionId セッションID
	 * @param tokenUsage トークン使用量
	 * @param elapsedTime 処理時間
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createAgentFinish(String agentName, String sessionId, long tokenUsage, long elapsedTime) {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_AGENT_FINISH;
		e.agentName = agentName;
		e.sessionId = sessionId;
		e.tokenUsage = tokenUsage;
		e.elapsedTime = elapsedTime;
		return e;
	}

	/**
	 * エージェント処理完了イベント(agent_finish)を生成します。<br>
	 * @param agentName エージェント名
	 * @param sessionId セッションID
	 * @param usage トークン使用量
	 * @param elapsedTime 処理時間
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createAgentFinish(String agentName, String sessionId, TokenUsage usage, long elapsedTime) {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_AGENT_FINISH;
		e.agentName = agentName;
		e.sessionId = sessionId;
		e.tokenUsage = usage != null ? usage.getTotalTokens() : 0L;
		e.inputTokens = usage != null ? usage.getInputTokens() : 0L;
		e.outputTokens = usage != null ? usage.getOutputTokens() : 0L;
		e.elapsedTime = elapsedTime;
		return e;
	}

	/**
	 * エージェントエラーイベント(agent_error)を生成します。<br>
	 * @param agentName エージェント名
	 * @param message エラーメッセージ
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createAgentError(String agentName, String message) {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_AGENT_ERROR;
		e.agentName = agentName;
		e.message = message;
		return e;
	}

	/**
	 * エージェントセッションID更新イベント(agent_session_updated)を生成します。<br>
	 * @param agentName エージェント名
	 * @param sessionId セッションID
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createAgentSessionUpdated(String agentName, String sessionId) {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_AGENT_SESSION_UPDATED;
		e.agentName = agentName;
		e.sessionId = sessionId;
		return e;
	}

	/**
	 * オーケストレーター処理完了イベント(orchestrator_done)を生成します。<br>
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createOrchestratorDone() {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_ORCHESTRATOR_DONE;
		return e;
	}

	/**
	 * オーケストレーター開始イベント(orchestrator_started)を生成します。<br>
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createOrchestratorStarted() {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_ORCHESTRATOR_STARTED;
		return e;
	}

	/**
	 * オーケストレーター停止イベント(orchestrator_stopped)を生成します。<br>
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createOrchestratorStopped() {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_ORCHESTRATOR_STOPPED;
		return e;
	}

	/**
	 * プロジェクト変更イベント(project_changed)を生成します。<br>
	 * @param projectName 変更後のプロジェクト名
	 * @return エージェント処理モニタリングイベント
	 */
	public static WebUIEvent createProjectChanged(String projectName) {
		WebUIEvent e = new WebUIEvent();
		e.type = EVENT_PROJECT_CHANGED;
		e.projectName = projectName;
		return e;
	}
}
