package org.ideaccum.ai.orchestrator.agents;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.ideaccum.ai.orchestrator.agent.AgentConfig;
import org.ideaccum.ai.orchestrator.context.Context;
import org.ideaccum.ai.orchestrator.context.TokenUsage;

import tools.jackson.databind.JsonNode;

/**
 * Claude Code CLI を子プロセスとして実行するエージェントクラスです。<br>
 * <p>
 * Claude Code CLI 固有の起動引数・出力解析設定を定数として保持します。<br>
 * エージェントクラスインスタンスはセッションごとに単一のインスタンスを想定したクラス設計になっています。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者		更新内容
 * 2026/03/31	Kitagawa	新規作成
 *-->
 */
public class ClaudeCliAgent extends AbstractCliAgent {

	/** トークン使用量ルックアップID */
	private String tokenUsageId;

	/**
	 * コンストラクタ<br>
	 * @param context アプリケーションコンテキスト
	 * @param agentConfig エージェント設定情報
	 */
	public ClaudeCliAgent(Context context, AgentConfig agentConfig) {
		super(context, agentConfig);
		this.tokenUsageId = null;
	}

	/**
	 * コマンド実行時の環境変数マップを生成します。<br>
	 * @return コマンド実行時の環境変数マップ
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#buildEnvironmentMap()
	 */
	@Override
	protected Map<String, String> buildEnvironmentMap() {
		Map<String, String> env = new HashMap<>();
		//env.put("CLAUDE_CONFIG_DIR", getConfig().getApplicationWorkspacePath().resolve(".claude").normalize().toString());
		return env;
	}

	/**
	 * CLI起動時引数を返します。<br>
	 * @param sessionId セッションID(セッション継続起動時は非null、初回起動時はnull)
	 * @return CLI起動時引数
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#buildStartupCommandArgs(java.lang.String)
	 */
	@Override
	protected List<String> buildStartupCommandArgs(String sessionId) {
		List<String> list = new ArrayList<>();
		// 読み取り、編集ディレクトリ
		list.add("--add-dir");
		list.add(getConfig().getApplicationProjectPath(getContext().getProjectName()).normalize().toString());

		// 直近セッション継続
		//list.add("--continue");

		// すべての権限プロンプトスキップ
		//list.add("--dangerously-skip-permissions");

		// プリントモード時の入力形式(text、stream-json)
		list.add("--input-format");
		list.add("text");

		// エージェントモデル指定
		if (getModel() != null && !getModel().isBlank()) {
			list.add("--model");
			list.add(getModel());
		}

		// プリントモード時の出力形式(text、json、stream-json)
		list.add("--output-format");
		list.add("stream-json");

		// 権限モード(default、acceptEdits、plan、auto、dontAsk、bypassPermissions) → settings.json側で指定
		//list.add("--permission-mode");
		//list.add("bypassPermissions");

		// 非対話モード
		list.add("--print");

		// セッション再開
		if (sessionId != null && !sessionId.isBlank()) {
			list.add("--resume");
			list.add(sessionId);
		}

		// 新規セッション指定
		if (sessionId == null || sessionId.isBlank()) {
			list.add("--session-id");
			list.add(UUID.randomUUID().toString());
		}

		// 詳細ログ有効化
		list.add("--verbose");

		return list;
	}

	/**
	 * コマンド実行時の準備処理を行います。<br>
	 * @throws Throwable 準備処理で予期せぬエラーが発生した場合にスローされます
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#prepare()
	 */
	@Override
	protected void prepare() throws Throwable {
		Files.createDirectories(getConfig().getApplicationProjectPath(getContext().getProjectName()).resolve(".claude"));
	}

