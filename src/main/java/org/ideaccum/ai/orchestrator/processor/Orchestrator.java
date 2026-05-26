package org.ideaccum.ai.orchestrator.processor;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agent.Agent;
import org.ideaccum.ai.orchestrator.agent.AgentResult;
import org.ideaccum.ai.orchestrator.agent.AgentResultType;
import org.ideaccum.ai.orchestrator.agent.AgentRunner;
import org.ideaccum.ai.orchestrator.context.Context;
import org.ideaccum.ai.orchestrator.context.Conversation;
import org.ideaccum.ai.orchestrator.webui.AgentWebUIEventController;
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
		try {
			/*
			 * Webインタフェース初期化イベント発行
			 */
			AgentWebUIEventController.instance().clearEventBuffer();
			AgentWebUIEventController.instance().publishInit(new ArrayList<>(context.getAgents().values()));

			/*
			 * 前回実行ログWebインタフェース復元イベント発行(initの直後にターンのみ挿入)
			 */
			AgentWebUIEventController.instance().publishRestore(context.getConversations(), context.getSessions());

			/*
			 * ディスパッチエージェント取得
			 * 前回セッションが完了済み(合意キーワードあり)の場合はリーダーから再開
			 */
			String prevConsensusKeyword = context.getConfig().getAgentFinalizeKeyword();
			boolean previouslyCompleted = context.getConversations().getAll().stream().anyMatch(c -> {
				if (c.getContent() == null) {
					return false;
				}
				for (String line : c.getContent().split("[\\r\\n]+")) {
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
			 * ディスパッチエージェント処理実行
			 */
			boolean consensus = false;
			while (!consensus && !dispatchAgents.isEmpty() && !stopSupplier.getAsBoolean()) {
				List<Agent> agents = new ArrayList<>();
				List<CompletableFuture<AgentResult>> futures = new ArrayList<>();
				for (Agent agent : dispatchAgents) {
					String prompt = context.getPromptFactory().create(agent);
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

					// エージェント処理完了イベント発行(累計トークン数を会話ログから算出して通知)
					long cumulativeTokens = 0;
					for (Conversation conversation : context.getConversations().getAll()) {
						if (agent.getName().equals(conversation.getAgentName()) && conversation.getTokenUsage() != null) {
							cumulativeTokens += conversation.getTokenUsage().getTotalTokens();
						}
					}
					AgentWebUIEventController.instance().publishFinish(agent, cumulativeTokens, result.getElapsedTime());

					// エージェント会話合意状態取得(CR/LF/CRLF の違いに対応してtrimで比較)
					String consensusKeyword = context.getConfig().getAgentFinalizeKeyword();
					for (String responseLine : result.getResponse().split("[\\r\\n]+")) {
						if (responseLine.trim().equals(consensusKeyword)) {
							consensus = true;
						}
					}

					// 合意済みの場合はディスパッチキーワードを無視して次エージェント呼び出しを抑止
					// 同一エージェントへの重複ディスパッチは除外する
					if (!consensus) {
						for (Agent dispatchAgent : extractDispatchAgents(result.getResponse())) {
							if (nextDispatchAgents.stream().noneMatch(a -> a.getName().equals(dispatchAgent.getName()))) {
								nextDispatchAgents.add(dispatchAgent);
							}
						}
					}
				}
				dispatchAgents = nextDispatchAgents;
			}
			if (stopSupplier.getAsBoolean()) {
				log.info("ユーザーからの停止要求を受け取ったため処理を終了します。");
				AgentWebUIEventController.instance().publishDone();
			} else {
				if (consensus) {
					log.info("エージェント間でのタスク完了合意に達したため処理を終了します。");
				}
				AgentWebUIEventController.instance().publishDone();
			}
		} finally {
			runner.shutdown();
		}
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
