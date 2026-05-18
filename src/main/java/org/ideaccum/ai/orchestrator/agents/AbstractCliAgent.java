package org.ideaccum.ai.orchestrator.agents;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.ideaccum.ai.orchestrator.agent.AgentAdapter;
import org.ideaccum.ai.orchestrator.agent.AgentConfig;
import org.ideaccum.ai.orchestrator.agent.AgentResult;
import org.ideaccum.ai.orchestrator.context.Config;
import org.ideaccum.ai.orchestrator.context.Context;
import org.ideaccum.ai.orchestrator.context.TokenUsage;
import org.ideaccum.ai.orchestrator.util.OutputUtils;
import org.ideaccum.ai.orchestrator.webui.AgentWebUIEventController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import tools.jackson.databind.JsonNode;

/**
 * CLIを子プロセスとして実行するエージェント基底クラスです。<br>
 * <p>
 * プロンプトは一時ファイル経由で標準入力に渡します。<br>
 * 構造化出力は行単位で解析し、CLI ごとの本文を抽出します。<br>
 * CLI種別固有の起動引数・出力解析設定はサブクラスが定数として保持します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者		更新内容
 * 2026/03/30	Kitagawa	新規作成
 *-->
 */
public abstract class AbstractCliAgent extends AgentAdapter {

	/** ロガーオブジェクト */
	protected final Logger log = LoggerFactory.getLogger(getClass());

	/** アプリケーションコンテキスト */
	private Context context;

	/** エージェント設定情報 */
	private AgentConfig agentConfig;

	/** CLI セッションID */
	private String sessionId;

	/** トークン使用量 */
	private TokenUsage tokenUsage;

	/**
	 * コンストラクタ<br>
	 * @param context アプリケーションコンテキスト
	 * @param agentConfig エージェント設定情報
	 */
	public AbstractCliAgent(Context context, AgentConfig agentConfig) {
		this.context = context;
		this.agentConfig = agentConfig;
		this.sessionId = null;
		this.tokenUsage = new TokenUsage();
	}

	/**
	 * 環境設定情報を取得します。<br>
	 * @return 環境設定情報
	 */
	@JsonIgnore
	public final Config getConfig() {
		return context == null ? null : context.getConfig();
	}

	/**
	 * アプリケーションコンテキストを取得します。<br>
	 * @return アプリケーションコンテキスト
	 */
	@JsonIgnore
	public final Context getContext() {
		return context;
	}

	/**
	 * エージェント環境設定情報を取得します。<br>
	 * @return エージェント環境設定情報
	 */
	@JsonIgnore
	public final AgentConfig getAgentConfig() {
		return agentConfig;
	}

	/**
	 * コマンド実行時の環境変数マップを生成します。<br>
	 * @return コマンド実行時の環境変数マップ
	 */
	protected abstract Map<String, String> buildEnvironmentMap();

	/**
	 * CLI起動時引数を返します。<br>
	 * @param sessionId セッションID(セッション継続起動時は非null、初回起動時はnull)
	 * @return CLI起動時引数
	 */
	protected abstract List<String> buildStartupCommandArgs(String sessionId);

	/**
	 * コマンド実行時の準備処理を行います。<br>
	 * @throws Throwable 準備処理で予期せぬエラーが発生した場合にスローされます
	 */
	protected abstract void prepare() throws Throwable;

	/**
	 * エージェントからの実行進捗レスポンスを抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return 実行進捗メッセージ
	 */
	protected abstract String lookupProgress(String response);

	/**
	 * エージェントレスポンスからセッションIDを抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return セッションID(取得できなかった場合はnullを返却)
	 */
	protected abstract String lookupSessionId(String response);

	/**
	 * エージェントレスポンスからコンテンツ内容を抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return コンテンツ内容(取得できなかった場合はnullを返却)
	 */
	protected abstract String lookupContent(String response);

	/**
	 * エージェントレスポンスからトークン使用量を抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return トークン使用量(取得できなかった場合はnullを返却)
	 */
	protected abstract TokenUsage lookupUsage(String response);

	/**
	 * エージェント名を取得します。<br>
	 * @return エージェント名
	 * @see org.ideaccum.ai.orchestrator.agent.Agent#getName()
	 */
	@Override
	public String getName() {
		return agentConfig.getName();
	}

	/**
	 * エージェント種類を取得します。<br>
	 * @return エージェント種類
	 * @see org.ideaccum.ai.orchestrator.agent.Agent#getType()
	 */
	@Override
	public String getType() {
		return agentConfig.getType();
	}

	/**
	 * エージェントモデルを取得します。<br>
	 * @return エージェントモデル
	 * @see org.ideaccum.ai.orchestrator.agent.Agent#getModel()
	*/
	@Override
	public String getModel() {
		return agentConfig.getModel();
	}

	/**
	 * エージェント役割名を取得します。<br>
	 * @return エージェント役割名
	 * @see org.ideaccum.ai.orchestrator.agent.Agent#getRole()
	 */
	@Override
	public String getRole() {
		return agentConfig.getRole();
	}

	/**
	 * エージェント動作性質を取得します。<br>
	 * @return エージェント動作性質
	 * @see org.ideaccum.ai.orchestrator.agent.Agent#getPersonality()
	 */
	@Override
	public String getPersonality() {
		return agentConfig.getPersonality();
	}

