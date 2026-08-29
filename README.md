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

Die Tests brauchen keinen API-Key. Tests mit echten Aufrufen tragen das Tag `llm` und laufen getrennt über `./gradlew :agent:llmTest`, dort braucht jeder Test den Schlüssel des Anbieters, den er aufruft.

## Lizenz

[MIT](LICENSE). Das Projekt überträgt den MIT-lizenzierten Original-Code zum Buch, [shangrilar/ai-agent-from-scratch](https://github.com/shangrilar/ai-agent-from-scratch) von Jungjun Hur, nach Java. Dessen Copyright-Hinweis steht deshalb mit in der `LICENSE`. Der Buchtext selbst gehört Manning und ist nicht Teil dieses Repos.
