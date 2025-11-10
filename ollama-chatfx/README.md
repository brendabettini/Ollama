# Ollama ChatFX UI (Java 17) — FIX

Correção: removido `timeout(Duration.ofSeconds(0))` que causava **IllegalArgumentException: PT0S** no Java 17.

## Rodar
```cmd
mvn -q clean javafx:run
```
Se a janela no Windows ficar preta:
```cmd
mvn -q javafx:run -Dprism.order=sw
```

## Uso
- Server: `http://127.0.0.1:11434`
- Model: `llama3.2:1b` (o seu instalado) ou outro existente.
- Enter envia, Shift+Enter nova linha. Botões: Testar, Enviar, Parar.
