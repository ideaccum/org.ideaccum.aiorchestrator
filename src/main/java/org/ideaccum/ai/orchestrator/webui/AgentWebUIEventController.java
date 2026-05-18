package org.ideaccum.ai.orchestrator.webui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agent.Agent;
import org.ideaccum.ai.orchestrator.agent.AgentResult;
import org.ideaccum.ai.orchestrator.context.Conversation;
import org.ideaccum.ai.orchestrator.context.Conversations;
import org.ideaccum.ai.orchestrator.context.Session;
import org.ideaccum.ai.orchestrator.context.Sessions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * エージェント処理モニタリングイベントの制御クラスです。<br>
 * <p>
 * 静的シングルトンとして動作し、アプリケーション各所からイベントを受け取ってSSEサブスクライバー(ブラウザ接続)への配信処理を提供します。<br>
 * 接続後のページリロードに備え、セッション開始からの全イベントをバッファリングします。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/04/30	Kitagawa		新規作成
 *-->
 */
public class AgentWebUIEventController implements Constants {

	/** ロガーオブジェクト */
	private static final Logger log = LoggerFactory.getLogger(AgentWebUIServer.class);

	/** シングルトンインスタンス */
	private static final AgentWebUIEventController INSTANCE = new AgentWebUIEventController();

	/** SSEサブスクライバーリスト */
	private final List<Consumer<String>> subscribers;

	/** 再接続時リプレイ用イベントバッファ */
	private final LinkedList<String> eventBuffer;

	/**
	 * コンストラクタ<br>
	 */
	private AgentWebUIEventController() {
		this.subscribers = new CopyOnWriteArrayList<>();
		this.eventBuffer = new LinkedList<>();
	}

	/**
	 * シングルトンインスタンスを提供します。<br>
	 * @return シングルトンインスタンス
	 */
	public static AgentWebUIEventController instance() {
		return INSTANCE;
	}

	/**
	 * バッファ済みイベント一覧を取得します。<br>
	 * @return バッファ済みイベント一覧
	 */
	public synchronized List<String> getEventBuffer() {
		return new LinkedList<>(eventBuffer);
	}

	/**
	 * イベントバッファをクリアします。<br>
	 */
	public synchronized void clearEventBuffer() {
		eventBuffer.clear();
	}

	/**
	 * 開始前コントローラー表示用に過去ログをSSEバッファへ投入し、接続済みのサブスクライバーにも送信します。<br>
	 * @param agents エージェントリスト
	 * @param conversations 復元済み会話ログ
	 * @param sessions 復元済みセッション情報
	 */
	public void preloadBuffer(List<Agent> agents, Conversations conversations, Sessions sessions) {
		log.info("開始前コントローラー表示用バッファ事前投入を実行します。");
		List<String> newBuffer = new LinkedList<>();
		try {
			newBuffer.add("data: " + MAPPER.writeValueAsString(AgentWebUIEvent.createAgentInitialized(sortAgents(agents))) + "\n\n");
			if (conversations != null && !conversations.isEmpty()) {
				for (Conversation conv : conversations.getAll()) {
					String agentName = conv.getAgentName();
					Session session = sessions != null ? sessions.get(agentName) : null;
					String sessionId = session != null ? session.getSessionId() : null;
					long tokenUsage = conv.getTokenUsage() != null ? conv.getTokenUsage().getTotalTokens() : 0L;
					newBuffer.add("data: " + MAPPER.writeValueAsString(AgentWebUIEvent.createAgentStart(agentName, sessionId, conv.getTimestamp())) + "\n\n");
					newBuffer.add("data: " + MAPPER.writeValueAsString(AgentWebUIEvent.createAgentContent(agentName, conv.getContent())) + "\n\n");
					newBuffer.add("data: " + MAPPER.writeValueAsString(AgentWebUIEvent.createAgentFinish(agentName, sessionId, tokenUsage, 0L)) + "\n\n");
				}
				// 過去ログ表示はオーケストレーター処理完了状態送信でアイドル状態に設定
				newBuffer.add("data: " + MAPPER.writeValueAsString(AgentWebUIEvent.createOrchestratorDone()) + "\n\n");
			}
		} catch (Throwable e) {
			log.error("バッファ事前投入でエラーが発生しました。", e);
			return;
		}
		// バッファ更新(ロック内)
		synchronized (this) {
			eventBuffer.clear();
			eventBuffer.addAll(newBuffer);
		}
		// 接続済みサブスクライバーへ通知(ロック外)
		List<Consumer<String>> dead = new LinkedList<>();
		for (Consumer<String> subscriber : subscribers) {
			try {
				for (String event : newBuffer) {
					subscriber.accept(event);
				}
			} catch (Throwable e) {
				dead.add(subscriber);
			}
		}
		if (!dead.isEmpty()) {
			subscribers.removeAll(dead);
			log.debug("切断済みSSEサブスクライバーを{}件削除しました(残: {}件)。", dead.size(), subscribers.size());
		}
	}

