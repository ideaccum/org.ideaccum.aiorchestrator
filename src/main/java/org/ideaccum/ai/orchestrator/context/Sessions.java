package org.ideaccum.ai.orchestrator.context;

import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.ideaccum.ai.orchestrator.Constants;

/**
 * 各エージェントのCLIセッションIDを永続管理するストアクラスです。<br>
 * <p>
 * セッションIDは、エージェントのCLI起動時に出力される特定の文字列から正規表現で抽出されます。<br>
 * 抽出されたセッションIDは、エージェント名と紐づけて管理されます。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public class Sessions implements Constants {

	/** プロジェクト名 */
	private String projectName;

	/** 環境設定情報 */
	private Config config;

	/** セッションエントリ */
	private Map<String, Session> entries;

	/**
	 * コンストラクタ<br>
	 * @param projectName プロジェクト名
	 * @param config 環境設定情報
	 */
	Sessions(String projectName, Config config) {
		this.projectName = projectName;
		this.config = config;
		this.entries = new ConcurrentHashMap<>();
		restore();
	}

	/**
	 * セッションストアのリストアを行います。<br>
	 */
	private void restore() {
		if (!Files.exists(config.getAgentSessionStore(projectName))) {
			return;
		}
		try {
			Map<String, Session> loaded = MAPPER.readValue( //
					config.getAgentSessionStore(projectName).toFile(), //
					MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Session.class) //
			);
			entries.putAll(loaded);
		} catch (Throwable e) {
			throw new InternalError("セッションストアのリストアに失敗しました", e);
		}
	}

	/**
	 * セッションストアの永続化(保存)を行います。<br>
	 */
	private synchronized void persist() {
		try {
			if (!Files.exists(config.getAgentSessionStore(projectName).getParent())) {
				Files.createDirectories(config.getAgentSessionStore(projectName).getParent());
			}
			MAPPER.writerWithDefaultPrettyPrinter().writeValue( //
					config.getAgentSessionStore(projectName).toFile(), //
					entries //
			);
		} catch (Throwable e) {
			throw new InternalError("セッションストアの保存に失敗しました", e);
		}
	}

	/**
	 * セッションストアが空であるか判定します。<br>
	 * @return セッションストアが空である場合にtrueを返却
	 */
	public boolean isEmpty() {
		return entries.isEmpty();
	}

	/**
	 * セッションストアサイズを取得します。<br>
	 * @return セッションストアサイズ
	 */
	public int size() {
		return entries.size();
	}

	/**
	 * セッションIDを追加します。<br>
	 * 同一エージェント名のエントリが存在する場合は上書きします。<br>
	 * @param agentName エージェント名
	 * @param sessionId セッションID
	 * @param tokenUsage トークン使用量
	 */
	public void add(String agentName, String sessionId, TokenUsage tokenUsage) {
		if (sessionId == null || sessionId.isBlank()) {
			return;
		}
		if (!entries.containsKey(agentName)) {
			entries.put(agentName, new Session(agentName, sessionId, tokenUsage));
		} else {
			Session session = entries.get(agentName);
			session.setSessionId(sessionId);
			session.getTokenUsage().add(tokenUsage);
		}
		persist();
	}

	/**
	 * セッションIDを取得します。<br>
	 * @param agentName エージェント名
	 * @return セッションID
	 */
	public Session get(String agentName) {
		return entries.get(agentName);
	}

	/**
	 * エージェント名の変更に伴いセッションエントリのキーを更新します。<br>
	 * 旧エージェント名のエントリが存在しない場合は何もしません。<br>
	 * @param oldName 変更前エージェント名
	 * @param newName 変更後エージェント名
	 */
	public synchronized void rename(String oldName, String newName) {
		Session session = entries.remove(oldName);
		if (session == null) {
			return;
		}
		session.setAgentName(newName);
		entries.put(newName, session);
		persist();
	}
}
