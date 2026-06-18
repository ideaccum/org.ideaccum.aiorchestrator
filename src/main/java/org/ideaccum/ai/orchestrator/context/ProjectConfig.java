package org.ideaccum.ai.orchestrator.context;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * プロジェクト設定情報管理クラスです。<br>
 * <p>
 * プロジェクトディレクトリ配下の{@code .orchestrator/project.properties}を読み書きします。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/01	Kitagawa		新規作成
 *-->
 */
public class ProjectConfig {

	/** プロパティオブジェクト */
	private Properties properties;

	/**
	 * コンストラクタ<br>
	 * @param configFile プロジェクト設定ファイルパス
	 */
	public ProjectConfig(Path configFile) {
		this.properties = configFile == null ? new Properties() : load(configFile);
	}

	/**
	 * プロパティファイルを読み込みます。<br>
	 * @param configFile 設定ファイルパス
	 * @return プロパティオブジェクト
	 */
	private Properties load(Path configFile) {
		Properties properties = new Properties();
		if (configFile == null || !Files.exists(configFile)) {
			properties = new Properties();
			return properties;
		}
		try (Reader reader = new InputStreamReader(new BufferedInputStream(Files.newInputStream(configFile)), StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (Throwable e) {
			throw new InternalError(String.format("プロジェクト設定ファイル(%s)の読み込みに失敗しました。", configFile.toString()), e);
		}
		return properties;
	}

	/**
	 * プロパティファイルを保存します。<br>
	 * @param configFile 設定ファイルパス
	 * @throws IOException プロパティファイル保存で予期せぬ例外が発生した場合にスローされます
	 */
	public void save(Path configFile) throws IOException {
		try (OutputStream os = new BufferedOutputStream(new FileOutputStream(configFile.toFile()))) {
			properties.store(os, "");
		}
	}

	/**
	 * プロジェクト名を取得します。<br>
	 * @return プロジェクト名
	 */
	public String getName() {
		String key = "project.name";
		String raw = properties.getProperty(key);
		return raw;
	}

	/**
	 * プロジェクト名を設定します。<br>
	 * @param name プロジェクト名
	 */
	public void setName(String name) {
		String key = "project.name";
		properties.setProperty(key, name);
	}

	/**
	 * プロジェクト表示タイトルを取得します。<br>
	 * @return プロジェクト表示タイトル
	 */
	public String getTitle() {
		String key = "project.title";
		String raw = properties.getProperty(key);
		return raw;
	}

	/**
	 * プロジェクト表示タイトルを設定します。<br>
	 * @param title プロジェクト表示タイトル
	 */
	public void setTitle(String title) {
		String key = "project.title";
		properties.setProperty(key, title);
	}

	/**
	 * 外部パス使用フラグを取得します。<br>
	 * @return 外部パスを使用する場合にtrue
	 */
	public boolean isExternalEnabled() {
		String key = "project.external.enabled";
		String raw = properties.getProperty(key, "false");
		return Boolean.parseBoolean(raw);
	}

	/**
	 * 外部パス使用フラグを設定します。<br>
	 * @param enabled 外部パスを使用する場合にtrue
	 */
	public void setExternalEnabled(boolean enabled) {
		String key = "project.external.enabled";
		properties.setProperty(key, String.valueOf(enabled));
	}

	/**
	 * エージェント作業ルートの外部パスを取得します。<br>
	 * @return 外部パス文字列(未設定の場合はnull)
	 */
	public String getExternalPath() {
		String key = "project.external.path";
		String raw = properties.getProperty(key, "");
		return raw.isBlank() ? null : raw;
	}

	/**
	 * エージェント作業ルートの外部パスを設定します。<br>
	 * @param path 外部パス文字列
	 */
	public void setExternalPath(String path) {
		String key = "project.external.path";
		properties.setProperty(key, path == null ? "" : path);
	}
}
