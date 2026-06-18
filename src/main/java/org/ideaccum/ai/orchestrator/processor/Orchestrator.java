package org.ideaccum.ai.orchestrator.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agent.Agent;
import org.ideaccum.ai.orchestrator.agent.AgentResult;
import org.ideaccum.ai.orchestrator.agent.AgentResultType;
import org.ideaccum.ai.orchestrator.agent.AgentRunner;
import org.ideaccum.ai.orchestrator.context.Config;
import org.ideaccum.ai.orchestrator.context.Context;
import org.ideaccum.ai.orchestrator.context.Conversation;
import org.ideaccum.ai.orchestrator.context.TokenUsage;
import org.ideaccum.ai.orchestrator.webui.WebUIController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * マルチエージェント自立対話型オケーストレータークラスです。<br>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 * 2026/04/08	Kitagawa		AgentRunnerを使った並列ディスパッチ対応
 *-->
 */
public class Orchestrator implements Constants {

	/** ロガーオブジェクト */
	private final Logger log = LoggerFactory.getLogger(getClass());

	/** コンテキストオブジェクト */
	private final Context context;

	/** エージェント実行オブジェクト */
	private final AgentRunner runner;

	/** 処理停止判定サプライヤー */
	private final BooleanSupplier stopSupplier;

	/**
	 * コンストラクタ<br>
	 * @param context コンテキストオブジェクト
	 * @param stopSupplier 処理停止判定サプライヤー
	 */
	public Orchestrator(Context context, BooleanSupplier stopSupplier) {
		this.context = context;
		this.runner = new AgentRunner(context);
		this.stopSupplier = stopSupplier;
	}

