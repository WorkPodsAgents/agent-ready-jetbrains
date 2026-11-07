# Agent Ready Website Audit for JetBrains IDEs

Run an SEO and AI search audit of any web page without leaving IntelliJ IDEA, WebStorm, PyCharm or any other JetBrains IDE (2025.1 or newer). No account or API key needed.

Install it from the [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/35872-agent-ready-website-audit), then open **Tools | Agent Ready Website Audit**:

- **SEO Audit of a Page**: indexing, title and meta description, headings, schema markup (JSON-LD), links, image alt text and AI readiness.
- **Check AI Crawler Access**: whether GPTBot, OAI-SearchBot, ClaudeBot, PerplexityBot, Google-Extended and more may read a page.
- **Check robots.txt, Sitemaps and llms.txt**, plus the agent files under /.well-known.

The summary appears as a notification with **Open Report** (a shareable report page kept for 30 days) and **Show Details** (the full result as JSON).

The checks run on the hosted [Agent Ready Website Audit](https://workpods.ai/tools/agent-ready) server at `mcp.workpods.ai`. It fetches only the public page you enter and records the domain and path it checked, never the query string, your IP address or the page text. See the [privacy policy](https://workpods.ai/privacy#agent-ready-mcp).

## Build

```bash
./gradlew buildPlugin    # build/distributions/agent-ready-jetbrains-<version>.zip
./gradlew verifyPlugin   # JetBrains Plugin Verifier against the recommended IDEs
```

Needs JDK 21. Licensed under Apache 2.0.
