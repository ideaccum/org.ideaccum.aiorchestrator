package org.ideaccum.ai.orchestrator.context;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agent.Agent;
import org.ideaccum.ai.orchestrator.exception.ApplicationException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * エージェント間のやり取りを記録・永続化する会話ログ管理クラスです。<br>
 * <p>
 * 会話ログは、JSON形式でイテレーションごとに記録され、後続のエージェントへのプロンプトに埋め込むことで文脈を継続させる際に利用されます。<br>
 * 会話ログは、ファイルに永続化され、アプリケーションの再起動後も復元されます。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public class Conversations implements Constants {

	/** タイムスタンプフォーマット */
	@JsonIgnore
	private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	/** コンテキストオブジェクト */
	@JsonIgnore
	private final Context context;

	/** エージェント会話リスト */
	@JsonProperty
	private final List<Conversation> entries = new CopyOnWriteArrayList<>();

	/**
	 * コンストラクタ<br>
	 * @param context コンテキストオブジェクト
	 */
	Conversations(Context context) {
		this.context = context;
		restore();
	}

	/**
	 * コンストラクタ<br>
	 * @deprecated Jacksonからのデシリアライズ利用のためのみに設置されたコンストラクタです
	 */
	public Conversations() {
		this(null);
	}

	/**
	 * 会話ログのリストアを行います。<br>
	 */
	private void restore() {
		Config config = context.getConfig();
		if (!Files.exists(config.getAgentConversationLogfile(context.getProjectName())))
			return;
		try {
			List<Conversation> loaded = MAPPER.readValue( //
					config.getAgentConversationLogfile(context.getProjectName()).toFile(), //
					MAPPER.getTypeFactory().constructCollectionType(//
							List.class, Conversation.class //
					));
			for (Conversation conversation : loaded) {
				conversation.setContext(context);
			}
			entries.clear();
			entries.addAll(loaded);
		} catch (Throwable e) {
			throw new ApplicationException("会話ログのリストアに失敗しました", e);
		}
	}

	/**
	 * 会話ログの永続化(保存)を行います。<br>
	 */
	private synchronized void persist() {
		Config config = context.getConfig();
		try {
			if (!Files.exists(config.getAgentConversationLogfile(context.getProjectName()).getParent())) {
				Files.createDirectories(config.getAgentConversationLogfile(context.getProjectName()).getParent());
			}
			MAPPER.writerWithDefaultPrettyPrinter().writeValue(config.getAgentConversationLogfile(context.getProjectName()).toFile(), entries);
		} catch (IOException e) {
			throw new ApplicationException("会話ログの保存に失敗しました", e);
		}
	}

	/**
	 * 会話ログが空であるか判定します。<br>
	 * @return 会話ログが空である場合にtrueを返却
	 */
	public boolean isEmpty() {
		return entries.isEmpty();
	}

	/**
	 * 会話ログサイズを取得します。<br>
	 * @return 会話ログサイズ
	 */
	public int size() {
		return entries.size();
	}

	/**
	 * エージェント発言ログを追加します。<br>
	 * @param agentName エージェント名
	 * @param content 発言内容
	 * @param tokenUsage トークン使用量
	 */
	public void add(String agentName, String content, TokenUsage tokenUsage) {
		entries.add(new Conversation(context, LocalDateTime.now().format(FORMAT_DATE), agentName, content, tokenUsage));
		persist();
	}

	/**
	 * すべてのエージェント発言内容を取得します。<br>
	 * @return すべてのエージェント発言内容
	 */
	public List<Conversation> getAll() {
		return Collections.unmodifiableList(entries);
	}

	/**
	 * 直近指定件数の発言を文脈文字列として取得します。<br>
	 * セッション継続時の継続プロンプトに使用します(全ログではなく直近のみ提供します)。<br>
	 * @param count 取得件数
	 * @return 直近指定件数の発言文脈文字列
	 */
	public String getRecentConversationLog(int count) {
		int size = entries.size();
		if (size == 0) {
			return "";
		}
		List<Conversation> latest = entries.subList(Math.max(0, size - count), size);
		StringBuilder builder = new StringBuilder();
		latest.forEach(entry -> builder.append(entry.toString()));
		return builder.toString();
	}

	/**
	 * 最新イテレーションのディスパッチキーワードをもとに対象エージェントリストを取得します。<br>
	 * @return 最新イテレーションのディスパッチキーワードをもとに対象エージェントリスト
	 */
	public List<Agent> getLastDispatchAgents() {
		for (int i = entries.size() - 1; i >= 0; i--) {
			List<Agent> agents = entries.get(i).getDispatchAgents();
			if (!agents.isEmpty()) {
				return agents;
			}
		}
		return new LinkedList<>();
	}

	/**
	 * 指定エージェント向けのの最新会話イテレーションを取得します。<br>
	 * @param agent エージェントオブジェクト
	 * @return 指定エージェント向けのの最新会話イテレーション。存在しない場合はnullを返却
	 */
	public Conversation getLastConversation(Agent agent) {
		for (int i = entries.size() - 1; i >= 0; i--) {
			Conversation conversation = entries.get(i);
			if (conversation.getDispatchAgents().contains(agent)) {
				return conversation;
			}
		}
		return null;
	}

	/**
	 * 指定エージェント向けの未読会話イテレーションリストを取得します。<br>
	 * @param agent エージェントオブジェクト
	 * @return 指定エージェント向けの未読会話イテレーションリスト。存在しない場合は空リストを返却
	 */
	public List<Conversation> getUnreadConversation(Agent agent) {
		List<Conversation> result = new LinkedList<>();
		for (int i = entries.size() - 1; i >= 0; i--) {
			Conversation conversation = entries.get(i);
			if (Objects.equals(agent.getName(), conversation.getAgentName())) {
				break;
			}
			result.addFirst(conversation);
		}
		return result;
	}

	/**
	 * 会話イテレーションリストから会話履歴文字列を生成します。<br>
	 * @param conversations 会話イテレーションリスト
	 * @return 会話履歴文字列
	 */
	private String getHistory(List<Conversation> conversations) {
		if (conversations == null || conversations.isEmpty()) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		for (Conversation conversation : conversations) {
			builder.append("**");
			builder.append(conversation.getTimestamp());
			builder.append(" / ");
			builder.append(conversation.getAgentName());
			builder.append("**");
			builder.append("\n");
			builder.append(conversation.getContent());
			builder.append("\n");
		}
		return builder.toString();
	}

	/**
	 * 全ての会話履歴を取得します。<br>
	 * オーナーエントリはエージェントに対するプロンプトに既に含まれるため除外します。<br>
	 * @return 全ての会話履歴
	 */
	public String getAllHistory() {
		List<Conversation> filtered = entries.stream() //
				.filter(conversation -> !OWNER_AGENT_NAME.equals(conversation.getAgentName())) //
				.collect(Collectors.toList()) //
		;
		return getHistory(filtered);
	}

	/**
	 * 指定エージェント向けの未読会話履歴を取得します。<br>
	 * セッション継続エージェントへのオーナー追加プロンプト伝達のため、オーナーエントリを含みます。<br>
	 * @param agent エージェントオブジェクト
	 * @return 指定エージェント向けの未読会話履歴。存在しない場合は空文字を返却
	 */
	public String getUnreadHistory(Agent agent) {
		return getHistory(getUnreadConversation(agent));
	}
}
