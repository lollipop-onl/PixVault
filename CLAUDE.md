# CLAUDE.md

このファイルは、このリポジトリでコードを扱う際のClaude Code (claude.ai/code) へのガイダンスを提供します。

## プロジェクト概要

PixVaultは、Google PhotosやiCloudの代替として、AWS S3 Glacierを使用した低コストの写真・動画ストレージソリューションです。プロジェクトはScala/Play Frameworkバックエンドと計画中のReact/TypeScriptフロントエンドを持つモノレポ構造を使用しています。

## 主要コマンド

### 開発
```bash
npm run dev              # Dockerサービスを起動 + フロントエンドとバックエンドを実行
npm run dev:backend      # バックエンドのみ実行 (cd backend && sbt run)
npm run docker:up        # PostgreSQL、LocalStack、MinIOを起動
npm run docker:down      # すべてのDockerサービスを停止
npm run db:shell         # PostgreSQLシェルに接続
```

### バックエンド (Scala/Play)
```bash
cd backend
sbt run                  # アプリケーションを http://localhost:9000 で実行
sbt test                 # すべてのテストを実行
sbt "testOnly *AuthControllerSpec"  # 特定のテストを実行
sbt dist                 # プロダクション用ディストリビューションを作成
```

### テスト
```bash
npm run test             # すべてのテストを実行 (フロントエンド + バックエンド)
npm run test:backend     # バックエンドのテストのみ実行

# バックエンドのテストカバレッジ (PATHにsbtが必要)
cd backend
sbt clean coverage test coverageReport  # テストカバレッジレポートを生成
# カバレッジレポートは backend/target/scala-3.7.0/scoverage-report/index.html にあります
```

## アーキテクチャ

### バックエンド構造
バックエンドはリポジトリパターンを使用したクリーンアーキテクチャに従っています：
- **Controllers**: `app/controllers/` 内のHTTPリクエストハンドラー
- **Services**: ビジネスロジック（例：BCrypt用の `PasswordService`、トークン用のJWTサービス）
- **Repositories**: trait/実装パターンを使用したデータアクセス層
  - `UserRepository` traitがインターフェースを定義
  - `SlickUserRepository` がPostgreSQL実装を提供
- **Models**: ドメインエンティティとデータベーステーブルマッピング
- **Dependency Injection**: コンポーネントの配線用Guiceモジュール

### データベース
- UUID主キーを使用したPostgreSQL
- スキーママイグレーション用のPlay Evolutions（開発モードで自動適用）
- Evolutionファイルは `backend/conf/evolutions/default/` にあります
- テストユーザー: `tanaka.yuki@example.com` / `SecurePass123!`

### 認証
- HS256アルゴリズムを使用したJWTベース認証
- BCryptパスワードハッシュ（本番環境で10ラウンド、テストで4ラウンド）
- トークン有効期限: 24時間
- リフレッシュトークンサポート

### APIエンドポイント
すべてのAPIエンドポイントは `/v1` プレフィックスを使用します。完全な仕様は `docs/openapi.yaml` を参照してください。

実装済み:
- `/v1/health` - ヘルスチェックエンドポイント
- `/v1/auth/login` - ユーザー認証
- `/v1/auth/refresh` - アクセストークンをリフレッシュ

未実装:
- `/v1/media/*` - メディアCRUD操作
- `/v1/storage/*` - ストレージ管理
- `/v1/jobs/*` - バックグラウンドジョブ監視

## 設定

### 環境変数
ローカル開発用に `.env.example` を `.env` にコピーしてください。主要な変数:
- `DB_*` - PostgreSQL接続設定
- `JWT_SECRET` - JWT署名シークレット
- `AWS_*` - AWS/LocalStackクレデンシャル
- `S3_BUCKET` - MinIO/S3バケット名

### テスト設定
`backend/conf/test.conf` はEvolutionsを無効化し、テスト用に外部サービスをモックします。

