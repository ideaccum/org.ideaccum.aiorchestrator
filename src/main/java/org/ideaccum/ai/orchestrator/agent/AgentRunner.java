package org.ideaccum.ai.orchestrator.agent;

import java.text.DecimalFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.context.Context;
import org.ideaccum.ai.orchestrator.webui.AgentWebUIEventController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * エージェントタスクの処理を管理するクラスです。<br>
 * <p>
 * このクラスは、エージェントタスクを必要に応じて待機、実行を管理します。<br>
 * </p>
 * 
 * @author Kitagawa<br>
 * 
 *<!--
 * 更新日		更新者			更新内容
 * 2026/04/07	Kitagawa		新規作成
 *-->
 */
public class AgentRunner implements Constants {

	/** ロガーオブジェクト */
	private final Logger log = LoggerFactory.getLogger(getClass());

	/** コンテキストオブジェクト */
	private final Context context;

	/** 処理実行サービスオブジェクト */
	private final ExecutorService executor;

	/** タスクコンテナマップ */
	private final ConcurrentHashMap<Agent, CompletableFuture<AgentResult>> tasks;

	/**
	 * コンストラクタ<br>
	 * @param context コンテキストオブジェクト
	 */
	public AgentRunner(Context context) {
		super();
		this.context = context;
		this.executor = Executors.newVirtualThreadPerTaskExecutor();
		this.tasks = new ConcurrentHashMap<>();
	}

	/**
	 * コンテキストオブジェクトを取得します。<br>
	 * @return コンテキストオブジェクト
	 */
	public Context getContext() {
		return context;
	}

	/**
	 * エージェントタスク管理を終了します。<br>
	 * @throws InterruptedException 処理中のタスクが存在する場合などの割り込みとなった場合にスローされます
	 */
	public void shutdown() throws InterruptedException {
		executor.shutdown();
		executor.awaitTermination(1, TimeUnit.MICROSECONDS);
	}

	/**
	 * エージェント処理をタスク管理して実行します。<br>
	 * @param agent エージェントオブジェクト
	 * @param prompt 実行処理プロンプト
	 * @return 実行結果情報
	 */
	public CompletableFuture<AgentResult> execute(Agent agent, String prompt) {
		/*
		 * エージェント単位タスクとして追加を行い、既存タスク完了後に新タスクを実行
		 */
		CompletableFuture<AgentResult> task = new CompletableFuture<>();
		tasks.merge(agent, task, //
				(last, incoming) -> last.whenCompleteAsync( //
						(action, executer) -> execute(agent, prompt, incoming), executor) //
						.thenCompose(func -> incoming) //
		);

		/*
		 * 処理キューが先頭の場合に即時実行
		 */
		tasks.computeIfPresent(agent, (key, current) -> {
			if (current == task) {
				executor.submit( //
						() -> execute(agent, prompt, task) //
				);
			}
			return current;
		});

		return task;
	}

	/**
	 * エージェント処理を実行します。<br>
	 * @param agent エージェントオブジェクト
	 * @param prompt 実行処理プロンプト
	 * @param future タスクコンテナ
	 */
	private void execute(Agent agent, String prompt, CompletableFuture<AgentResult> future) {
		try {
			printStart(agent);
			AgentResult result = agent.execute(prompt);
			future.complete(result);
			printFinish(agent, result);
		} catch (Throwable e) {
			log.error("エージェント処理に失敗しました。", e);
			AgentResult result = AgentResult.error(agent.getName(), e.getMessage(), e);
			future.complete(result);
			AgentWebUIEventController.instance().publishError(agent.getName(), e.getMessage());
		}
	}

	/**
	 * エージェント処理開始ラベルを出力します。<br>
	 * @param agent エージェントオブジェクト
	 */
	private void printStart(Agent agent) {
		String name = agent.getName();
		String type = agent.getType();
		if (agent.getModel() != null && !agent.getModel().isBlank()) {
			type += "(" + agent.getModel() + ")";
		}
		String role = agent.getRole();
		String sessionId = agent.getSessionId() == null ? "<None>" : agent.getSessionId();
		String threadName = Thread.currentThread().getName();
		log.info("エージェント処理セッションを開始します(%s(%s)) / %s / Session=%s [%s]".formatted(name, role, type, sessionId, threadName));
		AgentWebUIEventController.instance().publishStart(agent);
	}

	/**
	 * エージェント処理終了ラベルを出力します。<br>
	 * @param agent エージェントオブジェクト
	 * @param result 処理結果オブジェクト
	 */
	private void printFinish(Agent agent, AgentResult result) {
		String name = agent.getName();
		String type = agent.getType();
		if (agent.getModel() != null && !agent.getModel().isBlank()) {
			type += "(" + agent.getModel() + ")";
		}
		String role = agent.getRole();
		String sessionId = agent.getSessionId() == null ? "<None>" : agent.getSessionId();
		String threadName = Thread.currentThread().getName();
		String tokenUsage = result == null || result.getUsage() == null ? "<Unkown>" : new DecimalFormat("#,##0").format(result.getUsage().getTotalTokens());
		String elapsedTime = result == null || result.getUsage() == null ? "<Unkown>" : new DecimalFormat("#,##0").format(result.getElapsedTime());
		log.info("エージェント処理セッションを終了します(%s(%s)) / %s / Session=%s [%s]".formatted(name, role, type, sessionId, threadName));
		log.info("エージェント処理で使用したトークンは、" + tokenUsage + "でした。");
		log.info("エージェント処理の所要時間は、" + elapsedTime + "msでした。");
		AgentWebUIEventController.instance().publishFinish(agent, result);
	}
}