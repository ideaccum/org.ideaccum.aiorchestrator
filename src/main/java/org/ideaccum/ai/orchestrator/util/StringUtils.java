package org.ideaccum.ai.orchestrator.util;

import org.ideaccum.ai.orchestrator.Constants;

/**
 * 文字列に関するユーティリティ操作メソッドを提供するクラスです。<br>
 * 
 * @author Kitagawa<br>
 * 
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/31	Kitagawa		新規作成
 *-->
 */
public class StringUtils implements Constants {

	/**
	 * コンストラクタ<br>
	 */
	private StringUtils() {
		super();
	}

	/**
	 * 文字列の半角換算文字長を返却します。<br>
	 * 半角文字(ASCII印字可能文字・半角カタカナ)は1、それ以外の全角文字は2として計算します。<br>
	 * @param value 対象文字列
	 * @return 半角換算文字長
	 */
	public static int lena(String value) {
		if (value == null) {
			return 0;
		}
		int length = 0;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			// 半角文字: ASCII印字可能文字(U+0020〜U+007E)および半角カタカナ(U+FF61〜U+FF9F)
			if ((c >= '\u0020' && c <= '\u007E') || (c >= '\uFF61' && c <= '\uFF9F')) {
				length += 1;
			} else {
				length += 2;
			}
		}
		return length;
	}

	/**
	 * 文字列を指定された半角換算長になるよう左詰めで半角スペースで埋めた文字列を返却します。<br>
	 * 文字列の半角換算長が指定長を超える場合は切り捨てます。<br>
	 * 全角文字の途中で切り捨てが発生する場合は半角スペースで補完して指定長に合わせます。<br>
	 * @param value 対象文字列
	 * @param length 半角換算での目標文字長
	 * @return 左詰めでスペースパディングされた文字列
	 */
	public static String padra(String value, int length) {
		String base = value == null ? "" : value;
		StringBuilder sb = new StringBuilder();
		int currentLength = 0;
		for (int i = 0; i < base.length(); i++) {
			char c = base.charAt(i);
			int charWidth = ((c >= '\u0020' && c <= '\u007E') || (c >= '\uFF61' && c <= '\uFF9F')) ? 1 : 2;
			if (currentLength + charWidth > length) {
				// 全角文字が収まりきらない場合は半角スペースで補完
				break;
			}
			sb.append(c);
			currentLength += charWidth;
		}
		while (currentLength < length) {
			sb.append(' ');
			currentLength++;
		}
		return sb.toString();
	}

	/**
	 * 指定された文字列がJSONとして扱えるか判定します。<br>
	 * @param string 判定対象文字列
	 * @return JSONとして扱える場合にtrueを返却
	 */
	public static boolean isJSON(String string) {
		try {
			JSON.readTree(string);
			return true;
		} catch (Throwable e) {
			return false;
		}
	}
}