## 現在の実装状況
- ✅ 認証システム (login/refreshエンドポイント、JWT)
- ✅ PostgreSQLを使用したユーザーリポジトリ
- ✅ データベーススキーマとマイグレーション
- ✅ Docker Compose開発環境
- ✅ ヘルスチェックエンドポイント
- ✅ APIバージョニング (すべてのエンドポイントが `/v1` プレフィックスを使用)
- ✅ テストカバレッジ設定 (scoverageプラグイン)
- ⏳ フロントエンドは未初期化
- ⏳ メディアアップロード/ストレージ機能
- ⏳ S3/Glacier統合
- ⏳ バックグラウンドジョブ処理

## Media API実装計画

### フェーズ1: データ層 (高優先度)
1. **MediaItemモデル** (`backend/app/models/MediaItem.scala`)
   - UUID主キーを持つコアメディアエンティティ
   - 写真と動画のサポート
   - 重複検出用のファイルハッシュ (SHA-256)
   - メタデータフィールド (EXIF、位置情報、タグ)
   - ストレージステータス追跡 (ACTIVE、ARCHIVING、ARCHIVED、RESTORING)

2. **データベーススキーマ** (`backend/conf/evolutions/default/2.sql`)
   - 包括的なメタデータを持つ `media_items` テーブル
   - タグ関係用の `media_tags` ジャンクションテーブル
   - 重複防止用の `file_hash` へのユニークインデックス
   - パフォーマンス用のuserId、type、uploadedAtへのインデックス

3. **MediaRepository** (`backend/app/repositories/MediaRepository.scala`)
   - 既存のUserRepositoryパターンに従ったCRUD操作
   - 高度なフィルタリング (タイプ、日付範囲、タグ、ストレージステータス)
   - 大規模メディアライブラリ用のページネーションサポート
   - ファイルハッシュによる重複チェック (`findByHash` メソッド)
   - Slick実装 (`backend/app/repositories/impl/SlickMediaRepository.scala`)

### フェーズ2: サービス層 (中優先度)
4. **MediaService** (`backend/app/services/MediaService.scala`)
   - ファイルアップロード処理と検証
   - アップロード中のSHA-256ハッシュ計算
   - S3アップロード前の重複ファイル検出
   - メタデータ抽出 (写真用EXIF、動画用duration)
   - サムネイルとプレビュー生成
   - S3操作用のStorageServiceとの統合

5. **StorageService** (`backend/app/services/StorageService.scala`)
   - S3/MinIOファイル操作
   - セキュアダウンロード用のプリサインドURL生成
   - Glacierアーカイブと復元ワークフロー
   - ストレージクラス管理 (STANDARD → GLACIER → DEEP_ARCHIVE)

6. **MetadataExtractorService** (`backend/app/services/MetadataExtractorService.scala`)
   - 画像からのEXIFデータ抽出
   - 動画メタデータ抽出 (duration、解像度、コーデック)
   - GPS座標処理
   - カメラとレンズ情報の解析

### フェーズ3: コントローラー層 (中優先度)
7. **MediaController** (`backend/app/controllers/MediaController.scala`)
   - `GET /v1/media` - フィルタリングとページネーションを含むメディア一覧
   - `POST /v1/media` - 新しいメディアファイルをアップロード (単一)
   - `POST /v1/media/batch` - バッチアップロード (フェーズ1: 並列呼び出し、フェーズ2: ZIP処理)
   - `GET /v1/media/:id` - 特定のメディア詳細を取得
   - `PUT /v1/media/:id` - メディアメタデータを更新
   - `DELETE /v1/media/:id` - メディアを削除
   - `GET /v1/media/:id/download` - 品質オプション付きダウンロード
   - `POST /v1/media/:id/archive` - Glacierへアーカイブ
   - `POST /v1/media/:id/restore` - Glacierから復元

