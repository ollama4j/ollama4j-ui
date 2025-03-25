package io.github.ollama4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.exceptions.ToolInvocationException;
import io.github.ollama4j.models.chat.*;
import io.github.ollama4j.models.generate.OllamaStreamHandler;
import io.github.ollama4j.models.response.Model;
import io.github.ollama4j.tools.OllamaToolsResult;
import io.github.ollama4j.tools.ToolFunction;
import io.github.ollama4j.tools.Tools;
import io.github.ollama4j.utils.OptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@SuppressWarnings("ExtractMethodRecommender")
public class Ollama4jUI {
    private static final Logger logger = LoggerFactory.getLogger(OllamaAPI.class);

    private static final String appTitle = "Ollama4j UI";
    private static String ollamaHost = "http://localhost:11434";
    private static List<OllamaChatMessage> history = new ArrayList<>();
    private static boolean isChatInProgress = false;
    private static String selectedModel = null;
    private static boolean useTools = true;
    private static OllamaAPI ollamaAPI;
    private static String cacheDirectory = System.getProperty("user.home") + File.separator + "ollama4j-ui";
    private static String settingsFilePath = cacheDirectory + File.separator + "ollama4j-ui.properties";
    private static final String defaultFileNameForChatExport = cacheDirectory + File.separator + "ollama4j-chat.txt";
    private static JFrame frame = new JFrame(appTitle);
    private static String temperature = "0.75";
    private static String maxTokens = "2048";
    private static String openWeatherMapApiKey = "";
    public static void main(String[] args) throws IOException {
        File cacheDir = new File(cacheDirectory);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        File settingsFile = new File(settingsFilePath);
        if (!settingsFile.exists()) {
            settingsFile.createNewFile();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsFile))) {
                ollamaHost = "http://localhost:11434";
                writer.write("ollamaHost=" + ollamaHost + "\n");
                writer.write("temperature=0.75\n");
                writer.write("maxTokens=2048\n");
                writer.write("openWeatherMapApiKey=your-key-here\n");
            }
        } else {
            Properties properties = new Properties();
            properties.load(new FileReader(settingsFile));
            ollamaHost = properties.getProperty("ollamaHost");
            temperature = properties.getProperty("temperature");
            maxTokens = properties.getProperty("maxTokens");
            openWeatherMapApiKey = properties.getProperty("openWeatherMapApiKey");
        }
        // write default settings to file
        ollamaAPI = new OllamaAPI(ollamaHost);
        ollamaAPI.setRequestTimeoutSeconds(60);
        SwingUtilities.invokeLater(Ollama4jUI::createAndShowGUI);
    }

    private static JPanel getChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());

        JTextArea chatHistory = new JTextArea();
        chatHistory.setEditable(false);
        chatHistory.setLineWrap(true);
        chatHistory.setWrapStyleWord(true);
        JScrollPane chatScrollPane = new JScrollPane(chatHistory);
        chatScrollPane.setPreferredSize(new Dimension(600, 300));

        JPanel chatInputPanel = new JPanel(new BorderLayout());
        JTextField chatInputField = new JTextField();

        JButton sendButton = new JButton("➤");
        JButton clearButton = new JButton("❌");
        JButton exportButton = new JButton("📤");
        sendButton.setToolTipText("Send");
        clearButton.setToolTipText("Clear");
        exportButton.setToolTipText("Export");

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(sendButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(exportButton);

        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("Select Model");
        String[] modelsList;
        try {
            ollamaAPI.listModels().stream().map(Model::getName).forEach(model::addElement);
            modelsList = ollamaAPI.listModels().stream().map(Model::getName).toArray(String[]::new);
        } catch (OllamaBaseException | IOException | URISyntaxException | InterruptedException e) {
            modelsList = new String[] { "No Models Available" };
            logger.error("Error fetching models: {}", e.getMessage());
            model.addElement("No Models Available");
        }

        JComboBox<String> modelDropdown = new JComboBox<>(model);
        modelDropdown.setPreferredSize(new Dimension(100, 30));
        modelDropdown.setToolTipText("Select a model");
        modelDropdown.setSelectedIndex(0);

        if (modelsList.length > 0 && !modelsList[0].equals("No Models")) {
            // modelDropdown.setSelectedIndex(-1);
        } else {
            modelDropdown.setEnabled(false);
        }
        JCheckBox useToolsCheckbox = new JCheckBox("Use Tools");
        useToolsCheckbox.setSelected(true);
        useToolsCheckbox
                .setToolTipText("Enable tools for the model. Note: This uses tool-calling models such as Mistral.");

        useToolsCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                logger.info("Tools enabled");
                useTools = true;
            } else {
                logger.info("Tools disabled");
                useTools = false;
            }
        });

        modelDropdown.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String newModel = (String) e.getItem();
                if (newModel.equals("Select Model")) {
                    selectedModel = null;
                    return;
                }
                int confirm = JOptionPane.showConfirmDialog(frame,
                        "Are you sure you want to select the model: " + newModel + "?", "Confirm Model Selection",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    selectedModel = newModel;
                    logger.info("Selected model: {}", newModel);
                    chatHistory.setText("");
                    history.clear();
                    chatInputPanel.setEnabled(true);
                    chatInputField.setEditable(true);
                    modelDropdown.setToolTipText(selectedModel);
                } else {
                    modelDropdown.setSelectedItem(selectedModel);
                }
            }
        });

        Dimension buttonSize = new Dimension(30, 30);
        sendButton.setPreferredSize(buttonSize);
        clearButton.setPreferredSize(buttonSize);
        exportButton.setPreferredSize(buttonSize);

        JPanel modelSelectionAndToolsPanel = new JPanel();
        modelSelectionAndToolsPanel.setLayout(new BoxLayout(modelSelectionAndToolsPanel, BoxLayout.X_AXIS));
        modelSelectionAndToolsPanel.add(modelDropdown);
        modelSelectionAndToolsPanel.add(useToolsCheckbox);

        JPanel inputAndButtonsPanel = new JPanel(new BorderLayout());
        inputAndButtonsPanel.add(chatInputField, BorderLayout.CENTER);
        inputAndButtonsPanel.add(buttonPanel, BorderLayout.EAST);

        chatInputPanel.add(modelSelectionAndToolsPanel, BorderLayout.NORTH);
        chatInputPanel.add(inputAndButtonsPanel, BorderLayout.SOUTH);

        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        chatPanel.add(chatInputPanel, BorderLayout.SOUTH);

        OllamaStreamingChat ollamaChat = new OllamaStreamingChat();
        ActionListener sendMessage = e -> {
            String message = chatInputField.getText().trim();
            if (!message.isEmpty()) {
                chatHistory.append("You: " + message + "\n");
                isChatInProgress = true;
                sendButton.setText("⌛");
                sendButton.setEnabled(false);
                chatInputField.setEditable(false);
                clearButton.setEnabled(false);
                exportButton.setEnabled(false);

                if (selectedModel == null) {
                    JOptionPane.showMessageDialog(frame, "Please select a model first.", "Error",
                            JOptionPane.ERROR_MESSAGE);
                    chatHistory.setText("");
                    history.clear();
                    sendButton.setText("➤");
                    sendButton.setEnabled(true);
                    chatInputField.setText("");
                    chatInputField.setEnabled(true);
                    isChatInProgress = false;
                    return;
                }

                new Thread(() -> {
                    StringBuilder responseBuffer = new StringBuilder();
                    try {
                        String botLabel = "AI: ";
                        CustomStreamHandler streamHandlerCustom = new CustomStreamHandler(chatHistory, responseBuffer,
                                botLabel);
                        OllamaStreamHandler streamHandler = chunk -> SwingUtilities
                                .invokeLater(() -> streamHandlerCustom.accept(chunk));
                        if (useTools) {
                            history = ollamaChat.chatWithTools(message, history, ollamaAPI, selectedModel,
                                    streamHandler);
                        } else {
                            history = ollamaChat.chat(message, history, ollamaAPI, selectedModel, streamHandler);
                        }
                    } catch (OllamaBaseException | IOException | InterruptedException ex) {
                        SwingUtilities.invokeLater(() -> chatHistory.append("\n[Error] " + ex.getMessage() + "\n"));
                    } catch (ToolInvocationException ex) {
                        throw new RuntimeException(ex);
                    } finally {
                        SwingUtilities.invokeLater(() -> {
                            sendButton.setText("➤");
                            sendButton.setEnabled(true);
                            isChatInProgress = false;
                            chatInputField.setEditable(true);
                            clearButton.setEnabled(true);
                            exportButton.setEnabled(true);
                            chatHistory.append("\n------\n");
                        });
                    }
                }).start();
                chatInputField.setText("");
            }
        };

        sendButton.addActionListener(sendMessage);
        chatInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !isChatInProgress) {
                    sendMessage.actionPerformed(null);
                }
            }
        });

        clearButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(frame, "Are you sure you want to clear the chat?",
                    "Confirm Clear", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                chatHistory.setText("");
                history.clear();
            }
        });

        exportButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(defaultFileNameForChatExport));
            int returnValue = fileChooser.showSaveDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    writer.write(chatHistory.getText());
                    JOptionPane.showMessageDialog(frame, "Chat exported successfully!");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(frame, "Error exporting chat: " + ex.getMessage());
                }
            }
        });
        return chatPanel;
    }

    private static JPanel getSettingsPanel() {
        JPanel settingsPanel = new JPanel(new BorderLayout());
        String[] settingsColumnNames = { "Setting", "Value" };
        DefaultTableModel settingsTableModel = new DefaultTableModel(settingsColumnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        settingsPanel.add(new JLabel("Ollama4j UI Configuration"), BorderLayout.NORTH);
        JTable settingsTable = new JTable(settingsTableModel);
        settingsTable.setToolTipText("Config loaded from " + settingsFilePath);
        settingsTableModel.addRow(new Object[] { "Ollama Host", ollamaHost });
        settingsTableModel.addRow(new Object[] { "Temperature", temperature });
        settingsTableModel.addRow(new Object[] { "Max Tokens", maxTokens });

        settingsPanel.add(new JScrollPane(settingsTable), BorderLayout.CENTER);
        // settingsPanel.add(settingsButtonPanel, BorderLayout.SOUTH);
        return settingsPanel;
    }

    private static JPanel getModelsPanel() {
        JPanel modelsPanel = new JPanel(new BorderLayout());
        String[] columnNames = { "Model", "Version", "Size", "Parameter Size", "Quantization Level", "Format" };
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(tableModel);

        try {
            ollamaAPI.listModels().forEach(model -> {
                String size = humanReadableSize(model.getSize());
                tableModel.addRow(new Object[] { model.getModelName(), model.getModelVersion(), size,
                        model.getModelMeta().getParameterSize(), model.getModelMeta().getQuantizationLevel(),
                        model.getModelMeta().getFormat() });
            });
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage());
        }
        table.setDefaultEditor(Object.class, null);
        modelsPanel.add(new JLabel("Downloaded Models"), BorderLayout.NORTH);
        modelsPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        return modelsPanel;
    }

    private static JPanel getDownloadableModelsPanel() {
        JPanel downloadableModelsPanel = new JPanel(new BorderLayout());
        String[] columnNames = { "Model", "Description", "Pull Count", "Last Updated" };
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(tableModel);

        try {
            ollamaAPI.listModelsFromLibrary().forEach(model -> {
                tableModel.addRow(new Object[] { model.getName(), model.getDescription(), model.getPullCount(),
                        model.getLastUpdated() });
            });
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage());
        }
        table.setDefaultEditor(Object.class, null);
        downloadableModelsPanel.add(new JLabel("Model Library"), BorderLayout.NORTH);
        downloadableModelsPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        return downloadableModelsPanel;
    }

    private static void createAndShowGUI() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        DefaultListModel<String> listModel = new DefaultListModel<>();
        listModel.addElement("Chat");
        listModel.addElement("Models");
        listModel.addElement("Model Library");
        listModel.addElement("Settings");

        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(150, frame.getHeight()));
        leftPanel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new CardLayout());

        JPanel chatPanel = getChatPanel();
        JPanel modelsPanel = getModelsPanel();
        JPanel settingsPanel = getSettingsPanel();
        JPanel downloadableModelsPanel = getDownloadableModelsPanel();

        rightPanel.add(chatPanel, "Chat");
        rightPanel.add(modelsPanel, "Models");
        rightPanel.add(downloadableModelsPanel, "Model Library");
        rightPanel.add(settingsPanel, "Settings");

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                CardLayout cl = (CardLayout) rightPanel.getLayout();
                cl.show(rightPanel, list.getSelectedValue());
            }
        });

        frame.add(leftPanel, BorderLayout.WEST);
        frame.add(rightPanel, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    private static String humanReadableSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int unit = 1024;
        String[] sizes = { "KB", "MB", "GB", "TB", "PB", "EB" };
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        return String.format("%.2f %s", bytes / Math.pow(unit, exp), sizes[exp - 1]);
    }

    static class CustomStreamHandler implements OllamaStreamHandler {
        private final JTextArea chatHistory;
        private final StringBuilder responseBuffer;
        private final String botLabel;

        public CustomStreamHandler(JTextArea chatHistory, StringBuilder responseBuffer, String botLabel) {
            this.chatHistory = chatHistory;
            this.responseBuffer = responseBuffer;
            this.botLabel = botLabel;
            this.responseBuffer.append(botLabel);
            chatHistory.setText(chatHistory.getText() + botLabel);
        }

        @Override
        public void accept(String message) {
            String substr = message.substring(responseBuffer.length() - botLabel.length());
            responseBuffer.append(substr);
            chatHistory.setText(chatHistory.getText() + substr);
            chatHistory.setCaretPosition(chatHistory.getDocument().getLength());
        }
    }

    static class OllamaStreamingChat {
        public List<OllamaChatMessage> chat(String message, List<OllamaChatMessage> history, OllamaAPI ollamaAPI,
                String model, OllamaStreamHandler streamHandler)
                throws OllamaBaseException, IOException, InterruptedException {
            OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model).withMessages(history);
            OllamaChatRequest requestModel = builder.withMessage(OllamaChatMessageRole.USER, message)
                    .build();
            OllamaChatResult chatResult = ollamaAPI.chat(requestModel, streamHandler);
            return chatResult.getChatHistory();
        }

        public List<OllamaChatMessage> chatWithTools(String message, List<OllamaChatMessage> history,
                OllamaAPI ollamaAPI,
                String model, OllamaStreamHandler streamHandler)
                throws OllamaBaseException, IOException, InterruptedException, ToolInvocationException {
            Tools.ToolSpecification weatherToolSpec = getWeatherToolSpec(openWeatherMapApiKey);
            ollamaAPI.registerTool(weatherToolSpec);
            for (OllamaToolsResult.ToolResult r : ollamaAPI.generateWithTools(model, new Tools.PromptBuilder()
                    .withToolSpecification(weatherToolSpec)
                    .withPrompt(message + "\n\nMake sure you respond ONLY in a valid JSON format.")
                    .build(), new OptionsBuilder().build()).getToolResults()) {
                String systemResponse = (String) r.getResult();
                OllamaChatMessage userMessage = new OllamaChatMessage(OllamaChatMessageRole.USER, message);
                OllamaChatMessage systemMessage = new OllamaChatMessage(OllamaChatMessageRole.SYSTEM, systemResponse);
                history.add(userMessage);
                history.add(systemMessage);
                streamHandler.accept(systemResponse);
            }
            return history;
        }
    }

    public static Tools.ToolSpecification getWeatherToolSpec(String openWeatherMapApiKey) {
        return Tools.ToolSpecification.builder()
                .functionName("weather-reporter")
                .functionDescription(
                        "You are a tool who simply finds the city name from the user's message input/query about weather.")
                .toolFunction(new WeatherToolFunction(openWeatherMapApiKey))
                .toolPrompt(
                        Tools.PromptFuncDefinition.builder()
                                .type("prompt")
                                .function(
                                        Tools.PromptFuncDefinition.PromptFuncSpec.builder()
                                                .name("get-city-name")
                                                .description("Get the city name")
                                                .parameters(
                                                        Tools.PromptFuncDefinition.Parameters.builder()
                                                                .type("object")
                                                                .properties(
                                                                        Map.of(
                                                                                "cityName",
                                                                                Tools.PromptFuncDefinition.Property
                                                                                        .builder()
                                                                                        .type("string")
                                                                                        .description(
                                                                                                "The name of the city. e.g. Bengaluru")
                                                                                        .required(true)
                                                                                        .build()))
                                                                .required(java.util.List.of("cityName"))
                                                                .build())
                                                .build())
                                .build())
                .build();
    }
}

class WeatherToolFunction implements ToolFunction {
    private final String apiKey;

    public WeatherToolFunction(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public Object apply(Map<String, Object> arguments) {
        String city = (String) arguments.get("cityName");
        System.out.println("Finding weather for city: " + city);

        String url = String.format("https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric", city,
                this.apiKey);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response.body());
                JsonNode main = root.path("main");
                double temperature = main.path("temp").asDouble();
                String description = root.path("weather").get(0).path("description").asText();

                return String.format("Weather in %s: %.1f°C, %s", city, temperature, description);
            } else {
                return "Could not retrieve weather data for " + city + ". Status code: " + response.statusCode();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Error retrieving weather data: " + e.getMessage();
        }
    }
}