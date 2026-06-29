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
import org.ideaccum.ai.orchestrator.util.StringUtils;

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
 * 2026/05/26	Kitagawa	トークン集計不具合を修正
 *-->
 */
public class ClaudeCliAgent extends AbstractCliAgent {

	/**
	 * コンストラクタ<br>
	 * @param context アプリケーションコンテキスト
	 * @param agentConfig エージェント設定情報
	 */
	public ClaudeCliAgent(Context context, AgentConfig agentConfig) {
		super(context, agentConfig);
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
		list.add(getContext().getAgentRootPath().normalize().toString());

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
		Files.createDirectories(getContext().getAgentRootPath().resolve(AGENT_CONFIG_DIR_CLAUDE));
	}

	/**
	 * エージェントからのエラーレスポンスを抽出します。<br>
	 * このレスポンスがあった場合は後続処理は中断されます。<br>
	 * @param response エージェントレスポンス
	 * @return エラーメッセージ
	 * @see org.ideaccum.ai.orchestrator.agents.AbstractCliAgent#lookupError(java.lang.String)
	 */
	protected String lookupError(String response) {
		if (response == null || response.isBlank()) {
			return null;
		}
		String result = null;
		try {
			if (StringUtils.isJSON(response)) {
				return result;
			}

			if (response.indexOf("Not logged in") >= 0) {
				result = "認証が行われていません。";
			}

			return result;
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupErrorで予期せぬエラーが発生しました。", e);
			return response;
		}
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
			if (!StringUtils.isJSON(response)) {
				return result;
			}

			JsonNode node = JSON.readTree(response);
			String type = node.path("type").asString();

			if ("system".equals(type)) {
				String subtype = node.path("subtype").asString();
				if ("thinking_tokens".equals(subtype)) {
					result = "エージェント思考中です。";
				}
				if ("task_updated".equals(subtype)) {
					result = "内部タスクステータスを更新しました。";
				}
				if ("init".equals(subtype)) {
					result = "スレッドを初期化しました。";
				}
				if ("task_started".equals(subtype)) {
					String description = node.path("description").asString();
					result = "サブタスク開始 : " + description;
				}
				if ("task_progress".equals(subtype)) {
					String description = node.path("description").asString();
					result = "サブタスク進捗 : " + description;
				}
				if ("task_notification".equals(subtype)) {
					String status = node.path("status").asString();
					String summary = node.path("summary").asString();
					result = "サブタスク(" + status + ") : " + summary;
				}
				if ("status".equals(subtype)) {
					String status = node.path("status").asString();
					String compactResult = node.path("compact_result").asString();
					if ("compacting".equals(status)) {
						result = "コンテキストを圧縮しています。";
					} else if ("success".equals(compactResult)) {
						result = "コンテキストの圧縮が完了しました。";
					} else {
						result = "コンテキスト圧縮関連タスク処理中です。";
					}
				}
				if ("compact_boundary".equals(subtype)) {
					long preTokens = node.path("compact_metadata").path("pre_tokens").asLong(0);
					long postTokens = node.path("compact_metadata").path("post_tokens").asLong(0);
					result = "コンテキスト圧縮 : " + preTokens + " → " + postTokens + "トークン。";
				}
				if ("api_retry".equals(subtype)) {
					int attempt = node.path("attempt").asInt(0);
					int maxRetries = node.path("max_retries").asInt(0);
					long delayMs = node.path("retry_delay_ms").asLong(0);
					String error = node.path("error").asString("");
					int errorStatus = node.path("error_status").asInt(0);
					result = "APIリトライ中 (" + attempt + "/" + maxRetries + "回) : " + errorStatus + " " + error + " - " + (delayMs / 1000) + "秒後に再試行。";
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
					result = "エージェントメッセージがレスポンスされました。";
				}
				if ("thinking".equals(messageContentType)) {
					String thinking = node.path("message").path("content").path(0).path("thinking").asString();
					result = "考慮中 : " + thinking;
				}
				if ("tool_use".equals(messageContentType)) {
					String toolName = node.path("message").path("content").path(0).path("name").asString();
					result = "ツール実行中 : " + toolName;
				}
			}
			if ("user".equals(type)) {
				result = "ユーザー指示内容を確認中です。";
			}

			// メッセージフック漏れ確認用エラーコンソール出力
			if (result == null) {
				log.warn("[Internal] ClaudeCliAgentのlookupProgressでメッセージフック漏れがあります : " + node.toString());
				result = node.toString();
			}

			return result;
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupProgressで予期せぬエラーが発生しました。", e);
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
			JsonNode node = JSON.readTree(response);

			// content内容を取得
			String result = node.path("session_id").asString();

			return result;
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupSessionIdで予期せぬエラーが発生しました。", e);
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
			JsonNode node = JSON.readTree(response);

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
			log.error(response);
			log.error("lookupContentで予期せぬエラーが発生しました。", e);
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
			JsonNode node = JSON.readTree(response);

			// type=resultのみ対象とする(assistant、user、systemはスキップ)
			if (!"result".equals(node.path("type").asString())) {
				return null;
			}

			// modelUsageが存在しない場合はスキップ(error_during_execution等）
			JsonNode modelUsageNode = node.path("modelUsage");
			if (modelUsageNode.isMissingNode() || modelUsageNode.isNull()) {
				return null;
			}

			// 全モデルを合算(例: claude-sonnet-4-6[1m] + claude-haiku-4-5-20251001)
			long inputTokens = 0L;
			long outputTokens = 0L;
			for (JsonNode modelNode : modelUsageNode) {
				inputTokens += modelNode.path("inputTokens").asLong(0L);
				outputTokens += modelNode.path("outputTokens").asLong(0L);
			}

			TokenUsage result = new TokenUsage(inputTokens, outputTokens);

			return result;
		} catch (Throwable e) {
			log.error(response);
			log.error("lookupUsageで予期せぬエラーが発生しました。", e);
			return null;
		}
	}
}