8. **ファイルアップロード処理**
   - 大容量ファイル用のマルチパート処理 (最大50GB)
   - ファイルタイプ検証:
     - 画像: JPEG、PNG、HEIC、HEIF、TIFF、BMP
     - RAWフォーマット: CR2、NEF、ARW、RAF、ORF、DNG等
     - 動画: MP4、MOV、AVI、MKV (4K/8K解像度を含む)
   - アップロードストリーム上でのSHA-256ハッシュ計算 (GB当たり0.5-4秒)
   - 重複検出ワークフロー:
     1. アップロード中にファイルハッシュを計算
     2. データベースで既存のハッシュをチェック
     3. 重複が存在する場合、既存のメディアメタデータを返す
     4. 新規の場合、S3アップロードとメタデータ処理に進む
   - サイズ制限: ファイル当たり最大50GB
   - ストリーミング処理による一時ファイル管理

### フェーズ4: 追加機能 (低優先度)
9. **バックグラウンドジョブシステム**
   - 非同期操作用のジョブモデル (アーカイブ、サムネイル生成、バッチ処理)
   - バックグラウンドタスク監視用のJobController (`GET /v1/jobs/:id`)
   - Playのアクターシステムまたは外部キューとの統合
   - 進捗追跡付きバッチアップロードZIP処理

10. **アップロード進捗と監視**
    - フロントエンド用のストリーミングアップロード進捗
    - リアルタイム更新用のWebSocketまたはServer-Sent Events
    - バッチアップロード進捗用のポーリングエンドポイント
    - ハッシュベースの重複検出によるアップロード再開処理

11. **高度な検索とメタデータ**
    - 説明とタグ全体の全文検索
    - GPSベースの位置検索 (半径クエリ)
    - カメラ機器フィルタリング (ブランド、モデル、レンズ)
    - タイムゾーンサポート付き日付範囲クエリ
    - EXIFメタデータ検索 (ISO、絞り、シャッタースピード)

12. **テスト戦略**
    - すべてのサービスとリポジトリのユニットテスト
    - ファイルアップロード/ダウンロードフローの統合テスト
    - 外部依存関係なしのテスト用モックS3サービス
    - 大容量ファイル操作のパフォーマンステスト (最大50GB)
    - 同時アップロードのロードテスト

### 実装依存関係
**必要なSBT依存関係** (`backend/build.sbt` に追加):
```scala
// S3操作用AWS SDK
"software.amazon.awssdk" % "s3" % "2.21.29",
"software.amazon.awssdk" % "glacier" % "2.21.29",

// 画像処理とメタデータ抽出
"org.apache.commons" % "commons-imaging" % "1.0.0-alpha5",
"com.drewnoakes" % "metadata-extractor" % "2.18.0",

// ファイルタイプ検出
"org.apache.tika" % "tika-core" % "2.9.1",

// サムネイル生成
"net.coobird" % "thumbnailator" % "0.4.19",

// 重複検出用ファイルハッシング
"commons-codec" % "commons-codec" % "1.16.0",

// 動画メタデータ抽出
"org.bytedeco" % "ffmpeg-platform" % "6.0-1.5.9"
```

### ファイルストレージ構造
```
s3://pixvault-media/
├── users/{userId}/
│   └── media/{mediaId}/
│       ├── original.{ext}     # オリジナルファイル (最大50GB)
│       ├── preview.{ext}      # Web最適化版
│       └── thumbnail.webp     # WebPサムネイル (200x200)
└── batch/{batchId}/           # バッチ処理用一時ZIPファイル
    └── upload.zip
```

### S3ライフサイクル設定
```yaml
# 自動ストレージクラス遷移
Rules:
  - Id: "MediaLifecycle"
    Status: Enabled
    Filter:
      Prefix: "users/"
    Transitions:
      - Days: 30          # Standard → Standard-IA
        StorageClass: STANDARD_IA
      - Days: 90          # Standard-IA → Glacier
        StorageClass: GLACIER  
      - Days: 365         # Glacier → Deep Archive
        StorageClass: DEEP_ARCHIVE
    
  - Id: "BatchCleanup"
    Status: Enabled
    Filter:
      Prefix: "batch/"
    Expiration:
      Days: 7             # 7日後にバッチファイルを自動削除
```

