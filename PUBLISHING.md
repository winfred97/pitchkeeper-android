# 发布清单

这份清单用于把「音准」发布到个人 GitHub，并生成可下载的正式 APK。

## 1. 登录自己的 GitHub

不要向任何人发送 GitHub 密码、验证码或访问令牌。在本机终端由账号本人执行：

```bash
gh auth login --hostname github.com --git-protocol https --web
```

完成后检查：

```bash
gh auth status
```

如果电脑同时用于公司项目，本仓库只配置仓库级 Git 身份，不修改全局配置，也不使用公司邮箱。

## 2. 添加赞助收款码

把本人有效的支付宝或微信个人收款码保存为：

```text
docs/sponsor/coffee-qr.png
```

要求见 [docs/sponsor/README.md](docs/sponsor/README.md)。然后删除 `README.md` 中“请我喝杯咖啡”区块前后的 HTML 注释标记：

```html
<!-- 收款码到位后启用此区块...
...
-->
```

发布前必须在另一台设备实际扫码，确认收款方和付款页面正确。二维码公开后难以撤回。

## 3. 创建 Release 签名

只由项目所有者在自己的电脑上执行，密码不要发给 AI 或提交到仓库：

```bash
mkdir -p "$HOME/.pitchkeeper-signing"
chmod 700 "$HOME/.pitchkeeper-signing"
keytool -genkeypair -v \
  -keystore "$HOME/.pitchkeeper-signing/pitchkeeper-release.jks" \
  -alias pitchkeeper \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

请把密钥文件和密码分别备份到安全位置。丢失密钥后，已安装用户将无法正常升级同一应用。

构建前在当前终端设置四个环境变量，值必须与创建密钥时一致：

```bash
export PITCHKEEPER_STORE_FILE="$HOME/.pitchkeeper-signing/pitchkeeper-release.jks"
export PITCHKEEPER_STORE_PASSWORD='你的密钥库密码'
export PITCHKEEPER_KEY_ALIAS='pitchkeeper'
export PITCHKEEPER_KEY_PASSWORD='你的密钥密码'
```

然后构建：

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

正式 APK 位于 `app/build/outputs/apk/release/app-release.apk`。上传前用 `apksigner verify` 确认签名。

## 4. 创建本地 Git 仓库

```bash
git init -b main
git config user.name "你的 GitHub 用户名"
git config user.email "你的 GitHub noreply 邮箱"
git add .
git status
```

确认暂存区中没有 `local.properties`、`*.jks`、`*.keystore`、`keystore.properties`、APK/AAB 或公司凭据，再提交。

## 5. 创建 GitHub 仓库

由账号本人登录后，可使用本机 `gh` 创建公开仓库并推送。仓库名建议使用 `pitchkeeper-android`。

公开 Release 下载要求仓库为 Public；Private 仓库的 Release 只对授权成员可见。

## 6. 发布版本

- 标签：`v0.1.0`
- 标题：`音准 v0.1.0`
- APK：重命名为 `Pitchkeeper-v0.1.0.apk`，避免 GitHub 将中文资产名替换为连字符
- 同时上传 SHA-256 校验文件
- Release 说明使用 `CHANGELOG.md`
- 明确标注尚未完成实体手机人声精度验收

## 7. 发布后检查

- 未登录浏览器能打开仓库和 Release。
- APK 链接可以直接下载。
- 文件大小正常，`apksigner verify --print-certs` 显示发布者证书而非 Android Debug。
- README 截图、隐私说明、许可证和赞助二维码均可访问。
- 真机安装后能申请麦克风权限、开始与停止测量。
- 公开页面没有公司邮箱、真实姓名、手机号、密钥或本机路径。
