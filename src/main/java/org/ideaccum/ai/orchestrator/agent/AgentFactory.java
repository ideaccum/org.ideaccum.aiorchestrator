package org.ideaccum.ai.orchestrator.agent;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ideaccum.ai.orchestrator.Constants;
import org.ideaccum.ai.orchestrator.agents.AbstractCliAgent;
import org.ideaccum.ai.orchestrator.context.Context;
import org.ideaccum.ai.orchestrator.context.Session;

/**
 * エージェントファクトリクラスです。<br>
 * <p>
 * エージェントのインスタンスを生成するためのクラスです。<br>
 * エージェントの種類に応じて適切なエージェントクラスのインスタンスを生成します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/03/30	Kitagawa		新規作成
 *-->
 */
public class AgentFactory implements Constants {

	/** アプリケーションコンテキスト */
	private Context context;

	/**
	 * コンストラクタ<br>
	 * @param context アプリケーションコンテキスト
	 */
	public AgentFactory(Context context) {
		this.context = context;
	}

	/**
	 * エージェント設定ディレクトリからすべてのエージェントを生成します。<br>
	 * @return エージェントリスト
	 */
	public Map<String, Agent> create() {
		/*
		 * エージェント設定ディレクトリ確認
		 */
		Path agentsPath = context.getConfig().getApplicationAgentsPath(context.getProjectName());
		if (!Files.exists(agentsPath)) {
			throw new InternalError(String.format("エージェント設定ディレクトリが見つかりません(%s)。", context.getConfig().getApplicationAgentsPath(context.getProjectName())));
		}

		/*
		 * エージェント設定情報読み込み
		 */
		List<AgentConfig> agentConfigs = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(agentsPath, "*.properties")) {
			for (Path file : stream) {
				agentConfigs.add(new AgentConfig(file));
			}
		} catch (Throwable e) {
			throw new InternalError("エージェント設定の読み込みに失敗しました。", e);
		}
		Collections.sort(agentConfigs, new Comparator<AgentConfig>() {
			@Override
			public int compare(AgentConfig o1, AgentConfig o2) {
				return o1.isLeader() ? 0 : 1;
			}
		});

		/*
		 * エージェントクラスインスタンス生成
		 */
		Map<String, Agent> agents = new LinkedHashMap<>();
		for (AgentConfig agentConfig : agentConfigs) {
			Agent agent = createAgent(agentConfig);
			agents.put(agent.getName(), agent);
		}

		/*
		 * CLIエージェントセッションID設定
		 */
		for (Agent agent : agents.values()) {
			if (agent instanceof AbstractCliAgent cliAgent) {
				Session session = context.getSessions().get(agent.getName());
				String sessionId = session != null ? session.getSessionId() : null;
				if (sessionId != null && !sessionId.isBlank()) {
					cliAgent.setSessionId(sessionId);
				}
			}
		}

		return agents;
	}

	/**
	 * エージェントタイプに応じたエージェントインスタンスを生成します。<br>
	 * @param agentConfig エージェント設定情報
	 * @return エージェントインスタンス
	 */
	private Agent createAgent(AgentConfig agentConfig) {
		AgentType agentType = AgentType.of(agentConfig.getType());
		if (agentType != null) {
			if (AbstractCliAgent.class.isAssignableFrom(agentType.getType())) {
				try {
					return (AbstractCliAgent) agentType.getType().getConstructor(Context.class, AgentConfig.class).newInstance(context, agentConfig);
				} catch (Throwable e) {
					throw new InternalError(String.format("エージェントタイプ(%s)のインスタンス生成に失敗しました", agentConfig.getType()));
				}
			}
		}
		throw new InternalError(String.format("未知のエージェントタイプ(%s)が指定されました", agentConfig.getType()));
	}
}
