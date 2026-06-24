package org.ideaccum.ai.orchestrator.agent;

import java.util.Objects;

import org.ideaccum.ai.orchestrator.agents.AntigravityCliAgent;
import org.ideaccum.ai.orchestrator.agents.ClaudeCliAgent;
import org.ideaccum.ai.orchestrator.agents.CodexCliAgent;
import org.ideaccum.ai.orchestrator.agents.CopilotCliAgent;
import org.ideaccum.ai.orchestrator.agents.GeminiCliAgent;

/**
 * エージェント種類を提供する列挙型クラスです。<br>
 * <p>
 * 当クラスでは、アプリケーションで利用可能なエージェントの種類を定義します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/31	Kitagawa		新規作成
 *-->
 */
public enum AgentType {

	/** CaludeCode CLI */
	CLAUDE_CLI(ClaudeCliAgent.class, "claude-cli"),
	//CLAUDE_CLI(ClaudeCliResidentAgent.class, "claude-cli"),

	/** Gemini CLI */
	GEMINI_CLI(GeminiCliAgent.class, "gemini-cli"),

	/** Antigravity CLI */
	ANTIGRAVITY_CLI(AntigravityCliAgent.class, "antigravity-cli"),

	/** Codex CLI */
	CODEX_CLI(CodexCliAgent.class, "codex-cli"),

	/** GitHub Copilot CLI */
	COPILOT_CLI(CopilotCliAgent.class, "copilot-cli"),

	;

	/** エージェントクラスタイプ */
	private Class<? extends Agent> type;

	/** エージェント種類名 */
	private String name;

	/**
	 * コンストラクタ<br>
	 * @param type エージェントクラスタイプ
	 * @param name エージェント種類名
	 */
	private AgentType(Class<? extends Agent> type, String name) {
		this.type = type;
		this.name = name;
	}

	/**
	 * 種類名からエージェント種類を取得します。<br>
	 * @param name 種類名
	 * @return エージェント種類
	 */
	public static AgentType of(String name) {
		for (AgentType type : values()) {
			if (Objects.equals(type.name, name)) {
				return type;
			}
		}
		return null;
	}

	/**
	 * エージェントクラスタイプを取得します。<br>
	 * @return エージェントクラスタイプ
	 */
	public Class<? extends Agent> getType() {
		return type;
	}

	/**
	 * エージェント種類名を取得します。<br>
	 * @return エージェント種類名
	 */
	public String getName() {
		return name;
	}
}