	/**
	 * エージェントからの実行進捗レスポンスを抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return 実行進捗メッセージ
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#lookupProgress(java.lang.String)
	 */
	protected String lookupProgress(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		String result = null;
		try {
			JsonNode node = MAPPER.readTree(response);
			String type = node.path("type").asString();

			if ("system".equals(type)) {
				String subtype = node.path("subtype").asString();
				if ("init".equals(subtype)) {
					result = "スレッドを初期化しました。";
				}
			}
			if ("result".equals(type)) {
				result = "エージェント処理が完了しました。";
			}
			if ("rate_limit_event".equals(type)) {
				String rateLimitInfoStatus = node.path("rate_limit_info").path("status").asString();
				result = "モデル利用制限ステータスは " + rateLimitInfoStatus + "です。";
			}
			if ("assistant".equals(type)) {
				String messageContentType = node.path("message").path("content").path(0).path("type").asString();
				if ("text".equals(messageContentType)) {
					result = "エージェントメッセージがレスポンスされました。。";
				}
				if ("thinking".equals(messageContentType)) {
					String thinking = node.path("message").path("content").path(0).path("thinking").asString();
					result = "考慮中: " + thinking;
				}
			}

			// メッセージフック漏れ確認用エラーコンソール出力
			if (result == null) {
				System.err.println("[WARN] " + node.toString());
				result = node.toString();
			}

			return result;
		} catch (Throwable e) {
			System.err.println("[ERROR] " + response);
			return response;
		}
	}

	/**
	 * エージェントレスポンスからセッションIDを抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return セッションID(取得できなかった場合はnullを返却)
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#lookupSessionId(java.lang.String)
	 */
	@Override
	protected String lookupSessionId(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		try {
			JsonNode node = MAPPER.readTree(response);

			// content内容を取得
			String result = node.path("session_id").asString();

			return result;
		} catch (Throwable e) {
			return null;
		}
	}

	/**
	 * エージェントレスポンスからコンテンツ内容を抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return コンテンツ内容(取得できなかった場合はnullを返却)
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#lookupContent(java.lang.String)
	 */
	@Override
	protected String lookupContent(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		try {
			JsonNode node = MAPPER.readTree(response);

			// typeがassistant以外は解析スキップ
			if (!"assistant".equals(node.path("type").asString())) {
				return null;
			}

			// content内容を取得
			StringBuilder contentBuilder = new StringBuilder();
			for (int i = 0; i <= node.path("message").path("content").size() - 1; i++) {
				String content = node.path("message").path("content").path(i).path("text").asString();
				if (content != null && !content.isEmpty()) {
					contentBuilder.append(content).append("\n");
				}
			}
			String result = contentBuilder.toString();

			if (result != null && !result.isEmpty()) {
				return result;
			} else {
				return null;
			}
		} catch (Throwable e) {
			return null;
		}
	}

	/**
	 * エージェントレスポンスからトークン使用量を抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return トークン使用量(取得できなかった場合はnullを返却)
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#lookupUsage(java.lang.String)
	 */
	@Override
	protected TokenUsage lookupUsage(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		try {
			JsonNode node = MAPPER.readTree(response);

			if (node.path("message").path("id").asString().equals(tokenUsageId)) {
				return null;
			}

			// トークンノードがない場合はスキップ
			if (false //
					|| node.path("message").path("usage").path("input_tokens").isMissingNode() //
					|| node.path("message").path("usage").path("cache_creation_input_tokens").isMissingNode() //
					|| node.path("message").path("usage").path("cache_read_input_tokens").isMissingNode() //
					|| node.path("message").path("usage").path("output_tokens").isMissingNode() //
			) {
				return null;
			}

			// トークン情報取得
			long inputToken = node.path("message").path("usage").path("input_tokens").asLong();
			long cacheCreationInputTokens = node.path("message").path("usage").path("cache_creation_input_tokens").asLong();
			long cacheReadInputTokens = node.path("message").path("usage").path("cache_read_input_tokens").asLong();
			long outputToken = node.path("message").path("usage").path("output_tokens").asLong();

			TokenUsage result = new TokenUsage(inputToken + cacheCreationInputTokens + cacheReadInputTokens, outputToken, 0);
			tokenUsageId = node.path("message").path("id").asString();

			return result;
		} catch (Throwable e) {
			return null;
		}
	}
}
