# 更新手順

## ReVanced Manager向けJSONの配布

登録用URLは `https://raw.githubusercontent.com/roflsunriz/povo-2.0-revanced/main/patches.json` です。

release workflowは、リリースの公開に成功した後、生成した `patches.json` を既定ブランチへ同期します。最新リリース以外の再実行では同期せず、JSONの版不一致や同時更新を検出した場合は失敗します。失敗時は最新リリースのJSONと既定ブランチの差分を確認し、最新のJSONだけを再反映してください。RVPの配布先はGitHub Releasesのままです。

## 前提

具体的な手順・構成・知見は [findings.md](<findings.md#update-1>) に移動しました。着手前に該当条件を確認してください。

## 1. 依存関係を更新する

具体的な手順・構成・知見は [findings.md](<findings.md#update-2>) に移動しました。着手前に該当条件を確認してください。

## 2. povo 新版を調査する

具体的な手順・構成・知見は [findings.md](<findings.md#update-3>) に移動しました。着手前に該当条件を確認してください。

## 3. ビルドする

具体的な手順・構成・知見は [findings.md](<findings.md#update-4>) に移動しました。着手前に該当条件を確認してください。

## 4. 複数世代へ適用する

具体的な手順・構成・知見は [findings.md](<findings.md#update-5>) に移動しました。着手前に該当条件を確認してください。

## 5. バージョンと文書を更新する

具体的な手順・構成・知見は [findings.md](<findings.md#update-6>) に移動しました。着手前に該当条件を確認してください。

## 6. リリースする

具体的な手順・構成・知見は [findings.md](<findings.md#update-7>) に移動しました。着手前に該当条件を確認してください。

## ロールバック

具体的な手順・構成・知見は [findings.md](<findings.md#update-8>) に移動しました。着手前に該当条件を確認してください。

## Dependabot PR の更新



Dependabot version update は毎月28日00:00、検証済み PR の自動マージは同日18:00（いずれも `Asia/Tokyo`）にまとめます。28日より前に自動マージせず、現在の head SHA と全 PR チェックを月末処理で再確認します。
具体的な手順・構成・知見は [findings.md](<findings.md#update-9>) に移動しました。着手前に該当条件を確認してください。

## Dependabotラベルの維持

具体的な手順・構成・知見は [findings.md](<findings.md#update-10>) に移動しました。着手前に該当条件を確認してください。
