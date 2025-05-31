# PixVault

PixVault は、 Google Photos や iCloud の代替として AWS S3 Glacier を活用した低コストで写真・動画を長期保存します。

## 技術スタック

### フロントエンド

- Vite
- TypeScript
- React 19
- Tailwind CSS 4

### バックエンド

- Scala 3.7
- Play Framework 3.0
- PostgreSQL

### インフラ

#### 開発 (ローカル)

- Docker Compose
- LocalStack
- MinIO

#### 本番

- AWS S3
- Glacier Deep Archive
- Lambda

## ディレクトリ構成

```
PixVault/
|- frontend/            # Vite React
|- backend/             # Play Framework
|- infra/               # インフラの設定・構成
|- docs/                # ドキュメント
|- package.json         # モノレポ管理
`- docker-compose.yaml  # 開発環境の設定
```

## ライセンス

[MIT License](./LICENSE)
