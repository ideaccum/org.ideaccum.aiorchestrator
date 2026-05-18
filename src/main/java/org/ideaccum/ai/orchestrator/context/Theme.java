package org.ideaccum.ai.orchestrator.context;

import java.util.Objects;

/**
 * 
 * アプリケーションテーマを列挙型で提供します。<br>
 * <p>
 * ここで提供されるテーマは外部環境定義されるテーマプロパティ値と対応します。<br>
 * </p>
 * 
 * @author Kitagawa<br>
 * 
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/14	Kitagawa		新規作成
 *-->
 */
public enum Theme {

	/** デフォルトテーマ */
	DEFAULT(null, "css/theme_dark.css"),

	/** ダークテーマ */
	DARK("dark", "css/theme_dark.css"),

	/** ライトテーマ */
	LIGHT("light", "css/theme_light.css"),

	;

	/** プロパティ値 */
	private String property;

	/** スタイルシート名 */
	private String css;

	/**
	 * コンストラクタ<br>
	 * @param property プロパティ値
	 * @param css スタイルシート名
	 */
	private Theme(String property, String css) {
		this.property = property;
		this.css = css;
	}

	/**
	 * プロパティ値を取得します。<br>
	 * @return プロパティ値
	 */
	public String getProperty() {
		return property;
	}

	/**
	 * スタイルシート名を取得します。<br>
	 * @return スタイルシート名
	 */
	public String getCss() {
		return css;
	}

	/**
	 * プロパティ値からテーマを取得します。<br>
	 * @param property プロパティ値
	 * @return テーマ
	 */
	public static Theme of(String property) {
		for (Theme e : values()) {
			if (Objects.equals(property, e.property)) {
				return e;
			}
		}
		return DEFAULT;
	}
}
