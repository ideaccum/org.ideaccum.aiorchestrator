package org.ideaccum.ai.orchestrator.agent;

import java.util.Objects;

import org.ideaccum.ai.orchestrator.Constants;

/**
 * アプリケーションで利用するエージェントクラスの共通的な処理を提供するクラスです。<br>
 * <p>
 * 当クラスでは任意のエージェントに依存しないすべてのエージェントにおける共通仕様のみを提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public abstract class AgentAdapter implements Constants, Agent {

	/**
	 * オブジェクトのハッシュコードを提供します。<br>
	 * <p>
	 * エージェントオブジェクトのハッシュコードはエージェント名、エージェント種別のみで生成されます。<br>
	 * </p>
	 * @return オブジェクトのハッシュコード
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash( //
				getName(), //
				getType() //
		);
	}

	/**
	 * オブジェクト等価比較を行います。<br>
	 * <p>
	 * エージェントオブジェクトはエージェント名、エージェント種別のみで等価比較を行います。<br>
	 * </p>
	 * @param object 比較対象オブジェクト
	 * @return オブジェクト同士が等価の場合にtrueを返却
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (object == null) {
			return false;
		}
		if (getClass() != object.getClass()) {
			return false;
		}
		AgentAdapter other = (AgentAdapter) object;
		return true //
				&& Objects.equals(getName(), other.getName()) //
				&& Objects.equals(getType(), other.getType()) //
		;
	}
}