	/**
	 * SSEサブスクライバーを登録します。<br>
	 * @param subscriber SSEサブスクライバーオブジェクト
	 */
	public void subscribe(Consumer<String> subscriber) {
		subscribers.add(subscriber);
	}

	/**
	 * SSEサブスクライバーを解除します。<br>
	 * @param subscriber SSEサブスクライバーオブジェクト
	 */
	public void unsubscribe(Consumer<String> subscriber) {
		subscribers.remove(subscriber);
	}

	/**
	 * イベントをブラウザに対して発行します。<br>
	 * @param event イベントオブジェクト
	 */
	private void publish(AgentWebUIEvent event) {
		try {
			String json = MAPPER.writeValueAsString(event);
			String data = "data: " + json + "\n\n";
			synchronized (this) {
				eventBuffer.add(data);
				if (eventBuffer.size() > SSE_MAX_BUFFER) {
					eventBuffer.removeFirst();
				}
			}
			List<Consumer<String>> dead = new LinkedList<>();
			for (Consumer<String> subscriber : subscribers) {
				try {
					subscriber.accept(data);
				} catch (Throwable e) {
					// 書き込み失敗/切断済み(削除候補追加)
					dead.add(subscriber);
				}
			}
			if (!dead.isEmpty()) {
				subscribers.removeAll(dead);
				log.debug("切断済みSSEサブスクライバーを{}件削除しました(残: {}件)。", dead.size(), subscribers.size());
			}
		} catch (Throwable e) {
			log.error("ブラウザイベント処理でエラーが発生しました。", e);
		}
	}

	/**
	 * 全エージェントの初期化イベントを発行します。<br>
	 * @param agents エージェントリスト
	 */
	public void publishInit(List<Agent> agents) {
		publish(AgentWebUIEvent.createAgentInitialized(sortAgents(agents)));
	}

	/**
	 * エージェント処理開始イベントを発行します。<br>
	 * @param agent エージェントオブジェクト
	 */
	public void publishStart(Agent agent) {
		log.info("エージェント処理開始イベントを通知します(" + agent.getName() + ")。");
		String timestamp = DEFAULT_DATE_FORMAT.format(new Date());
		publish(AgentWebUIEvent.createAgentStart(agent.getName(), agent.getSessionId(), timestamp));
	}

	/**
	 * エージェント処理開始イベントを発行します(エージェント名文字列版)。<br>
	 * @param agentName エージェント名
	 * @param sessionId セッションID
	 * @param timestamp タイムスタンプ
	 */
	public void publishStart(String agentName, String sessionId, String timestamp) {
		log.info("エージェント処理開始イベントを通知します(" + agentName + ")。");
		publish(AgentWebUIEvent.createAgentStart(agentName, sessionId, timestamp));
	}

	/**
	 * エージェントコンテンツチャンクイベントを発行します。<br>
	 * @param agentName エージェント名
	 * @param content コンテンツ
	 */
	public void publishContent(String agentName, String content) {
		log.info("エージェントコンテンツ処理イベントを通知します(" + agentName + ")。");
		publish(AgentWebUIEvent.createAgentContent(agentName, content));
	}

