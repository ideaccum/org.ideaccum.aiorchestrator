package org.ideaccum.ai.orchestrator.context;

import java.nio.file.Files;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agent.AgentFactory;
import org.ideaccum.ai.orchestrator.webui.WebUIServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * コンテキストファクトリクラスです。<br>
 * <p>
 * 環境設定情報をプロパティファイルから読み込み、会話ログやセッションストア、エージェントインスタンスの初期化行ったうえでコンテキストオブジェクトを生成します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public class ContextFactory implements Constants {

	/** ロガーオブジェクト */
	private static final Logger log = LoggerFactory.getLogger(WebUIServer.class);

	/** 環境定義情報 */
	private Config config;

	/**
	 * コンストラクタ<br>
	 * @param config 環境設定情報
	 */
	public ContextFactory(Config config) {
		this.config = config;
	}

	/**
	 * コンテキストオブジェクトを生成します。<br>
	 * @param projectName プロジェクト名
	 * @return コンテキストオブジェクト
	 * @throws Throwable 正常にコンテキストオブジェクトを生成できなかった場合にスローされます
	 */
	public Context create(String projectName) throws Throwable {
		/*
		 * コンテキスト生成
		 */
		Context context = new Context(projectName, config);

		/*
		 * プロジェクトディレクトリ初期化
		 */
		Files.createDirectories(config.getApplicationProjectPath(projectName));
		log.debug("プロジェクトディレクトリを初期化しました(" + config.getApplicationProjectPath(projectName) + ")。");

		/*
		 * 会話ログ初期化
		 */
		Conversations conversations = new Conversations(context);
		context.setConversations(conversations);
		log.debug("過去の会話ログを読み込みました(" + conversations.size() + "ターン)。");

		/*
		 * セッションストア初期化
		 */
		Sessions sessions = new Sessions(projectName, config);
		context.setSessions(sessions);
		log.debug("セッションストアを読み込みました(" + sessions.size() + "セッション)。");

		/*
		 * トークン使用量初期化
		 */
		TokenUsage usage = new TokenUsage();
		context.setUsage(usage);
		log.debug("トークン使用量情報を読み込みました(" + usage.getTotalTokens() + "トークン)。");

		/*
		 * プロンプトファクトリ初期化
		 */
		PromptFactory promptFactory = new PromptFactory(context);
		context.setPromptFactory(promptFactory);
		log.debug("プロンプトファクトリオブジェクトを初期化しました。");

		/*
		 * エージェントインスタンス初期化
		 */
		AgentFactory agentFactory = new AgentFactory(context);
		context.setAgents(agentFactory.create());
		log.debug("エージェントインスタンスを初期化しました(" + context.getAgents().size() + "エージェント)。");

		return context;
	}
}
