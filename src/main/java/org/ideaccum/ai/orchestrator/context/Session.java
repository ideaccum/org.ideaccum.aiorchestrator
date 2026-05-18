package org.ideaccum.ai.orchestrator.context;

import java.io.Serializable;

import org.ideaccum.ai.orchestrator.Constants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 各エージェントのCLI単一セッションIDを管理するストアクラスです。<br>
 * <p>
 * セッションIDは、エージェントのCLI起動時に出力される特定の文字列から正規表現で抽出されます。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Session implements Constants, Serializable {

	/** エージェント名 */
	@JsonProperty
	private String agentName;

	/** セッションID */
	@JsonProperty
	private String sessionId;

	/** トークン使用量 */
	@JsonProperty
	private TokenUsage tokenUsage;

	/**
	 * コンストラクタ<br>
	 * @param agentName エージェント名
	 * @param sessionId セッションID
	 * @param tokenUsage トークン使用量
	 */
	public Session(String agentName, String sessionId, TokenUsage tokenUsage) {
		super();
		this.agentName = agentName;
		this.sessionId = sessionId;
		this.tokenUsage = tokenUsage;
	}

	/**
	 * コンストラクタ<br>
	 * @deprecated Jacksonからのデシリアライズ利用のためのみに設置されたコンストラクタです
	 */
	@Deprecated
	public Session() {
		this(null, null, null);
	}

	/**
	 * エージェント名を取得します。<br>
	 * @return エージェント名
	 */
	public String getAgentName() {
		return agentName;
	}

	/**
	 * エージェント名を設定します。<br>
	 * @param agentName エージェント名
	 */
	public void setAgentName(String agentName) {
		this.agentName = agentName;
	}

	/**
	 * セッションIDを取得します。<br>
	 * @return セッションID
	 */
	public String getSessionId() {
		return sessionId;
	}

	/**
	 * セッションIDを設定します。<br>
	 * @param sessionId セッションID
	 */
	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	/**
	 * トークン使用量を取得します。<br>
	 * @return トークン使用量
	 */
	public TokenUsage getTokenUsage() {
		if (tokenUsage == null) {
			tokenUsage = new TokenUsage();
		}
		return tokenUsage;
	}

	/**
	 * トークン使用量を設定します。<br>
	 * @param tokenUsage トークン使用量
	 */
	public void setTokenUsage(TokenUsage tokenUsage) {
		this.tokenUsage = tokenUsage;
	}
}
