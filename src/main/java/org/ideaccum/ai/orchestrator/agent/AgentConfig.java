package org.ideaccum.ai.orchestrator.agent;

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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.exception.ApplicationException;

/**
 * エージェント個別環境定義アクセス用のクラスです。<br>
 * <p>
 * エージェント設定ファイルからプロパティを読み込み、エージェント環境定義を提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public class AgentConfig implements Constants {

	/** プロパティオブジェクト */
	private Properties properties;

	/**
	 * コンストラクタ<br>
	 * @param configFile エージェント設定ファイルパス
	 */
	public AgentConfig(Path configFile) {
		this.properties = configFile == null ? new Properties() : load(configFile);
	}

	/**
	 * コンストラクタ<br>
	 */
	public AgentConfig() {
		this(null);
	}

	/**
	 * プロパティファイルを読み込みます。<br>
	 * @param configFile 設定ファイルパス
	 * @return プロパティオブジェクト
	 */
	private Properties load(Path configFile) {
		Properties properties = new Properties();
		if (configFile == null || !Files.exists(configFile)) {
			throw new ApplicationException(String.format("エージェント設定ファイル(%s)が見つかりません。", configFile.toString()));
		}
		try (Reader reader = new InputStreamReader(new BufferedInputStream(Files.newInputStream(configFile)), StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (Throwable e) {
			throw new ApplicationException(String.format("エージェント設定ファイル(%s)の読み込みに失敗しました。", configFile.toString()), e);
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
	 * プロパティ必須定義チェックを行います。<br>
	 * @param key プロパティキー
	 * @throws ApplicationException 定義不正の場合にスローされます
	 */
	private void checkRequired(String key) throws ApplicationException {
		String raw = properties.getProperty(key);
		if (raw == null || raw.isBlank()) {
			throw new ApplicationException(String.format("環境定義キー(%s)が指定されていません。", key));
		}
	}

	/**
	 * プロパティ真偽型定義チェックを行います。<br>
	 * @param key プロパティキー
	 * @throws ApplicationException 定義不正の場合にスローされます
	 */
	private void checkBoolean(String key) throws ApplicationException {
		String raw = properties.getProperty(key);
		if (raw == null || raw.isBlank()) {
			return;
		}
		try {
			Boolean.parseBoolean(raw);
		} catch (Throwable e) {
			throw new ApplicationException(String.format("環境定義キー(%s)がboolean型にパースできません。", key));
		}
	}

	/**
	 * エージェント名を取得します。<br>
	 * @return エージェント名
	 */
	public String getName() {
		String key = "agent.name";
		String raw = properties.getProperty(key);
		checkRequired(key);
		return raw;
	}

	/**
	 * エージェント名を設定します。<br>
	 * @param name エージェント名
	 */
	public void setName(String name) {
		String key = "agent.name";
		properties.setProperty(key, name);
	}

	/**
	 * エージェントタイプ(gemini、claude、codex)を取得します。<br>
	 * @return エージェントタイプ(gemini、claude、codex)
	 */
	public String getType() {
		String key = "agent.type";
		String raw = properties.getProperty(key);
		checkRequired(key);
		return raw;
	}

	/**
	 * エージェントタイプを設定します。<br>
	 * @param type エージェントタイプ
	 */
	public void setType(String type) {
		String key = "agent.type";
		properties.setProperty(key, type);
	}

	/**
	 * エージェントモデルを取得します。<br>
	 * Gemini(gemini-3-flash-preview、gemini-3.1-flash-lite-preview、gemini-2.5-flash、gemini-2.5-flash-lite)<br>
	 * Claude(sonnet、opus、haiku)<br>
	 * Codex(gpt-5.1、gpt-5.4-mini、gpt-5.3-codex、gpt-5.2-codex、gpt-5.2、gpt-5.1-codex-max、gpt-5.1-codex-mini))<br>
	 * @return エージェントモデル
	 */
	public String getModel() {
		String key = "agent.model";
		String raw = properties.getProperty(key);
		//checkRequired(key);
		return raw;
	}

	/**
	 * エージェントモデルを設定します。
	 * @param model エージェントモデル
	 */
	public void setModel(String model) {
		String key = "agent.model";
		properties.setProperty(key, model);
	}

	/**
	 * エージェントリーダーフラグを取得します。<br>
	 * @return エージェントリーダーフラグ
	 */
	public boolean isLeader() {
		String key = "agent.leader";
		String raw = properties.getProperty(key);
		checkRequired(key);
		checkBoolean(key);
		return Boolean.parseBoolean(raw);
	}

	/**
	 * エージェントリーダーフラグを設定します。
	 * @param leader エージェントリーダーフラグ
	 */
	public void setLeader(boolean leader) {
		String key = "agent.leader";
		properties.setProperty(key, Boolean.toString(leader));
	}

	/**
	 * エージェント役割名を取得します。<br>
	 * @return エージェント役割名
	 */
	public String getRole() {
		String key = "agent.role";
		String raw = properties.getProperty(key);
		checkRequired(key);
		return raw;
	}

	/**
	 * エージェント役割名を設定します。
	 * @param role エージェント役割名
	 */
	public void setRole(String role) {
		String key = "agent.role";
		properties.setProperty(key, role);
	}

	/**
	 * エージェント性質を取得します。<br>
	 * @return エージェント性質
	 */
	public String getPersonality() {
		String key = "agent.personality";
		String raw = properties.getProperty(key);
		checkRequired(key);
		return raw;
	}

	/**
	 * エージェント性質を設定します。
	 * @param personality エージェント性質
	 */
	public void setPersonality(String personality) {
		String key = "agent.personality";
		properties.setProperty(key, personality);
	}

	/**
	 * エージェントCLI追加引数を取得します。<br>
	 * @return エージェントCLI追加引数
	 */
	public List<String> getExtraArgs() {
		String key = "agent.extra.args";
		String raw = properties.getProperty(key);
		//checkRequired(key);
		if (raw == null || raw.isBlank()) {
			return List.of();
		}
		return Arrays.asList(raw.split("\\s+"));
	}

	/**
	 * エージェントCLI追加引数を設定します。
	 * @param extraArgs エージェントCLI追加引数
	 */
	public void setExtraArgs(List<String> extraArgs) {
		String key = "agent.extra.args";
		if (extraArgs == null || extraArgs.isEmpty()) {
			properties.setProperty(key, "");
		} else {
			properties.setProperty(key, String.join(" ", extraArgs));
		}
	}
}
