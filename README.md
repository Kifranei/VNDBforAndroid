# VNDB for Android

非官方 VNDB 客户端，界面使用 [Miuix](https://github.com/compose-miuix-ui/miuix)（HyperOS 风格）。

数据来自 [VNDB API v2 (Kana)](https://api.vndb.org/kana)。

本项目以 [Apache License 2.0](LICENSE) 授权。VNDB 条目数据遵循 [VNDB Data License](https://vndb.org/d7)。

## 功能

- 发现：随机摘录、高分 / 最近发售 / 最多评分
- 搜索：作品、角色、制作组、职员、标签
- 作品筛选：语言、平台、评分、时长、发售区间、`devstatus`、`has_description`
- 作品详情：评分、标签、简介、截图、角色、发行、相关作品
- 角色 / 制作组 / 职员 / 标签详情
- 本地收藏与浏览记录
- 可选 VNDB Token，同步个人列表（`POST /ulist`）
- 标题语言、敏感封面、剧透等级、浅色 / 深色 / 莫奈主题

## 构建

需要 JDK 17+ 与 Android SDK。仓库默认使用本机：

```
C:\Program Files\Microsoft\jdk-21.0.7.6-hotspot
C:\Users\Croilan\AppData\Local\Android\Sdk
```

```bat
gradlew.bat :app:assembleDebug
```

## Token

在 [vndb.org/u/tokens](https://vndb.org/u/tokens) 创建应用 Token，填入设置页即可读取个人列表。
