package org.ideaccum.ai.orchestrator.context;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.exception.ApplicationException;

/**
 * 環境設定情報管理クラスです。<br>
 * <p>
 * 環境設定情報ファイルからプロパティを読み込み、アプリケーション内で使用する環境設定情報を提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者		更新内容
 * 2026/03/30	Kitagawa	新規作成
 *-->
 */
public class Config implements Constants {

	/** 環境設定情報ファイルパス */
	private Path configFilePath;

	/** プロパティオブジェクト */
	private Properties properties;

	/**
	 * コンストラクタ<br>
	 * @param configFile 環境設定情報プロパティパス
	 */
	public Config(Path configFile) {
		this.configFilePath = configFile;
		load(configFile);
	}

	/**
	 * 環境設定情報ファイルパスを取得します。<br>
	 * @return 環境設定情報ファイルパス
	 */
	public Path getConfigFilePath() {
		return configFilePath;
	}

	/**
	 * プロパティリソースを読み込みます。<br>
	 * 指定ファイルが存在しない場合はクラスパス上のデフォルト設定({@code /default-config.properties})を読み込み、
	 * 同時に指定パスへコピーして次回以降はファイルから読み込めるようにします。<br>
	 */
	private void load(Path configFile) {
		if (configFile == null) {
			throw new ApplicationException("環境設定情報ファイルパスが指定されていません");
		}
		if (!Files.exists(configFile)) {
			Properties defaults = new Properties();
			try (InputStream is = Config.class.getResourceAsStream(DEFALUT_CONFIG_FILE)) {
				defaults.load(is);
			} catch (IOException e) {
				throw new ApplicationException("デフォルト環境設定情報ファイルの読み込みに失敗しました", e);
			}
			try {
				if (configFile.getParent() != null) {
					Files.createDirectories(configFile.getParent());
				}
				try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile.toFile()), StandardCharsets.UTF_8))) {
					defaults.store(writer, null);
				}
			} catch (IOException e) {
				throw new ApplicationException("デフォルト環境設定情報ファイルの書き出しに失敗しました", e);
			}
		}
		properties = new Properties();
		try (Reader reader = new InputStreamReader(new BufferedInputStream(Files.newInputStream(configFile)), StandardCharsets.UTF_8)) {
			properties.load(reader);
		} catch (Throwable e) {
			throw new ApplicationException(String.format("環境設定情報ファイル(%s)の読み込みに失敗しました", configFile), e);
		}
	}

	/**
	 * 環境設定情報をファイルに保存します。<br>
	 * コメント行を保持するためテキスト行単位で更新します。<br>
	 * @throws IOException 保存に失敗した場合にスローされます
	 */
	public void save() throws IOException {
		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFilePath.toFile()), StandardCharsets.UTF_8))) {
			properties.store(writer, "");
		}
	}

	/**
	 * API応答用に編集可能な設定項目をマップ形式で返却します。<br>
	 * @return 設定項目マップ
	 */
	public Map<String, Object> toConfigMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("theme", properties.getProperty("application.theme", ""));
		map.put("repositoryPath", properties.getProperty("application.repository.path", ""));
		map.put("webuiPort", properties.getProperty("application.webui.port", ""));
		map.put("webuiConnection", properties.getProperty("application.webui.connection", ""));
		map.put("agentTimeout", properties.getProperty("agent.timeout", ""));
		map.put("cliClaudeCommand", properties.getProperty("agent.cli.claude-cli.command", ""));
		map.put("cliGeminiCommand", properties.getProperty("agent.cli.gemini-cli.command", ""));
		map.put("cliCodexCommand", properties.getProperty("agent.cli.codex-cli.command", ""));
		map.put("cliCopilotCommand", properties.getProperty("agent.cli.copilot-cli.command", ""));
		return map;
	}

	/**
	 * プロパティ必須定義チェックを行います。<br>
	 * @param key プロパティキー
	 * @throws ApplicationException 定義不正の場合にスローされます
	 */
	private void checkRequired(String key) throws ApplicationException {
		String raw = properties.getProperty(key);
		if (raw == null || raw.isBlank()) {
			throw new ApplicationException(String.format("環境定義キー(%s)が指定されていません", key));
		}
	}

	/**
	 * 正規表現パターン型定義チェックを行います。<br>
	 * @param key プロパティキー
	 * @throws ApplicationException 定義不正の場合にスローされます
	 */
	@SuppressWarnings("unused")
	private void checkRegexp(String key) throws ApplicationException {
		String raw = properties.getProperty(key);
		if (raw == null || raw.isBlank()) {
			return;
		}
		try {
			Pattern.compile(raw);
		} catch (Throwable e) {
			throw new ApplicationException(String.format("環境定義キー(%s)が正規表現パターンにパースできません", key));
		}
	}

	/**
	 * プロパティ数値型定義チェックを行います。<br>
	 * @param key プロパティキー
	 * @throws ApplicationException 定義不正の場合にスローされます
	 */
	private void checkInteger(String key) throws ApplicationException {
		String raw = properties.getProperty(key);
		if (raw == null || raw.isBlank()) {
			return;
		}
		try {
			Integer.parseInt(raw);
		} catch (Throwable e) {
			throw new ApplicationException(String.format("環境定義キー(%s)がint型にパースできません", key));
		}
	}

	/**
	 * プロパティ真偽値型定義チェックを行います。<br>
	 * @param key プロパティキー
	 * @throws ApplicationException 定義不正の場合にスローされます
	 */
	@SuppressWarnings("unused")
	private void checkBoolean(String key) throws ApplicationException {
		String raw = properties.getProperty(key);
		if (raw == null || raw.isBlank()) {
			return;
		}
		try {
			Boolean.parseBoolean(raw);
		} catch (Throwable e) {
			throw new ApplicationException(String.format("環境定義キー(%s)がboolean型にパースできません", key));
		}
	}

	/**
	 * アプリケーションテーマを取得します。<br>
	 * @return アプリケーションテーマ
	 */
	public Theme getApplicationTheme() {
		String key = "application.theme";
		String raw = properties.getProperty(key);
		checkRequired(key);
		return Theme.of(raw);
	}

	/**
	 * アプリケーションテーマを設定します。<br>
	 * @param theme テーマ文字列(dark/light)
	 */
	public void setApplicationTheme(String theme) {
		String key = "application.theme";
		properties.setProperty(key, theme);
	}

	/**
	 * ワークスペースリポジトリパスを取得します。<br>
	 * @return ワークスペースリポジトリパス
	 */
	public Path getApplicationRepositoryPath() {
		String key = "application.repository.path";
		String raw = properties.getProperty(key);
		checkRequired(key);
		return Path.of(raw);
	}

	/**
	 * ワークスペースリポジトリパスを設定します。<br>
	 * @param path リポジトリパス文字列
	 */
	public void setApplicationRepositoryPath(String path) {
		String key = "application.repository.path";
		properties.setProperty(key, path);
	}

	/**
	 * Webインタフェースサーバーポートを取得します。<br>
	 * @return Webインタフェースサーバーポート
	 */
	public int getApplicationWebuiPort() {
		String key = "application.webui.port";
		String raw = properties.getProperty(key);
		checkRequired(key);
		checkInteger(key);
		return Integer.parseInt(raw);
	}

	/**
	 * Webインタフェースサーバーポートを設定します。<br>
	 * @param port ポート番号
	 */
	public void setApplicationWebuiPort(int port) {
		String key = "application.webui.port";
		properties.setProperty(key, String.valueOf(port));
	}

	/**
	 * Webインタフェースサーバー同時接続数を取得します。<br>
	 * @return Webインタフェースサーバー同時接続数
	 */
	public int getApplicationWebuiConnection() {
		String key = "application.webui.connection";
		String raw = properties.getProperty(key);
		checkRequired(key);
		checkInteger(key);
		return Integer.parseInt(raw);
	}

	/**
	 * Webインタフェースサーバー同時接続数を設定します。<br>
	 * @param connection 同時接続数
	 */
	public void setApplicationWebuiConnection(int connection) {
		String key = "application.webui.connection";
		properties.setProperty(key, String.valueOf(connection));
	}

	/**
	 * エージェントタイムアウト秒数を取得します。<br>
	 * @return エージェントタイムアウト秒数
	 */
	public int getAgentTimeout() {
		String key = "agent.timeout";
		String raw = properties.getProperty(key);
		checkRequired(key);
		checkInteger(key);
		return Integer.parseInt(raw);
	}

	/**
	 * エージェントタイムアウト秒数を設定します。<br>
	 * @param timeout タイムアウト秒数
	 */
	public void setAgentTimeout(int timeout) {
		String key = "agent.timeout";
		properties.setProperty(key, String.valueOf(timeout));
	}

	/**
	 * CLI起動コマンド文字列を取得します。<br>
	 * @param type CLIタイプ
	 * @return CLI起動コマンド文字列
	 */
	public String getAgentCliCommand(String type) {
		String key = String.format("agent.cli.%s.command", type);
		String raw = properties.getProperty(key);
		checkRequired(key);
		return raw;
	}

	/**
	 * CLI起動コマンド文字列を設定します。<br>
	 * @param type CLIタイプ
	 * @param command コマンド文字列
	 */
	public void setAgentCliCommand(String type, String command) {
		String key = String.format("agent.cli.%s.command", type);
		properties.setProperty(key, command);
	}

	/**
	 * プロジェクトワークスペースパスを取得します。<br>
	 * @return プロジェクトワークスペースパス
	 */
	public Path getApplicationProjectPath(String projectName) {
		return Path.of(getApplicationRepositoryPath().toString(), projectName);
	}

	/**
	 * プロジェクト設定ファイルパスを取得します。<br>
	 * @param projectName プロジェクト名
	 * @return プロジェクト設定ファイルパス({@code .orchestrator/project.properties})
	 */
	public Path getApplicationProjectPropertiesPath(String projectName) {
		return getApplicationAgentsPath(projectName).getParent().resolve("project.properties");
	}

	/**
	 * プロジェクトワークスペースパス配下のプロジェクト名一覧を取得します。<br>
	 * {@code .orchestrator/project.properties}が存在するディレクトリのみをプロジェクトとして返却します。<br>
	 * @return プロジェクトワークスペースパス配下のプロジェクト名
	 */
	public List<String> getApplicationProjectNames() {
		Path repoPath = getApplicationRepositoryPath();
		if (!Files.isDirectory(repoPath)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.list(repoPath)) {
			return stream //
					.filter(Files::isDirectory) //
					.filter(dir -> Files.exists(getApplicationProjectPropertiesPath(dir.getFileName().toString()))) //
					.map(file -> file.getFileName().toString()) //
					.sorted() //
					.collect(Collectors.toList()) //
			;
		} catch (IOException e) {
			return List.of();
		}
	}

	/**
	 * アプリケーションログパスを取得します。<br>
	 * @param projectName プロジェクト名
	 * @return アプリケーションログパス
	 */
	public Path getApplicationLogPath(String projectName) {
		//String key = "application.logs.path";
		//String raw = properties.getProperty(key);
		//checkRequired(key);
		//return Path.of(getApplicationRepositoryPath().toString(), projectName, raw);
		return Path.of(getApplicationRepositoryPath().toString(), projectName, ORCHESTRATOR_LOGS_PATH);
	}

	/**
	 * 動作エージェント定義配置パスを取得します。<br>
	 * @param projectName プロジェクト名
	 * @return 動作エージェント定義配置パス
	 */
	public Path getApplicationAgentsPath(String projectName) {
		//String key = "application.agents.path";
		//String raw = properties.getProperty(key);
		//checkRequired(key);
		//return Path.of(getApplicationRepositoryPath().toString(), projectName, raw);
		return Path.of(getApplicationRepositoryPath().toString(), projectName, ORCHESTRATOR_AGENTS_PATH);
	}

	/**
	 * エージェント合意完了キーワードを取得します。<br>
	 * @return エージェント合意完了キーワード
	 */
	public String getAgentFinalizeKeyword() {
		//String key = "agent.finalize.keyword";
		//String raw = properties.getProperty(key);
		//checkRequired(key);
		//return raw;
		return AGENT_FINALIZE_KEYWORD;
	}

	/**
	 * 処理中断キーワードを取得します。<br>
	 * @return 処理中断キーワード
	 */
	public String getAgentStopKeyword() {
		//String key = "agent.stop.keyword";
		//String raw = properties.getProperty(key);
		//checkRequired(key);
		//return raw;
		return AGENT_INTERRRUPTE_KEYWORD;
	}

	/**
	 * タスクディスパッチキーワードパターンを取得します。<br>
	 * @return タスクディスパッチキーワードパターン
	 */
	public Pattern getAgentDispatchKeywordPattern() {
		//String key = "agent.dispacth.keyword.pattern";
		//String raw = properties.getProperty(key);
		//checkRequired(key);
		//checkRegexp(key);
		//return Pattern.compile(raw);
		return Pattern.compile(AGENT_DISPATH_REGEX);
	}

	/**
	 * 会話ログファイルを取得します。<br>
	 * @param projectName プロジェクト名
	 * @return 会話ログファイル
	 */
	public Path getAgentConversationLogfile(String projectName) {
		//String key = "agent.conversation.logfile";
		//String raw = properties.getProperty(key);
		//checkRequired(key);
		//return Path.of(getApplicationRepositoryPath().toString(), projectName, raw);
		return Path.of(getApplicationRepositoryPath().toString(), projectName, ORCHESTRATOR_CONVERSATION_FILE);
	}

	/**
	 * セッションストアファイルを取得します。<br>
	 * @param projectName プロジェクト名
	 * @return セッションストアファイル
	 */
	public Path getAgentSessionStore(String projectName) {
		//String key = "agent.session.storefile";
		//String raw = properties.getProperty(key);
		//checkRequired(key);
		//return Path.of(getApplicationRepositoryPath().toString(), projectName, raw);
		return Path.of(getApplicationRepositoryPath().toString(), projectName, ORCHESTRATOR_SESSIONS_FILE);
	}
}