	/**
	 * マルチエージェント処理を実行します。<br>
	 * <p>
	 * コンテキスト内容をもとにエージェント処理を実行します。<br>
	 * 過去のセッションが存在する場合はそれを復元して会話を再開します。<br>
	 * エージェント処理は会話内のディスパッチ先が存在する場合は明示的にエージェントにプロンプトをディスパッチして並行処理を実施します。<br>
	 * </p>
	 * @throws Throwable エージェント処理中に予期せぬエラーが発生した場合にスローされます
	 */
	public void execute() throws Throwable {
		Thread stopMonitor = null;
		try {
			/*
			 * Webインタフェース初期化イベント発行
			 */
			WebUIController.instance().clearEventBuffer();
			WebUIController.instance().publishInit(new ArrayList<>(context.getAgents().values()));

			/*
			 * 前回実行ログWebインタフェース復元イベント発行(initの直後にターンのみ挿入)
			 */
			WebUIController.instance().publishRestore(context.getConversations(), context.getSessions());

			/*
			 * ディスパッチエージェント取得
			 * 前回セッションが完了済み(合意キーワードあり)の場合はリーダーから再開
			 */
			String prevConsensusKeyword = context.getConfig().getAgentFinalizeKeyword();
			boolean previouslyCompleted = context.getConversations().getAll().stream().anyMatch(c -> {
				if (c.getContent() == null) {
					return false;
				}
				for (String line : c.getContent().split(LINE_SEPARATOR_REGEX)) {
					if (line.trim().equals(prevConsensusKeyword)) {
						return true;
					}
				}
				return false;
			});
			List<Agent> dispatchAgents = previouslyCompleted ? new LinkedList<>() : context.getConversations().getLastDispatchAgents();

			/*
			 * ディスパッチエージェントが存在しない場合はリーダーエージェントを設定
			 */
			if (dispatchAgents.isEmpty()) {
				dispatchAgents.add(context.getLeaderAgent());
			}

			/*
			 * 停止監視スレッド生成
			 * - 停止シグナル受信後はOrchestratorが終了するまで継続的にstopAllを呼び続ける
			 * - 停止後にリトライ等で新プロセスが起動した場合も確実にkillするため
			 */
			stopMonitor = new Thread(() -> {
				while (!Thread.currentThread().isInterrupted() && !stopSupplier.getAsBoolean()) {
					try {
						Thread.sleep(PROCESS_STOP_MONITOR_INTERVAL);
					} catch (InterruptedException e) {
						return;
					}
				}
				/*
				 * 停止シグナル受信後にOrchestratorが終了(interrupt)するまでkillを繰り返す
				 */
				while (!Thread.currentThread().isInterrupted()) {
					runner.stopAll();
					try {
						Thread.sleep(PROCESS_STOP_MONITOR_INTERVAL);
					} catch (InterruptedException e) {
						return;
					}
				}
			});
			stopMonitor.setDaemon(true);
			stopMonitor.start();

			/*
			 * ディスパッチエージェント処理実行
			 */
			Map<String, Integer> selfDispatchCount = new HashMap<>();
			boolean consensus = false;
			while (!consensus && !dispatchAgents.isEmpty() && !stopSupplier.getAsBoolean()) {
				List<Agent> agents = new ArrayList<>();
				List<CompletableFuture<AgentResult>> futures = new ArrayList<>();
				for (Agent agent : dispatchAgents) {
					String prompt = context.getPromptFactory().createStart(agent);
					agents.add(agent);
					futures.add(runner.execute(agent, prompt));
				}

				/*
				 * エージェント処理結果取得
				 */
				List<Agent> nextDispatchAgents = new LinkedList<>();
				for (int i = 0; i < agents.size(); i++) {
					Agent agent = agents.get(i);
					AgentResult result = futures.get(i).get();

					// エージェントセッションID保存
					context.getSessions().add(agent.getName(), agent.getSessionId(), result.getUsage());

					// エージェント会話ログ保存(コマンドラインレベルのエラーは記録しない)
					if (result.getType() != AgentResultType.ERROR) {
						context.getConversations().add(agent.getName(), result.getResponse(), result.getUsage());
					}

					// キーワードなし時リトライ(リトライプロンプト自体は会話ログに保存しない、リトライ応答は保存する)
					// 停止要求済みの場合はリトライを行わない(新プロセス起動を抑止)
					String response = result.getResponse();
					long elapsedTime = result.getElapsedTime();
					if (!stopSupplier.getAsBoolean() && result.getType() != AgentResultType.ERROR && response != null && !hasControlKeyword(response, context.getConfig())) {
						String retryPrompt = context.getPromptFactory().createRetry(agent);
						boolean retrySuccess = false;
						for (int retry = 0; retry < AGENT_RETRY_COUNT && !stopSupplier.getAsBoolean(); retry++) {
							log.debug("エージェント[" + agent.getName() + "]のレスポンスにキーワードがないためリトライします(" + (retry + 1) + "/" + AGENT_RETRY_COUNT + ")。");
							AgentResult retryResult = runner.execute(agent, retryPrompt).get();

							// リトライ処理時間を加算(レスポンスが空でも時間は消費しているため加算)
							elapsedTime += retryResult.getElapsedTime();

							// リトライ応答を会話ログに保存(コマンドラインレベルのエラーは記録しない)
							if (retryResult.getType() != AgentResultType.ERROR) {
								context.getConversations().add(agent.getName(), retryResult.getResponse(), retryResult.getUsage());
							}

							// キーワード判定にリトライ応答を使用
							if (retryResult.getResponse() != null && !retryResult.getResponse().isBlank()) {
								response = retryResult.getResponse();
							}

							if (hasControlKeyword(response, context.getConfig())) {
								retrySuccess = true;
								break;
							}
						}
						if (!retrySuccess) {
							log.warn("エージェント[" + agent.getName() + "]が" + AGENT_RETRY_COUNT + "回リトライしてもキーワードが確認できませんでした。");
							WebUIController.instance().publishError(agent.getName(), "エージェントの会話ループが発生しました");
						}
					}

					// エージェント処理完了イベント発行(累計トークン数を会話ログから算出して通知)
					TokenUsage tokenUsage = new TokenUsage();
					for (Conversation conversation : context.getConversations().getAll()) {
						if (agent.getName().equals(conversation.getAgentName()) && conversation.getTokenUsage() != null) {
							tokenUsage.add(conversation.getTokenUsage());
						}
					}
					WebUIController.instance().publishFinish(agent, tokenUsage, elapsedTime);

					// エージェント会話合意状態取得(CR/LF/CRLF の違いに対応してtrimで比較)
					String consensusKeyword = context.getConfig().getAgentFinalizeKeyword();
					for (String responseLine : response.split(LINE_SEPARATOR_REGEX)) {
						if (responseLine.trim().equals(consensusKeyword)) {
							consensus = true;
						}
					}

					// 合意済みの場合はディスパッチキーワードを無視して次エージェント呼び出しを抑止
					// 同一エージェントへの重複ディスパッチは除外する
					if (!consensus) {
						List<Agent> dispatchTargets = extractDispatchAgents(response);
						boolean hasSelfDispatch = dispatchTargets.stream().anyMatch(dispatchAgent -> dispatchAgent.getName().equals(agent.getName()));
						if (hasSelfDispatch) {
							int count = selfDispatchCount.merge(agent.getName(), 1, Integer::sum);
							if (count > AGENT_RETRY_COUNT) {
								log.warn("エージェント[" + agent.getName() + "]が連続して自己ディスパッチを" + count + "回行いました。");
								WebUIController.instance().publishError(agent.getName(), "エージェントの会話ループが発生しました");
							}
						} else {
							selfDispatchCount.remove(agent.getName());
						}
						for (Agent dispatchAgent : dispatchTargets) {
							// 自己ディスパッチ上限超過の場合はスキップ(他エージェントへのディスパッチは継続)
							if (agent.getName().equals(dispatchAgent.getName()) && selfDispatchCount.getOrDefault(agent.getName(), 0) > AGENT_RETRY_COUNT) {
								continue;
							}
							if (nextDispatchAgents.stream().noneMatch(nextDispatchAgent -> nextDispatchAgent.getName().equals(dispatchAgent.getName()))) {
								nextDispatchAgents.add(dispatchAgent);
							}
						}
					}
				}
				dispatchAgents = nextDispatchAgents;
			}
			if (stopSupplier.getAsBoolean()) {
				log.debug("ユーザーからの停止要求を受け取ったため処理を終了します。");
				WebUIController.instance().publishDone();
			} else {
				if (consensus) {
					log.debug("エージェント間でのタスク完了合意に達したため処理を終了します。");
				}
				WebUIController.instance().publishDone();
			}
		} finally {
			if (stopMonitor != null) {
				stopMonitor.interrupt();
			}
			runner.shutdown();
		}
	}