	/**
	 * エージェントセッションIDを取得します。<br>
	 * @return エージェントセッションID
	 * @see org.ideaccum.ai.orchestrator.agent.Agent#getSessionId()
	 */
	@Override
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
	 * エージェントがリーダーエージェントであるか判定します。<br>
	 * @return リーダーエージェントである場合にtrueを返却
	 * @see org.ideaccum.ai.orchestrator.agent.Agent#isLeader()
	 */
	@Override
	public boolean isLeader() {
		return agentConfig.isLeader();
	}

	/**
	 * エージェントに対してプロンプトを実行させます。<br>
	 * @param prompt プロンプト文字列
	 * @return プロンプト実行結果
	 * @throws Throwable プロンプト実行時に予期せぬエラーが発生した場合にスローされます
	 * @see org.ideaccum.ai.orchestrator.agent.Agent#execute(java.lang.String)
	 */
	@Override
	public AgentResult execute(String prompt) throws Throwable {
		/*
		 * エージェントプロンプトテンポラリファイル生成
		 */
		Path promptTemporaryFile = Files.createTempFile("prompt-" + getName(), ".txt");
		Files.writeString(promptTemporaryFile, prompt, StandardCharsets.UTF_8);

		try {
			/*
			 * 事前準備処理
			 */
			prepare();

			/*
			 * コマンドラインリスト構築
			 */
			List<String> cmdList = new LinkedList<>();
			cmdList.add(context.getConfig().getAgentCliCommand(agentConfig.getType()));
			cmdList.addAll(buildStartupCommandArgs(sessionId));
			cmdList.addAll(agentConfig.getExtraArgs());

			/*
			 * Windows環境変数展開
			 */
			Map<String, String> windowsEnv = System.getenv();
			List<String> cmdListFinal = new LinkedList<>();
			for (String cmd : cmdList) {
				for (Map.Entry<String, String> e : windowsEnv.entrySet()) {
					String key = "%" + e.getKey() + "%";
					String val = e.getValue() != null ? e.getValue() : "";
					cmd = cmd.replace(key, val);
				}
				cmdListFinal.add(cmd);
			}

			/*
			 * コマンドプロセスビルダー生成
			 */
			Map<String, String> environment = buildEnvironmentMap();
			ProcessBuilder builder = new ProcessBuilder(cmdListFinal);
			builder.directory(context.getConfig().getApplicationProjectPath(context.getProjectName()).toFile());
			builder.redirectErrorStream(true);
			builder.redirectInput(promptTemporaryFile.toFile());
			if (environment != null) {
				builder.environment().putAll(environment);
			}

			/*
			 * コマンドプロセス実行
			 */
			long start = System.currentTimeMillis();
			Process process = builder.start();

			/*
			 * コマンドレスポンス処理
			 */
			StringBuilder result = new StringBuilder();
			ExecutorService executor = Executors.newSingleThreadExecutor();
			executor.submit(() -> {
				try (InputStream is = process.getInputStream(); InputStreamReader isr = new InputStreamReader(is); BufferedReader reader = new BufferedReader(isr)) {
					for (String response; (response = reader.readLine()) != null;) {

						/*
						 * 生データダンプ
						 */
						OutputUtils.outputResponseDump(context, getName(), response);

						/*
						 * 実行進捗メッセージ取得
						 */
						String progress = lookupProgress(response);
						AgentWebUIEventController.instance().publishMessage(getName(), progress);

						/*
						 * レスポンス内容がJSONでない場合はスキップ
						 */
						try {
							JsonNode node = MAPPER.readTree(response);
							if (!node.isObject() && !node.isArray()) {
								continue;
							}
						} catch (Throwable e) {
							continue;
						}

						/*
						 * セッションID更新
						 */
						String sessionId = lookupSessionId(response);
						if (sessionId != null && !sessionId.isBlank()) {
							this.sessionId = sessionId;
						}

						/*
						 * トークン使用量更新
						 */
						TokenUsage tokenUsage = lookupUsage(response);
						if (tokenUsage != null) {
							this.tokenUsage.add(tokenUsage);
						}

						/*
						 * コンテンツ取得
						 */
						String content = lookupContent(response);
						if (content != null) {
							OutputUtils.outputConversationDump(context, getName(), content);
							result.append(content);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});

			boolean finished = process.waitFor(context.getConfig().getAgentTimeout(), TimeUnit.SECONDS);
			executor.shutdown();
			if (!finished) {
				process.destroyForcibly();
				return AgentResult.error(getName(), "タイムアウト(" + context.getConfig().getAgentTimeout() + "秒)");
			}

			// プロセス終了後もリーダースレッドが最終チャンクを result に書き込み中の場合があるため完了を待機する
			executor.awaitTermination(10, TimeUnit.SECONDS);

			long elapsed = System.currentTimeMillis() - start;
			return AgentResult.success(getName(), result.toString(), elapsed, tokenUsage);
		} catch (Throwable e) {
			Thread.currentThread().interrupt();
			return AgentResult.error(getName(), e.getMessage());
		} finally {
			try {
				Files.deleteIfExists(promptTemporaryFile);
			} catch (Throwable e) {
				// テンポラリファイル削除例外は無視
			}
		}
	}
}