	/**
	 * エージェント一般メッセージイベントを発行します。<br>
	 * @param agentName エージェント名
	 * @param message メッセージ
	 */
	public void publishMessage(String agentName, String message) {
		log.info("エージェント一般メッセージイベントを通知します(" + agentName + ")。");
		publish(AgentWebUIEvent.createAgentMessage(agentName, message));
	}

	/**
	 * エージェント処理完了イベントを発行します(エージェント名文字列版)。<br>
	 * @param agentName エージェント名
	 */
	public void publishFinish(String agentName) {
		log.info("エージェント処理完了イベントを通知します(" + agentName + ")。");
		publish(AgentWebUIEvent.createAgentFinish(agentName, null, 0L, 0L));
	}

	/**
	 * エージェント処理完了イベントを発行します。<br>
	 * @param agent エージェントオブジェクト
	 * @param result エージェント処理結果オブジェクト
	 */
	public void publishFinish(Agent agent, AgentResult result) {
		log.info("エージェント処理完了イベントを通知します(" + agent.getName() + ")。");
		long tokenUsage = result != null && result.getUsage() != null ? result.getUsage().getTotalTokens() : 0L;
		long elapsedTime = result != null ? result.getElapsedTime() : 0L;
		publish(AgentWebUIEvent.createAgentFinish(agent.getName(), agent.getSessionId(), tokenUsage, elapsedTime));
	}

	/**
	 * エージェントエラーイベントを発行します。<br>
	 * @param agentName エージェント名
	 * @param message エラーメッセージ
	 */
	public void publishError(String agentName, String message) {
		log.info("エージェント処理エラーイベントを通知します(" + agentName + ")。");
		publish(AgentWebUIEvent.createAgentError(agentName, message));
	}

	/**
	 * 前回実行の会話ログをSSEイベントとしてバッファに復元します。<br>
	 * @param conversations 復元済み会話ログ
	 * @param sessions 復元済みセッション情報
	 */
	public void publishRestore(Conversations conversations, Sessions sessions) {
		log.info("エージェント過去ログ復元処理を実行します。");
		if (conversations.isEmpty()) {
			return;
		}
		for (Conversation conv : conversations.getAll()) {
			String agentName = conv.getAgentName();
			Session session = sessions.get(agentName);
			String sessionId = session != null ? session.getSessionId() : null;
			long tokenUsage = conv.getTokenUsage() != null ? conv.getTokenUsage().getTotalTokens() : 0L;
			publish(AgentWebUIEvent.createAgentStart(agentName, sessionId, conv.getTimestamp()));
			publish(AgentWebUIEvent.createAgentContent(agentName, conv.getContent()));
			publish(AgentWebUIEvent.createAgentFinish(agentName, sessionId, tokenUsage, 0L));
		}
	}

	/**
	 * オーケストレーター完了イベントを発行します。<br>
	 */
	public void publishDone() {
		log.info("エージェント処理完了イベントを通知します。");
		publish(AgentWebUIEvent.createOrchestratorDone());
	}

	/**
	 * オーケストレーター開始イベントを発行します。<br>
	 */
	public void publishOrchestratorStarted() {
		log.info("オーケストレーター開始イベントを通知します。");
		publish(AgentWebUIEvent.createOrchestratorStarted());
	}

	/**
	 * オーケストレーター停止イベントを発行します。<br>
	 */
	public void publishOrchestratorStopped() {
		log.info("オーケストレーター停止イベントを通知します。");
		publish(AgentWebUIEvent.createOrchestratorStopped());
	}

	/**
	 * エージェントリストをリーダー先頭でソートして返します。<br>
	 * @param agents エージェントリスト
	 * @return ソート済みエージェントリスト
	 */
	private List<Agent> sortAgents(List<Agent> agents) {
		List<Agent> sorted = new ArrayList<>(agents);
		sorted.sort(Comparator.comparingInt(a -> (a.isLeader() ? 0 : 1)));
		return sorted;
	}
}
