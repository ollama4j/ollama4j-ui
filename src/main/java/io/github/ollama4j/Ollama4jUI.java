package io.github.ollama4j;

import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.models.chat.*;
import io.github.ollama4j.models.generate.OllamaStreamHandler;
import io.github.ollama4j.models.response.Model;
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
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ExtractMethodRecommender")
public class Ollama4jUI {
    private static final String appTitle = "Ollama4j UI";
    private static String ollamaHost = "http://localhost:11434";
    private static List<OllamaChatMessage> history = new ArrayList<>();
    private static final String defaultFileNameForChatExport = "ollama4j-ui-chat.txt";
    private static boolean isChatInProgress = false;
    private static String selectedModel = null;
    static OllamaAPI ollamaAPI;

    private static final Logger logger = LoggerFactory.getLogger(OllamaAPI.class);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Ollama4jUI::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame(appTitle);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        DefaultListModel<String> listModel = new DefaultListModel<>();
        listModel.addElement("Chat");
        listModel.addElement("Models");
        listModel.addElement("Settings");

        JList<String> list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(150, frame.getHeight()));
        leftPanel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new CardLayout());
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

        ollamaAPI = new OllamaAPI(ollamaHost);
        String[] modelsList = null;
        try {
            modelsList = ollamaAPI.listModels().stream().map(Model::getName).toArray(String[]::new);
        } catch (OllamaBaseException | IOException | URISyntaxException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        JComboBox<String> modelDropdown = new JComboBox<>(modelsList);
        modelDropdown.setPreferredSize(new Dimension(100, 30));
        modelDropdown.setToolTipText("Select a model");
        modelDropdown.setSelectedIndex(-1);

        modelDropdown.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String newModel = (String) e.getItem();
                int confirm = JOptionPane.showConfirmDialog(frame, "Are you sure you want to select the model: " + newModel + "?", "Confirm Model Selection", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    selectedModel = newModel;
                    logger.info("Selected model: {}", newModel);
                    chatHistory.setText("");
                    history.clear();
                    chatInputPanel.setEnabled(true);
                    chatInputField.setEditable(true);
                    modelDropdown.setToolTipText(selectedModel);
                    ollamaAPI = new OllamaAPI(ollamaHost);
                } else {
                    modelDropdown.setSelectedItem(selectedModel);
                }
            }
        });

        Dimension buttonSize = new Dimension(30, 30);
        sendButton.setPreferredSize(buttonSize);
        clearButton.setPreferredSize(buttonSize);
        exportButton.setPreferredSize(buttonSize);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(sendButton);
        buttonPanel.add(clearButton);
        buttonPanel.add(exportButton);

        chatInputPanel.add(modelDropdown, BorderLayout.WEST);
        chatInputPanel.add(chatInputField, BorderLayout.CENTER);
        chatInputPanel.add(buttonPanel, BorderLayout.EAST);

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
                    JOptionPane.showMessageDialog(frame, "Please select a model first.", "Error", JOptionPane.ERROR_MESSAGE);
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
                        CustomStreamHandler streamHandlerCustom = new CustomStreamHandler(chatHistory, responseBuffer, botLabel);
                        OllamaStreamHandler streamHandler = chunk -> SwingUtilities.invokeLater(() -> streamHandlerCustom.accept(chunk));
                        history = ollamaChat.chat(message, history, ollamaAPI, selectedModel, streamHandler);
                    } catch (OllamaBaseException | IOException | InterruptedException ex) {
                        SwingUtilities.invokeLater(() -> chatHistory.append("\n[Error] " + ex.getMessage() + "\n"));
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
            int confirm = JOptionPane.showConfirmDialog(frame, "Are you sure you want to clear the chat?", "Confirm Clear", JOptionPane.YES_NO_OPTION);
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

        JPanel modelsPanel = new JPanel(new BorderLayout());
        String[] columnNames = {"Model", "Version", "Size"};
        DefaultTableModel tableModel = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(tableModel);

        try {
            ollamaAPI.listModels().forEach(model -> {
                String size = humanReadableSize(model.getSize());
                tableModel.addRow(new Object[]{model.getModelName(), model.getModelVersion(), size});
            });
        } catch (Exception e) {
            logger.error("Error: {}", e.getMessage());
        }
        table.setDefaultEditor(Object.class, null);
        modelsPanel.add(new JLabel("Available Models"), BorderLayout.NORTH);
        modelsPanel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel settingsPanel = new JPanel(new BorderLayout());
        String[] settingsColumnNames = {"Setting", "Value"};
        DefaultTableModel settingsTableModel = new DefaultTableModel(settingsColumnNames, 0);
        JTable settingsTable = new JTable(settingsTableModel);

        settingsTableModel.addRow(new Object[]{"Ollama Host", ollamaHost});
        settingsTableModel.addRow(new Object[]{"Cache Directory", "/home"});

        JButton browseButton = new JButton("Select Cache Directory");
        browseButton.addActionListener(e -> {
            JFileChooser directoryChooser = new JFileChooser();
            directoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int returnValue = directoryChooser.showOpenDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedDirectory = directoryChooser.getSelectedFile();
                settingsTableModel.setValueAt(selectedDirectory.getAbsolutePath(), 1, 1);
            }
        });

        JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            ollamaHost = (String) settingsTableModel.getValueAt(0, 1);
            JOptionPane.showMessageDialog(frame, "Saved: " + ollamaHost);
        });

        JPanel settingsButtonPanel = new JPanel();
        settingsButtonPanel.add(browseButton);
        settingsButtonPanel.add(saveButton);

        settingsPanel.add(new JScrollPane(settingsTable), BorderLayout.CENTER);
        settingsPanel.add(settingsButtonPanel, BorderLayout.SOUTH);

        rightPanel.add(chatPanel, "Chat");
        rightPanel.add(modelsPanel, "Models");
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
        if (bytes < 1024) return bytes + " B";
        int unit = 1024;
        String[] sizes = {"KB", "MB", "GB", "TB", "PB", "EB"};
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
        public List<OllamaChatMessage> chat(String message, List<OllamaChatMessage> history, OllamaAPI ollamaAPI, String model, OllamaStreamHandler streamHandler) throws OllamaBaseException, IOException, InterruptedException {
            OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(model).withMessages(history);
            OllamaChatRequest requestModel = builder.withMessage(OllamaChatMessageRole.USER, message)
                    .build();
            OllamaChatResult chatResult = ollamaAPI.chat(requestModel, streamHandler);
            return chatResult.getChatHistory();
        }
    }
}
