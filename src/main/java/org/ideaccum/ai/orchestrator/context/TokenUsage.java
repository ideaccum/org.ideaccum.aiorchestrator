package org.ideaccum.ai.orchestrator.context;

import java.io.Serializable;

import org.ideaccum.ai.orchestrator.Constants;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * エージェントとのやり取りで消費されたトークン使用量を管理するクラスです。<br>
 * <p>
 * 入力トークン数・出力トークン数・上限トークン数を保持します。<br>
 * 上限トークン数が不明な場合は -1 を設定します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/31	Kitagawa		新規作成
 *-->
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenUsage implements Constants, Serializable {

	/** 入力トークン数 */
	@JsonProperty
	private long inputTokens;

	/** 出力トークン数 */
	@JsonProperty
	private long outputTokens;

	/**
	 * コンストラクタ<br>
	 * @param inputTokens 入力トークン数
	 * @param outputTokens 出力トークン数 
	 */
	public TokenUsage(long inputTokens, long outputTokens) {
		super();
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
	}

	/**
	 * コンストラクタ<br>
	 */
	public TokenUsage() {
		this(0, 0);
	}

	/**
	 * 使用トークン量を追加します。<br>
	 * @param inputTokens 入力トークン数
	 * @param outputTokens 出力トークン数 
	 * @param othrerTokens その他発生トークン数
	 */
	public void add(long inputTokens, long outputTokens) {
		this.inputTokens += inputTokens;
		this.outputTokens += outputTokens;
	}

	/**
	 * 使用トークン量を追加します。<br>
	 * @param usage 使用トークン量
	 */
	public void add(TokenUsage usage) {
		if (usage == null) {
			return;
		}
		this.inputTokens += usage.inputTokens;
		this.outputTokens += usage.outputTokens;
	}

	/**
	 * 使用トークン量を返却します。<br>
	 * @return 使用トークン量
	 */
	public long getInputTokens() {
		return inputTokens;
	}

	/**
	 * 使用トークン量を返却します。<br>
	 * @return 使用トークン量
	 */
	public long getOutputTokens() {
		return outputTokens;
	}

	/**
	 * 合計トークン数を返却します。<br>
	 * @return 合計トークン数(入力トークン数+出力トークン数+その他発生トークン数)
	 */
	public long getTotalTokens() {
		return inputTokens + outputTokens;
	}
}