	/**
	 * エージェントレスポンスに制御キーワードが含まれているか判定します。<br>
	 * @param response エージェントレスポンス
	 * @param config 設定オブジェクト
	 * @return キーワードが含まれる場合true
	 */
	private boolean hasControlKeyword(String response, Config config) {
		if (response == null) {
			return false;
		}
		if (config.getAgentDispatchKeywordPattern().matcher(response).find()) {
			return true;
		}
		for (String line : response.split(LINE_SEPARATOR_REGEX)) {
			String trimmed = line.trim();
			if (trimmed.equals(config.getAgentFinalizeKeyword())) {
				return true;
			}
			if (trimmed.equals(config.getAgentStopKeyword())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * エージェントレスポンスからディスパッチ先エージェントリストを抽出します。<br>
	 * @param response エージェントレスポンス
	 * @return ディスパッチ先エージェントリスト
	 */
	private List<Agent> extractDispatchAgents(String response) {
		List<Agent> agents = new LinkedList<>();
		if (response == null) {
			return agents;
		}
		Pattern pattern = context.getConfig().getAgentDispatchKeywordPattern();
		Matcher matcher = pattern.matcher(response);
		while (matcher.find()) {
			String agentName = matcher.group(1).trim();
			agents.add(context.getAgent(agentName));
		}
		return agents;
	}
}
