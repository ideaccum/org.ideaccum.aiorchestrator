package org.ideaccum.ai.orchestrator.context;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.ideaccum.ai.orchestrator.Constants;

import tools.jackson.core.type.TypeReference;

/**
 * プロジェクト設定情報管理クラスです。<br>
 * <p>
 * プロジェクトディレクトリ配下の{@code .orchestrator/project.yaml}を読み書きします。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/01	Kitagawa		新規作成
 *-->
 */
public class ProjectConfig implements Constants {

	/** プロパティオブジェクト */
	private Map<String, String> properties;

	/**
	 * コンストラクタ<br>
	 * @param configFile プロジェクト設定ファイルパス
	 */
	public ProjectConfig(Path configFile) {
		this.properties = configFile == null ? new LinkedHashMap<>() : load(configFile);
	}

	/**
	 * プロパティファイルを読み込みます。<br>
	 * @param configFile 設定ファイルパス
	 * @return プロパティオブジェクト
	 */
	private Map<String, String> load(Path configFile) {
		if (!Files.exists(configFile)) {
			return new LinkedHashMap<>();
		}
		try {
			return YAML.readValue(configFile.toFile(), new TypeReference<LinkedHashMap<String, String>>() {
			});
		} catch (Throwable e) {
			throw new InternalError(String.format("プロジェクト設定ファイル(%s)の読み込みに失敗しました。", configFile.toString()), e);
		}
	}

	/**
	 * プロパティファイルを保存します。<br>
	 * @param configFile 設定ファイルパス
	 * @throws IOException プロパティファイル保存で予期せぬ例外が発生した場合にスローされます
	 */
	public void save(Path configFile) throws IOException {
		YAML.writeValue(configFile.toFile(), properties);
	}

	/**
	 * プロジェクト名を取得します。<br>
	 * @return プロジェクト名
	 */
	public String getName() {
		String key = "project.name";
		String raw = properties.getOrDefault(key, "");
		return raw;
	}

	/**
	 * プロジェクト名を設定します。<br>
	 * @param name プロジェクト名
	 */
	public void setName(String name) {
		String key = "project.name";
		properties.put(key, name);
	}

	/**
	 * プロジェクト表示タイトルを取得します。<br>
	 * @return プロジェクト表示タイトル
	 */
	public String getTitle() {
		String key = "project.title";
		String raw = properties.getOrDefault(key, "");
		return raw;
	}

	/**
	 * プロジェクト表示タイトルを設定します。<br>
	 * @param title プロジェクト表示タイトル
	 */
	public void setTitle(String title) {
		String key = "project.title";
		properties.put(key, title);
	}

	/**
	 * 外部パス使用フラグを取得します。<br>
	 * @return 外部パスを使用する場合にtrue
	 */
	public boolean isExternalEnabled() {
		String key = "project.external.enabled";
		String raw = properties.getOrDefault(key, "false");
		return Boolean.parseBoolean(raw);
	}

	/**
	 * 外部パス使用フラグを設定します。<br>
	 * @param enabled 外部パスを使用する場合にtrue
	 */
	public void setExternalEnabled(boolean enabled) {
		String key = "project.external.enabled";
		properties.put(key, String.valueOf(enabled));
	}

	/**
	 * エージェント作業ルートの外部パスを取得します。<br>
	 * @return 外部パス文字列(未設定の場合はnull)
	 */
	public String getExternalPath() {
		String key = "project.external.path";
		String raw = properties.getOrDefault(key, "");
		return raw.isBlank() ? null : raw;
	}

	/**
	 * エージェント作業ルートの外部パスを設定します。<br>
	 * @param path 外部パス文字列
	 */
	public void setExternalPath(String path) {
		String key = "project.external.path";
		properties.put(key, path == null ? "" : path);
	}
}
