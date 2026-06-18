package org.ideaccum.ai.orchestrator.context;

import java.nio.file.Path;
import java.util.Map;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agent.Agent;

/**
 * 処理実行を行う上での各種情報を管理するコンテキストクラスです。<br>
 * <p>
 * 処理実行に必要な環境設定情報、エージェント会話ログ情報、セッションストア情報、エージェントインスタンスリスト、タスクプロンプトなどを管理します。<br>
 * このクラスは、処理実行の各段階で必要な情報を保持し、エージェントやタスクの実行に利用されます。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public class Context implements Constants {

	/** プロジェクト名 */
	private String projectName;

	/** 環境設定情報 */
	private Config config;

	/** エージェント会話ログ情報 */
	private Conversations conversations;

	/** セッションストア情報 */
	private Sessions sessions;

	/** エージェントインスタンス */
	private Map<String, Agent> agents;

	/** リーダーエージェントインスタンス */
	private Agent leaderAgent;

	/** プロンプトファクトリ */
	private PromptFactory promptFactory;

	/** タスク内容 */
	private String task;

	/** トークン使用量 */
	private TokenUsage usage;

	/**
	 * コンストラクタ<br>
	 * @param projectName プロジェクト名
	 * @param config 環境設定情報
	 */
	Context(String projectName, Config config) {
		super();
		this.projectName = projectName;
		this.config = config;
	}

	/**
	 * プロジェクト名を取得します。<br>
	 * @return プロジェクト名
	 */
	public String getProjectName() {
		return projectName;
	}

	/**
	 * エージェント作業ルートパスを取得します。<br>
	 * @return エージェント作業ルートパス
	 */
	public Path getAgentRootPath() {
		Path defaultPath = config.getApplicationProjectPath(projectName);
		ProjectConfig projectConfig = new ProjectConfig(config.getApplicationProjectPropertiesPath(projectName));
		// プロジェクト設定で外部パスが有効な場合は外部パス、以外の場合はプロジェクトパス
		if (projectConfig.isExternalEnabled()) {
			String externalPath = projectConfig.getExternalPath();
			if (externalPath != null && !externalPath.isBlank()) {
				return Path.of(externalPath);
			}
		}
		return defaultPath;
	}

	/**
	 * 環境設定情報を取得します。<br>
	 * @return 環境設定情報
	 */
	public Config getConfig() {
		return config;
	}

	/**
	 * エージェント会話ログ情報を取得します。<br>
	 * @return エージェント会話ログ情報
	 */
	public Conversations getConversations() {
		return conversations;
	}

	/**
	 * エージェント会話ログ情報を設定します。<br>
	 * @param conversations エージェント会話ログ情報
	 */
	void setConversations(Conversations conversations) {
		this.conversations = conversations;
	}

	/**
	 * セッションストア情報を取得します。<br>
	 * @return sessions
	 */
	public Sessions getSessions() {
		return sessions;
	}

	/**
	 * セッションストア情報を設定します。<br>
	 * @param sessions セッションストア情報
	 */
	void setSessions(Sessions sessions) {
		this.sessions = sessions;
	}

	/**
	 * エージェントインスタンスリストを取得します。<br>
	 * @return エージェントインスタンスリスト
	 */
	public Map<String, Agent> getAgents() {
		return agents;
	}

	/**
	 * エージェントインスタンスを取得します。<br>
	 * @param agentName エージェント名
	 * @return エージェントインスタンス
	 */
	public Agent getAgent(String agentName) {
		return agents.get(agentName);
	}

	/**
	 * エージェントが存在するか判定します。<br>
	 * @param agentName エージェント名
	 * @return エージェントが存在する場合にtrueを返却
	 */
	public boolean hasAgent(String agentName) {
		return agents.containsKey(agentName);
	}

	/**
	 * エージェントインスタンスリストを設定します。<br>
	 * @param agents エージェントインスタンスリスト
	 */
	void setAgents(Map<String, Agent> agents) {
		this.agents = agents;
		if (agents != null) {
			for (Agent agent : agents.values()) {
				if (agent.isLeader()) {
					this.leaderAgent = agent;
					return;
				}
			}
			this.leaderAgent = null;
		}
	}

	/**
	 * リーダーエージェントインスタンスを取得します。<br>
	 * @return リーダーエージェントインスタンス
	 */
	public Agent getLeaderAgent() {
		return leaderAgent;
	}

	/**
	 * プロンプトファクトリを取得します。<br>
	 * @return プロンプトファクトリ
	 */
	public PromptFactory getPromptFactory() {
		return promptFactory;
	}

	/**
	 * プロンプトファクトリを設定します。<br>
	 * @param promptFactory プロンプトファクトリ
	 */
	void setPromptFactory(PromptFactory promptFactory) {
		this.promptFactory = promptFactory;
	}

	/**
	 * タスク内容を取得します。<br>
	 * @return タスク内容
	 */
	public String getTask() {
		return task;
	}

	/**
	 * タスク内容を設定します。<br>
	 * @param task タスク内容
	 */
	public void setTask(String task) {
		this.task = task;
	}

	/**
	 * トークン使用量を取得します。<br>
	 * @return トークン使用量
	 */
	public TokenUsage getUsage() {
		return usage;
	}

	/**
	 * トークン使用量を設定します。<br>
	 * @param usage トークン使用量
	 */
	void setUsage(TokenUsage usage) {
		this.usage = usage;
	}
}
