package org.ideaccum.ai.orchestrator.context;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agent.Agent;

/**
 * エージェントに発行するプロンプトを生成するクラスです。<br>
 * <p>
 * Velocityテンプレートエンジンを使用して、エージェントに発行するプロンプトを生成します。<br>
 * プロンプト生成の際には、コンテキストオブジェクトからタスク内容や会話ログ、エージェント情報などを取得し、テンプレートに埋め込んでプロンプトを生成します。<br>
 * </p>
 * 
 * @author Kitagawa<br>
 * 
 *<!--
 * 更新日		更新者			更新内容
 * 2026/04/08	Kitagawa		新規作成
 *-->
 */
public class PromptFactory implements Constants {

	/** Velocityエンジン */
	private VelocityEngine velocityEngine;

	/** コンテキストオブジェクト */
	private Context context;

	/**
	 * コンストラクタ<br>
	 * @param context コンテキストオブジェクト
	 */
	public PromptFactory(Context context) {
		super();
		this.velocityEngine = new VelocityEngine();
		this.velocityEngine.setProperty("resource.loader", "class");
		this.velocityEngine.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
		this.velocityEngine.init();
		this.context = context;
	}

	/**
	 * テンプレートとコンテキストをもとにコンテンツを構築します。<br>
	 * @param velocityContext Velocityコンテキスト
	 * @param templateFile テンプレートファイル
	 * @return 生成されたコンテンツ
	 */
	private String build(VelocityContext velocityContext, String templateFile) {
		StringWriter writer = new StringWriter();
		velocityEngine.mergeTemplate(templateFile, StandardCharsets.UTF_8.name(), velocityContext, writer);
		return writer.toString();
	}

	/**
	 * エージェントに対する会話開始プロンプトを生成します。<br>
	 * プロンプト内容はエージェント役割、会話履歴などから動的に生成されます。<br>
	 * @param agent エージェントオブジェクト
	 * @return プロンプトコンテンツ
	 */
	public String createStart(Agent agent) {
		/*
		 * Velocityコンテキスト生成
		 */
		VelocityContext velocityContext = new VelocityContext();
		velocityContext.put("context", context);
		velocityContext.put("agent", agent);
		velocityContext.put("config", context.getConfig());

		/*
		 * エージェントセッション取得
		 */
		Session session = context.getSessions().get(agent.getName());

		/*
		 * エージェント向け会話履歴生成
		 */
		String history;
		if (session == null) {
			// セッションが存在しない場合は全会話履歴設定
			history = context.getConversations().getAllHistory();
			if (history == null || history.isBlank()) {
				// 会話ログが本当に存在しない場合
				history = AGENT_PROMPT_HISTORY_NO_LOG;
			}
		} else {
			// セッションが存在する場合は未読会話履歴設定
			history = context.getConversations().getUnreadHistory(agent);
			if (history == null || history.isBlank()) {
				history = AGENT_PROMPT_HISTORY_SESSION_CONTINUE;
			}
		}
		velocityContext.put("history", history);

		/*
		 * エージェント向けプロンプト生成
		 */
		StringBuilder builder = new StringBuilder();
		if (session == null) {
			builder.append(build(velocityContext, RESOURCE_TEMPLATE_START_PROMPT));
			if (agent.isLeader()) {
				builder.append("\n");
				builder.append(build(velocityContext, RESOURCE_TEMPLATE_LEADER_PROMPT));
			}
		} else {
			builder.append(build(velocityContext, RESOURCE_TEMPLATE_CONTINUE_PROMPT));
		}

		return builder.toString();
	}

	/**
	 * エージェントに対するリトライプロンプトを生成します。<br>
	 * キーワード未出力時にエージェントへ再指示するためのプロンプトを生成します。<br>
	 * @param agent エージェントオブジェクト
	 * @return リトライプロンプトコンテンツ
	 */
	public String createRetry(Agent agent) {
		/*
		 * Velocityコンテキスト生成
		 */
		VelocityContext velocityContext = new VelocityContext();
		velocityContext.put("context", context);
		velocityContext.put("agent", agent);
		velocityContext.put("config", context.getConfig());

		/*
		 * エージェント向けプロンプト生成
		 */
		StringBuilder builder = new StringBuilder();
		builder.append(build(velocityContext, RESOURCE_TEMPLATE_RETRY_PROMPT));

		return builder.toString();
	}
}
