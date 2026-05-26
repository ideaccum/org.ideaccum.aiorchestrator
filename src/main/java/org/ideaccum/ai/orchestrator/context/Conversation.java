package org.ideaccum.ai.orchestrator.context;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agent.Agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * エージェント間の単一のやり取りの会話ログ管理クラスです。<br>
 * <p>
 * 会話ログは、JSON形式でイテレーション単位の記録として管理するクラスです。<br>
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
public class Conversation implements Constants, Serializable {

	/** コンテキストオブジェクト */
	@JsonIgnore
	private Context context;

	/** タイムスタンプ */
	@JsonProperty
	private String timestamp;

	/** エージェント名 */
	@JsonProperty
	private String agentName;

	/** 会話内容 */
	@JsonProperty
	private String content;

	/** トークン使用量 */
	@JsonProperty
	private TokenUsage tokenUsage;

	/**
	 * コンストラクタ<br>
	 * @param context コンテキストオブジェクト
	 * @param timestamp タイムスタンプ
	 * @param agentName エージェント名
	 * @param content 会話内容
	 * @param tokenUsage トークン使用量
	 */
	public Conversation(Context context, String timestamp, String agentName, String content, TokenUsage tokenUsage) {
		super();
		this.context = context;
		this.timestamp = timestamp;
		this.agentName = agentName;
		this.content = content;
		this.tokenUsage = tokenUsage;
	}

	/**
	 * コンストラクタ<br>
	 * @deprecated Jacksonからのデシリアライズ利用のためのみに設置されたコンストラクタです
	 */
	@Deprecated
	public Conversation() {
		this(null, null, null, null, null);
	}

	/**
	 * オブジェクト内容を文字列として提供します。<br>
	 * @return オブジェクト内容文字列
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return String.format("%s [%s]\n%s\n\n", //
				timestamp, //
				agentName, //
				content //
		);
	}

	/**
	 * コンテキストオブジェクトを取得します。<br>
	 * @return コンテキストオブジェクト
	 */
	public Context getContext() {
		return context;
	}

	/**
	 * コンテキストオブジェクトを設定します。<br>
	 * @param context コンテキストオブジェクト
	 */
	void setContext(Context context) {
		this.context = context;
	}

	/**
	 * タイムスタンプを取得します。<br>
	 * @return タイムスタンプ
	 */
	public String getTimestamp() {
		return timestamp;
	}

	/**
	 * タイムスタンプを設定します。<br>
	 * @param timestamp タイムスタンプ
	 */
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
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
	 * 会話内容を取得します。<br>
	 * @return 会話内容
	 */
	public String getContent() {
		return content;
	}

	/**
	 * 会話内容を設定します。<br>
	 * @param content 会話内容
	 */
	public void setContent(String content) {
		this.content = content;
	}

	/**
	 * トークン使用量を取得します。<br>
	 * @return トークン使用量
	 */
	public TokenUsage getTokenUsage() {
		return tokenUsage;
	}

	/**
	 * トークン使用量を設定します。<br>
	 * @param tokenUsage トークン使用量
	 */
	public void setTokenUsage(TokenUsage tokenUsage) {
		this.tokenUsage = tokenUsage;
	}

	/**
	 * 会話内容からディスパッチキーワードを抽出して対象エージェントリストを取得します。<br>
	 * @return ディスパッチ対象エージェントリスト
	 */
	@JsonIgnore
	public List<Agent> getDispatchAgents() {
		List<Agent> result = new LinkedList<>();
		if (content == null) {
			return result;
		}
		Pattern pattern = context.getConfig().getAgentDispatchKeywordPattern();
		Matcher matcher = pattern.matcher(content);
		while (matcher.find()) {
			String agentName = matcher.group(1).trim();
			Agent agent = context.getAgent(agentName);
			if (agent != null) {
				result.add(agent);
			}
		}
		return result;
	}
}
