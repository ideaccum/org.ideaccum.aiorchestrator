package org.ideaccum.ai.orchestrator.webui;

import java.util.List;

import org.ideaccum.ai.orchestrator.agent.Agent;

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
public class AgentWebUIEvent {

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

	/** トークン使用量 */
	@JsonProperty("tokenUsage")
	private Long tokenUsage;

	/** 処理時間 */
	@JsonProperty("elapsedTime")
	private Long elapsedTime;

	/** セッションID */
	@JsonProperty("sessionId")
	private String sessionId;

	/** メッセージ */
	@JsonProperty("message")
	private String message;

	/**
	 * コンストラクタ<br>
	 */
	private AgentWebUIEvent() {
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
	 * トークン使用量を取得します。<br>
	 * @return トークン使用量
	 */
	public Long getTokenUsage() {
		return tokenUsage;
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
	public static AgentWebUIEvent createAgentInitialized(List<Agent> agents) {
		AgentWebUIEvent e = new AgentWebUIEvent();
		e.type = "agent_initialized";
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
	public static AgentWebUIEvent createAgentStart(String agentName, String sessionId, String timestamp) {
		AgentWebUIEvent e = new AgentWebUIEvent();
		e.type = "agent_start";
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
	public static AgentWebUIEvent createAgentContent(String agentName, String content) {
		AgentWebUIEvent e = new AgentWebUIEvent();
		e.type = "agent_content";
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
	public static AgentWebUIEvent createAgentMessage(String agentName, String message) {
		AgentWebUIEvent e = new AgentWebUIEvent();
		e.type = "agent_message";
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
	public static AgentWebUIEvent createAgentFinish(String agentName, String sessionId, long tokenUsage, long elapsedTime) {
		AgentWebUIEvent e = new AgentWebUIEvent();
		e.type = "agent_finish";
		e.agentName = agentName;
		e.sessionId = sessionId;
		e.tokenUsage = tokenUsage;
		e.elapsedTime = elapsedTime;
		return e;
	}

	/**
	 * エージェントエラーイベント(agent_error)を生成します。<br>
	 * @param agentName エージェント名
	 * @param message エラーメッセージ
	 * @return エージェント処理モニタリングイベント
	 */
	public static AgentWebUIEvent createAgentError(String agentName, String message) {
		AgentWebUIEvent e = new AgentWebUIEvent();
		e.type = "agent_error";
		e.agentName = agentName;
		e.message = message;
		return e;
	}

	/**
	 * オーケストレーター処理完了イベント(orchestrator_done)を生成します。<br>
	 * @return エージェント処理モニタリングイベント
	 */
	public static AgentWebUIEvent createOrchestratorDone() {
		AgentWebUIEvent e = new AgentWebUIEvent();
		e.type = "orchestrator_done";
		return e;
	}

	/**
	 * オーケストレーター開始イベント(orchestrator_started)を生成します。<br>
	 * @return エージェント処理モニタリングイベント
	 */
	public static AgentWebUIEvent createOrchestratorStarted() {
		AgentWebUIEvent e = new AgentWebUIEvent();
		e.type = "orchestrator_started";
		return e;
	}

	/**
	 * オーケストレーター停止イベント(orchestrator_stopped)を生成します。<br>
	 * @return エージェント処理モニタリングイベント
	 */
	public static AgentWebUIEvent createOrchestratorStopped() {
		AgentWebUIEvent e = new AgentWebUIEvent();
		e.type = "orchestrator_stopped";
		return e;
	}
}