### 実装順序
1. ハッシュフィールドを含むモデルとデータベーススキーマ (フェーズ1: 項目1-2)
2. 重複チェックを含むリポジトリ層 (フェーズ1: 項目3)
3. ハッシュ計算を含むコアサービス (フェーズ2: 項目4-5)
4. 重複検出を含む基本的なCRUDエンドポイント (フェーズ3: 項目7)
   - **効率的なCRUD実装順序:**
     1. `POST /v1/media` - CREATE (最も複雑、ファイル処理を含む)
     2. `GET /v1/media/:id` - READ single (作成されたデータを確認)
     3. `GET /v1/media` - READ list (ページネーション、フィルタリング)
     4. `PUT /v1/media/:id` - UPDATE (メタデータのみ、より単純)
     5. `DELETE /v1/media/:id` - DELETE (リスクを最小化するため最後)
5. ファイル処理サービス (フェーズ2: 項目6)
6. 高度なエンドポイント (フェーズ3: 項目7 - download、archive、restore)
7. バックグラウンドジョブと監視 (フェーズ4)

### 重複検出のメリット
- **ストレージコスト削減**: 冗長なS3ストレージコストを排除
- **アップロードパフォーマンス**: 重複ファイルに対する即座のレスポンス (S3アップロード不要)
- **帯域幅節約**: 重複アップロードのネットワークトラフィックを削減
- **ユーザー体験**: 既に保存されているファイルの高速アップロード完了
- **データ重複排除**: すべてのユーザー間での効率的なストレージ利用

### 将来のAI機能の準備
**データベーススキーマ拡張** (必要時に追加):
```sql
-- AI分析結果
ALTER TABLE media_items ADD COLUMN ai_tags JSONB;
ALTER TABLE media_items ADD COLUMN face_embeddings JSONB;
ALTER TABLE media_items ADD COLUMN object_detection JSONB;
ALTER TABLE media_items ADD COLUMN analyzed_at TIMESTAMP;

-- AI処理キュー
CREATE TABLE ai_analysis_jobs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  media_id UUID REFERENCES media_items(id),
  job_type VARCHAR(50) NOT NULL,
  status VARCHAR(20) DEFAULT 'pending',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  completed_at TIMESTAMP
);
```

**サービスアーキテクチャの準備**:
- 将来のML統合用の `AIAnalysisService` インターフェース
- 計算コストの高い操作用の非同期処理キュー
- 外部AIサービスコールバック用のWebhookエンドポイント

### 大容量ファイル用のパフォーマンス設定
**アプリケーション設定** (`backend/conf/application.conf`):
```scala
# 大容量ファイルアップロード設定
play.http.parser.maxMemoryBuffer = 128MB
play.http.parser.maxDiskBuffer = 50GB
play.server.http.idleTimeout = 1800s      # 30分
play.server.akka.requestTimeout = 1800s

# ストリーミング設定
akka.http.server.parsing.max-content-length = 50GB
akka.http.server.request-timeout = 1800s

# データベース接続プール
play.db.default.hikaricp.maximumPoolSize = 20
play.db.default.hikaricp.minimumIdle = 5

## 開発のヒント
- バックエンドはPlay Frameworkのホットリロードを使用 - 変更は自動的に適用されます
- データベースマイグレーションは開発モードで自動的に実行されます
- エンドポイントのテストには `api-client/` のBruno APIクライアントを使用
- 詳細なログは `backend/logs/application.log` を確認

## Gitコミットメッセージ規約
Conventional Commits仕様に従ってください:
- `feat:` 新機能
- `fix:` バグ修正
- `docs:` ドキュメント変更
- `style:` コードスタイル変更 (フォーマット、セミコロンの欠落など)
- `refactor:` コードリファクタリング
- `test:` テストの追加または更新
- `chore:` メンテナンスタスク、依存関係の更新

例:
- `feat: add user profile image upload`
- `fix: resolve JWT token expiration issue`
- `docs: update API documentation for media endpoints`