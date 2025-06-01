# PixVault API Client

このディレクトリには、PixVault APIのテストとドキュメント用のAPI clientの設定が含まれています。

## セットアップ

### Bruno のインストール

1. [Bruno](https://www.usebruno.com/) の公式サイトからダウンロード
2. または Homebrew を使用してインストール:
   ```bash
   brew install bruno
   ```

### 使い方

1. Bruno を起動
2. "Open Collection" をクリック
3. `api-client/pixvault` ディレクトリを選択
4. 環境を "Local" に設定

### 利用可能なエンドポイント

#### System
- Health Check - サービスの稼働状況確認

#### Authentication  
- Login - ユーザーログイン

### 環境設定

- **Local** - ローカル開発環境 (http://localhost:9000)
- **Production** - 本番環境 (https://api.pixvault.app)

### テスト実行

1. まず Play アプリケーションを起動:
   ```bash
   cd backend
   sbt run
   ```

2. Bruno で "Health Check" をクリックして実行
3. 次に "Login" をクリックして実行

ログイン成功時、アクセストークンとリフレッシュトークンが自動的に環境変数に保存されます。