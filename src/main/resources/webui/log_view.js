/**
 * ログビューページコントローラースクリプトクラスです。<br>
 * <p>
 * アクティブプロジェクトの会話ログを一覧表示する機能を提供します。<br>
 * </p>
 *
 * @author Kitagawa<br>
 *
 *<!--
 * 更新日		更新者			更新内容
 * 2026/05/15	Kitagawa		新規作成
 *-->
 */
class LogViewController {
	/**
	 * コンストラクタ<br>
	 */
	constructor() {
		try {
			marked.setOptions({
				breaks: true, // GitHub Flavored Markdown を使用
				gfm: true, // 単一の改行(\n)を<br>に変換
			});
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 初期化処理を実行します。<br>
	 */
	async init() {
		try {
			/*
			 * 会話ログの初期表示
			 */
			await this.#loadLog();

			/*
			 * イベントハンドラ登録
			 */
			document.getElementById("btn-refresh").onclick = () => this.#loadLog();
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 会話ログをサーバーから取得して画面に表示します。<br>
	 */
	async #loadLog() {
		try {
			/*
			 * 会話ログ取得
			 */
			const result = await WebAPI.getConversationLog({ _: null }, true);
			if (!result) {
				return;
			}

			const projectName = result.data?.projectName || "";
			if (!projectName) {
				return;
			}

			const conversations = result.data?.conversations || [];

			/*
			 * 会話ログ描画
			 */
			const elEntries = document.getElementById("log-entries");
			const elEmpty = document.getElementById("log-empty");
			elEntries.innerHTML = "";
			if (conversations.length === 0) {
				elEmpty.style.display = "";
				elEntries.style.display = "none";
				return;
			}
			elEmpty.style.display = "none";
			elEntries.style.display = "";
			elEntries.className = "log-entries";
			conversations.forEach((conv, index) => {
				const prevConv = index > 0 ? conversations[index - 1] : null;
				const elapsed = LogViewController.#calcElapsed(prevConv?.timestamp, conv.timestamp);

				const inTokens = conv.tokenUsage?.inputTokens || 0;
				const outTokens = conv.tokenUsage?.outputTokens || 0;
				const totalTokens = inTokens + outTokens;
				const tokenText = conv.tokenUsage ? totalTokens.toLocaleString() + " tokens (in: " + inTokens.toLocaleString() + " / out: " + outTokens.toLocaleString() + ")" : "—";

				const isOwner = conv.agentName === Constants.OWNER_AGENT_NAME;
				const turn = document.createElement("div");
				turn.className = "log-turn" + (isOwner ? " is-owner" : "");

				const initial = (conv.agentName || "?").charAt(0).toUpperCase();
				const avatarColor = Utils.avatarColor(conv.agentName || "");

				/*
				 * グリッド: 列1=アバター 列2=タイトル/本文/トークン
				 */
				const avatar = document.createElement("div");
				avatar.className = "log-turn-avatar";
				avatar.style.background = avatarColor;
				avatar.textContent = initial;

				const title = document.createElement("div");
				title.className = "log-turn-title";
				title.innerHTML =
					'<span class="log-agent-name">' + Utils.esc(conv.agentName || "") + "</span>" +
					'<span class="log-turn-timestamp">' + Utils.esc(conv.timestamp || "") + "</span>";

				const body = document.createElement("div");
				body.className = "log-turn-body";
				body.innerHTML = marked.parse(conv.content || "");

				const tokens = document.createElement("div");
				tokens.className = "log-turn-tokens";
				tokens.textContent = tokenText + (elapsed !== "—" ? "　" + elapsed : "");

				turn.appendChild(avatar);
				turn.appendChild(title);
				turn.appendChild(body);
				turn.appendChild(tokens);
				elEntries.appendChild(turn);
			});
		} catch (e) {
			Utils.catchFatal(e);
		}
	}

	/**
	 * 2つのタイムスタンプから所要時間文字列を計算します。<br>
	 * @param {string|null} prevTimestamp - 前ターンのタイムスタンプ
	 * @param {string|null} curTimestamp - 現ターンのタイムスタンプ
	 * @returns {string} 所要時間文字列
	 */
	static #calcElapsed(prevTimestamp, curTimestamp) {
		if (!prevTimestamp || !curTimestamp) {
			return "—";
		}
		const parse = (timestamp) => {
			const matcher = timestamp.match(/(\d{4})\/(\d{2})\/(\d{2}) (\d{2}):(\d{2}):(\d{2})/);
			if (!matcher) {
				return null;
			}
			return new Date(parseInt(matcher[1]), parseInt(matcher[2]) - 1, parseInt(matcher[3]), parseInt(matcher[4]), parseInt(matcher[5]), parseInt(matcher[6]));
		};
		const prev = parse(prevTimestamp);
		const cur = parse(curTimestamp);
		if (!prev || !cur) {
			return "—";
		}
		const diffMs = cur - prev;
		if (diffMs < 0) {
			return "—";
		}
		const sec = Math.floor(diffMs / 1000);
		if (sec < 60) {
			return sec + "秒";
		}
		const min = Math.floor(sec / 60);
		const remSec = sec % 60;
		return min + "分" + remSec + "秒";
	}
}
