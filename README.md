# agent

Ein KI-Agent, von Grund auf in Java gebaut. Das Projekt folgt dem Buch [Build an AI Agent (From Scratch)](https://www.manning.com/books/build-an-ai-agent-from-scratch) von Jungjun Hur und Younghee Song (Manning). Das Buch arbeitet mit Python, hier entsteht dieselbe Architektur in Java.

Spring AI dient nur als Schicht zum LLM-Anbieter, so wie LiteLLM im Buch. Die Agenten-Schleife, die Tools, das Memory und die Multi-Agent-Muster entstehen selbst geschrieben. Der `ChatClient` mit seinen Advisors, dem Memory und der automatischen Tool-Schleife bleibt deshalb außen vor, aufgerufen wird direkt das `ChatModel`.

## Stack

- Java 25
- Spring Boot 4.1 mit Spring MVC für REST-APIs
- Spring AI 2.0 mit OpenAI, Anthropic und Google Gemini
- Gradle 9 (Kotlin-DSL, Wrapper im Repo)

## Voraussetzungen

- JDK 25
- API-Keys für OpenAI, Anthropic und Google Gemini
- Docker für die lokale Websuche (SearXNG)
- Node mit npx für den MCP-Server `mcp-searxng` und den MCP Inspector

## Websuche

Die Websuche läuft über eine lokale SearXNG-Instanz, erreichbar nur auf diesem Rechner:

```bash
docker compose up -d
```

SearXNG leitet Suchanfragen an Suchmaschinen wie Google oder Bing weiter. Einzelne davon drosseln bei vielen Anfragen, dann fehlen ihre Treffer.

## Starten

```bash
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
export GEMINI_API_KEY=...
./gradlew :agent:bootRun
```

Die Anwendung läuft danach auf `http://localhost:8080`. Fehlt einer der drei Schlüssel, bricht der Start mit einer Fehlermeldung ab.

## Bauen und testen

```bash
./gradlew build
```

Die Tests brauchen weder API-Key noch Netz. Tests mit echten Aufrufen laufen getrennt:

```bash
./gradlew :agent:externalTest   # Hugging Face, SearXNG, MCP-Server, ohne Sprachmodell
./gradlew :agent:llmTest        # mit echtem Sprachmodell, verbraucht Kontingent
```

Jeder Live-Test überspringt sich, wenn der nötige Schlüssel fehlt oder SearXNG nicht läuft.

## Module

- `agent`: der Agent selbst
- `mcp-search-server`: ein MCP-Server mit einem Such-Tool über SearXNG, angesprochen über stdio (Buch 3.4.4)

Der Server lässt sich mit dem MCP Inspector ausprobieren:

```bash
docker compose up -d
./gradlew :mcp-search-server:bootJar
npx @modelcontextprotocol/inspector java -jar mcp-search-server/build/libs/mcp-search-server.jar
```

## Lizenz

[MIT](LICENSE). Das Projekt überträgt den MIT-lizenzierten Original-Code zum Buch, [shangrilar/ai-agent-from-scratch](https://github.com/shangrilar/ai-agent-from-scratch) von Jungjun Hur, nach Java. Dessen Copyright-Hinweis steht deshalb mit in der `LICENSE`. Der Buchtext selbst gehört Manning und ist nicht Teil dieses Repos.
