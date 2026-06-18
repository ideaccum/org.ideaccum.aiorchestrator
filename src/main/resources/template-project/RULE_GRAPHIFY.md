# RULE_GRAPHIFY.md

@graphify-out ディレクトリが存在する場合は**graphifyスキルを利用して効率的なリソース検索・参照を行う**。  
graphifyスキルについては**[graphify]章を参照**。  


## graphify

このプロジェクトには、 @graphify-out/ にナレッジグラフ（graphify）があります。  
graphipyによる解析が未実施の場合には該当のディレクトリは存在しない場合があります。  

ルール:
- アーキテクチャやコードベースに関する質問に答える前に、 @graphify-out/GRAPH_REPORT.md を読み、ゴッドノードとコミュニティ構造を確認すること。  
- @graphify-out/wiki/index.md が存在する場合は、生ファイルを直接読む代わりにそちらを参照すること。  
- "X と Y はどう関係しているか"といったモジュール横断的な質問には、grep によるファイルスキャンより`graphify query "<question>"`、`graphify path "<A>" "<B>"`、`graphify explain "<concept>"` を優先すること - これらはグラフの EXTRACTED + INFERRED エッジをトラバースします。  
- このセッション中にコードファイルを変更した場合は、`graphify update .`を実行してグラフを最新状態に保つこと（AST のみ解析・API コスト不要）。  
