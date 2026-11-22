# 公式资源来源

- 排版引擎：`@mathjax/src` 4.1.3，MathJax Consortium，Apache-2.0。
- 数学字形数据：`@mathjax/mathjax-newcm-font` 4.1.3；该发布包声明许可为 Apache-2.0。
- MathJax 字形数据版权补充：Copyright (c) 2024 MathJax, Inc. (www.mathjax.org)。该包的 `def/mathjax-newcm.ts` 指定底层 `NewCMMath-Book.otf`（New Computer Modern，Antonis Tsolomitis）；保留其 [GUST 字体许可](https://ctan.org/license/gfl)、LPPL 1.3c 及 [上游许可边界](https://ctan.org/pkg/newcomputermodern)，全文见 `GUST-FONT-LICENSE.txt`、`LPPL-1.3c.txt` 和 `NewComputerModern-License.txt`。未捆绑 OTF／WOFF 或 Apple 字体，不将生成字形路径重新声明为项目 MIT 代码。
- 精确下载来源及完整性摘要固定于 `Windows/package-lock.json`；许可全文见同目录 `MathJax-LICENSE.txt`。
- SVG 的字形为内置路径，不包含远程字体、脚本、图片或运行时 CDN；Mac PDF 由项目锁定的 Electron 45.1.0 从同一 SVG 生成。
- [MathJax SVG 输出说明](https://docs.mathjax.org/en/latest/output/svg.html)；[固定引擎版本](https://github.com/mathjax/MathJax-src/releases/tag/4.1.3)。上游字体声明于 2026-11-22 从 CTAN／GUST／LaTeX Project 官方站点读取，原文保留。
- 词库公式来自项目审核文本；生成仅改变显示资源，不修改词库、公式含义或音频。

资源生成：设置 `MATH_VOCAB_VALIDATION_DIRECTORY` 为独立 codextest 验证目录，执行 `node Scripts/generate_math_assets.mjs`。
资源核验：`node Scripts/generate_math_assets.mjs --verify`。
日常构建使用仓库中现成资源，不运行 MathJax，也无需重新生成 PDF。
