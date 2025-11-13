package org.china;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class App extends Application {

    private final OllamaClient client = new OllamaClient();
    private final AtomicReference<OllamaClient.StreamHandle> current = new AtomicReference<>(null);

    private final DatabaseClient dbClient = new DatabaseClient();

    @Override
    public void start(Stage stage) {
        TextField server = new TextField(client.defaultServer());
        server.setPromptText("http://127.0.0.1:11434");
        server.setPrefColumnCount(24);

        TextField model = new TextField("qwen3:1.7b");
        model.setPrefColumnCount(16);

        Button test = new Button("Testar");
        Button send = new Button("Enviar");
        Button stop = new Button("Parar");
        stop.setDisable(true); 

        Label status = new Label("Pronto.");
        status.setStyle("-fx-text-fill: #3a3a3a;");

        TextArea chat = new TextArea();
        chat.setEditable(false);
        chat.setWrapText(true);
        chat.setPrefRowCount(18);

        TextArea prompt = new TextArea();
        prompt.setPromptText("Digite o comando em português (CRUD) e pressione Enter (Shift+Enter = nova linha)");
        prompt.setWrapText(true);
        prompt.setPrefRowCount(4);

        HBox top = new HBox(8, new Label("Server:"), server, new Label("Model:"), model, test, send, stop);
        top.setPadding(new Insets(10));
        top.setStyle("-fx-background-color:#f4f6f8; -fx-border-color:#e1e5ea; -fx-border-width:0 0 1 0;");

        VBox root = new VBox(10, top, chat, new Label("Prompt:"), prompt, status);
        root.setPadding(new Insets(10));

        test.setOnAction(e -> {
            log(status, "Testando conexão...");
            disableButtons(send, stop, true, true);
            client.ping(server.getText().trim(), ok -> {
                Platform.runLater(() -> {
                    log(status, ok ? "Conexão OK." : "Falha ao conectar.");
                    disableButtons(send, stop, false, true);
                });
            });
        });

        Runnable doSend = () -> {
            String srv = server.getText().trim();
            String mdl = model.getText().trim();
            String msg = prompt.getText().trim();
            if (msg.isEmpty()) return;

            append(chat, "\n[" + LocalTime.now().withNano(0) + "] Você: " + msg + "\n");
            prompt.clear();
            disableButtons(send, stop, true, true);
            log(status, "Gerando SQL e executando...");

            client.generateSqlCommand(
                    srv,
                    mdl,
                    msg,
                    sqlJson -> {
                        String sql = sqlJson.path("sql").asText("").trim();
                        String kind = sqlJson.path("kind").asText("update").trim();

                        if (sql.isEmpty()) {
                            Platform.runLater(() -> {
                                append(chat, "[Erro] Modelo não retornou SQL válido.\n");
                                log(status, "Erro ao gerar SQL.");
                                disableButtons(send, stop, false, true);
                            });
                            return;
                        }

                        try {
                            DatabaseClient.DbResult result = dbClient.execute(sql, kind);

                            Platform.runLater(() -> {
                                append(chat, "SQL gerado:\n" + sql + "\n");
                                append(chat, "Resultado:\n" + result.getText() + "\n");
                                log(status, "Comando executado com sucesso.");
                                disableButtons(send, stop, false, true);
                            });

                        } catch (Exception ex) {
                            Platform.runLater(() -> {
                                append(chat, "SQL gerado:\n" + sql + "\n");
                                append(chat, "[Erro ao executar no MySQL] " + ex.getMessage() + "\n");
                                log(status, "Erro no MySQL.");
                                disableButtons(send, stop, false, true);
                            });
                        }
                    },
                    err -> Platform.runLater(() -> {
                        append(chat, "\n[Erro ao falar com o Ollama] " + err.getMessage() + "\n");
                        log(status, "Erro na chamada ao Ollama.");
                        disableButtons(send, stop, false, true);
                    })
            );
        };

        send.setOnAction(e -> doSend.run());

        prompt.setOnKeyPressed(ke -> {
            if (ke.getCode() == KeyCode.ENTER && !ke.isShiftDown()) {
                doSend.run();
                ke.consume();
            }
        });

        stop.setOnAction(e -> {
            OllamaClient.StreamHandle h = current.getAndSet(null);
            if (h != null) {
                h.cancel();
                append(chat, "\n[Cancelado]\n");
            }
            disableButtons(send, stop, false, true);
            log(status, "Cancelado.");
        });

        stage.setTitle("Ollama ChatFX (SQL MySQL Agent)");
        stage.setScene(new Scene(root, 1000, 680));
        stage.show();
    }

    private void append(TextArea area, String text) {
        area.appendText(text);
        area.setScrollTop(Double.MAX_VALUE);
    }

    private void log(Label status, String text) {
        status.setText(text);
        System.out.println("[UI] " + text);
    }

    private void disableButtons(Button send, Button stop, boolean sendDisabled, boolean stopDisabled) {
        send.setDisable(sendDisabled);
        stop.setDisable(stopDisabled);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
