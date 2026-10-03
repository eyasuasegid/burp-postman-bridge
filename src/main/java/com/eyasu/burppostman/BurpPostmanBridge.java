package com.eyasu.burppostman;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import com.google.gson.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.prefs.Preferences;

public class BurpPostmanBridge implements BurpExtension {
    private MontoyaApi api;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Preferences prefs = Preferences.userNodeForPackage(BurpPostmanBridge.class);
    private final Path backupDir = Paths.get(System.getProperty("user.home"), ".burp-postman", "backups");
    private final Path bodyDir = Paths.get(System.getProperty("user.home"), ".burp-postman", "bodies");
    private JTextField apiKeyField;
    private JLabel statusLabel;
    private JCheckBox includeContentType;
    private JCheckBox includeContentLength;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        this.api = montoyaApi;
        api.extension().setName("Burp → Postman Bridge");
        api.userInterface().registerContextMenuItemsProvider(new PostmanContextMenu());
        api.userInterface().registerSuiteTab("Postman Bridge", buildSettingsPanel());
        log("Loaded Burp → Postman Bridge 1.0.0");
        log("Right-click selected HTTP requests and choose 'Send selected requests to Postman'");
    }

    private JPanel buildSettingsPanel() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("Burp → Postman Bridge");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        root.add(title, BorderLayout.NORTH);

        apiKeyField = new JPasswordField(48);
        String saved = prefs.get("postmanApiKey", "");
        String env = System.getenv("POSTMAN_API_KEY");
        apiKeyField.setText(saved.isBlank() && env != null ? env : saved);

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        form.add(new JLabel("Postman API key:"), c);
        c.gridx = 1; c.weightx = 1;
        form.add(apiKeyField, c);

        JButton save = new JButton("Save API key");
        save.addActionListener(e -> {
            prefs.put("postmanApiKey", new String(apiKeyField.getText()));
            setStatus("API key saved locally.");
        });
        c.gridx = 1; c.gridy = 1; c.weightx = 0;
        form.add(save, c);

        JButton clear = new JButton("Clear saved key");
        clear.addActionListener(e -> {
            prefs.remove("postmanApiKey");
            apiKeyField.setText("");
            setStatus("Saved API key cleared.");
        });
        c.gridx = 1; c.gridy = 2;
        form.add(clear, c);

        JLabel behavior = new JLabel("Requests: selected Burp HTTP history items → existing Postman collection/folder. Content-Type and Content-Length can be included using the import options.");
        behavior.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        form.add(behavior, c);

        statusLabel = new JLabel("Ready.");
        c.gridy = 4;
        form.add(statusLabel, c);

        root.add(form, BorderLayout.NORTH);
        return root;
    }

    private void setStatus(String s) {
        if (statusLabel != null) SwingUtilities.invokeLater(() -> statusLabel.setText(s));
        log(s);
    }

    private String apiKey() {
        String ui = apiKeyField == null ? "" : new String(((JPasswordField) apiKeyField).getPassword()).trim();
        if (!ui.isBlank()) return ui;
        String env = System.getenv("POSTMAN_API_KEY");
        return env == null ? "" : env.trim();
    }

    private void log(String s) {
        if (api != null) api.logging().logToOutput("[Postman Bridge] " + s);
    }

    private class PostmanContextMenu implements ContextMenuItemsProvider {
        @Override
        public List<Component> provideMenuItems(ContextMenuEvent event) {
            List<HttpRequestResponse> selected = new ArrayList<>(event.selectedRequestResponses());

            // In table-based views such as Proxy HTTP history, Burp supplies
            // the selected requests through selectedRequestResponses().
            // In message editors such as Repeater, the current request is
            // exposed through messageEditorRequestResponse() instead.
            if (selected.isEmpty()) {
                event.messageEditorRequestResponse()
                        .map(editorMessage -> editorMessage.requestResponse())
                        .ifPresent(selected::add);
            }

            if (selected.isEmpty()) return Collections.emptyList();

            JMenuItem item = new JMenuItem("Send selected requests to Postman");
            item.addActionListener(e -> openImportDialog(selected));
            return List.of(item);
        }
    }

    private void openImportDialog(List<HttpRequestResponse> selected) {
        String key = apiKey();
        if (key.isBlank()) {
            JOptionPane.showMessageDialog(null,
                    "Set your Postman API key first in the 'Postman Bridge' tab.",
                    "Postman API key required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog((Frame) null, "Send to Postman", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(900, 600);
        dialog.setLocationRelativeTo(null);

        JPanel top = new JPanel(new GridLayout(0, 1, 6, 6));
        top.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        top.add(new JLabel("Selected requests: " + selected.size()));
        JComboBox<CollectionInfo> collections = new JComboBox<>();
        JComboBox<FolderInfo> folders = new JComboBox<>();
        top.add(new JLabel("Existing Postman collection:"));
        top.add(collections);
        top.add(new JLabel("Destination folder:"));
        top.add(folders);

        JPanel headerOptions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        includeContentType = new JCheckBox("Include Content-Type");
        includeContentLength = new JCheckBox("Include Content-Length");
        headerOptions.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
        headerOptions.add(includeContentType);
        headerOptions.add(includeContentLength);
        top.add(new JLabel("Headers:"));
        top.add(headerOptions);

        dialog.add(top, BorderLayout.NORTH);

        String[] columnNames = {"Name (editable)", "Method", "URL"};
        DefaultTableModel requestModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        for (HttpRequestResponse rr : selected) {
            HttpRequest r = rr.request();
            String path = r.path().isBlank() ? "/" : r.path();
            String defaultName = r.method() + " " + path;
            requestModel.addRow(new Object[]{defaultName, r.method(), r.url()});
        }

        JTable requestTable = new JTable(requestModel);
        requestTable.setRowHeight(24);
        requestTable.setFillsViewportHeight(true);
        requestTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        requestTable.getColumnModel().getColumn(0).setPreferredWidth(300);
        requestTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        requestTable.getColumnModel().getColumn(2).setPreferredWidth(500);

        JLabel nameHint = new JLabel("Request names are editable. Double-click a Name cell to change it.");
        nameHint.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel requestPanel = new JPanel(new BorderLayout(0, 4));
        requestPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));
        requestPanel.add(nameHint, BorderLayout.NORTH);
        requestPanel.add(new JScrollPane(requestTable), BorderLayout.CENTER);
        dialog.add(requestPanel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refresh = new JButton("Refresh");
        JButton cancel = new JButton("Cancel");
        JButton importButton = new JButton("Import");
        buttons.add(refresh); buttons.add(cancel); buttons.add(importButton);
        dialog.add(buttons, BorderLayout.SOUTH);

        Runnable loadCollections = () -> {
            try {
                List<CollectionInfo> list = listCollections(key);
                collections.removeAllItems();
                for (CollectionInfo x : list) collections.addItem(x);
                if (!list.isEmpty()) loadFolders(key, list.get(0).id, folders);
            } catch (Exception ex) {
                showError(dialog, "Could not load Postman collections", ex);
            }
        };

        collections.addActionListener(e -> {
            CollectionInfo c = (CollectionInfo) collections.getSelectedItem();
            if (c != null) {
                try { loadFolders(key, c.id, folders); }
                catch (Exception ex) { showError(dialog, "Could not load collection folders", ex); }
            }
        });
        refresh.addActionListener(e -> loadCollections.run());
        cancel.addActionListener(e -> dialog.dispose());

        importButton.addActionListener(e -> {
            CollectionInfo c = (CollectionInfo) collections.getSelectedItem();
            FolderInfo f = (FolderInfo) folders.getSelectedItem();
            if (c == null || f == null) {
                showError(dialog, "Choose a collection and destination.", null);
                return;
            }
            int answer = JOptionPane.showConfirmDialog(dialog,
                    "Import " + selected.size() + " request(s) into:\n\n" + c.name + "\n" + f.path +
                            "\n\nA local backup will be created before the collection is updated.",
                    "Confirm import", JOptionPane.OK_CANCEL_OPTION);
            if (answer != JOptionPane.OK_OPTION) return;

            if (requestTable.isEditing()) {
                requestTable.getCellEditor().stopCellEditing();
            }

            List<String> requestNames = new ArrayList<>();
            for (int i = 0; i < requestModel.getRowCount(); i++) {
                String name = String.valueOf(requestModel.getValueAt(i, 0)).trim();
                if (name.isBlank()) {
                    JOptionPane.showMessageDialog(
                            dialog,
                            "Request name " + (i + 1) + " is empty. Please enter a name.",
                            "Request name required",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                requestNames.add(name);
            }

            final boolean importContentType = includeContentType.isSelected();
            final boolean importContentLength = includeContentLength.isSelected();
            final List<String> finalRequestNames = List.copyOf(requestNames);

            importButton.setEnabled(false);
            refresh.setEnabled(false);
            new SwingWorker<Void, Void>() {
                Exception error;
                String message;
                @Override protected Void doInBackground() {
                    try {
                        JsonObject collection = getCollection(key, c.id);
                        saveBackup(collection, c.name);
                        JsonArray imported = new JsonArray();
                        for (int i = 0; i < selected.size(); i++) {
                            imported.add(toPostmanItem(
                                    selected.get(i).request(),
                                    finalRequestNames.get(i),
                                    importContentType,
                                    importContentLength));
                        }
                        JsonArray destinationItems = findDestinationItems(collection, f.path);
                        if (destinationItems == null) throw new IllegalStateException("Destination folder disappeared; refresh and try again.");
                        for (JsonElement x : imported) destinationItems.add(x);
                        putCollection(key, c.id, collection);
                        message = "Imported " + imported.size() + " request(s) into " + c.name + " / " + f.path;
                    } catch (Exception ex) { error = ex; }
                    return null;
                }
                @Override protected void done() {
                    importButton.setEnabled(true); refresh.setEnabled(true);
                    if (error != null) showError(dialog, "Import failed", error);
                    else {
                        setStatus(message);
                        JOptionPane.showMessageDialog(dialog, message, "Success", JOptionPane.INFORMATION_MESSAGE);
                        dialog.dispose();
                    }
                }
            }.execute();
        });

        loadCollections.run();
        dialog.setVisible(true);
    }

    private List<CollectionInfo> listCollections(String key) throws Exception {
        JsonObject root = getJson("https://api.getpostman.com/collections", key);
        List<CollectionInfo> result = new ArrayList<>();
        JsonArray a = root.getAsJsonArray("collections");
        if (a != null) for (JsonElement e : a) {
            JsonObject o = e.getAsJsonObject();
            result.add(new CollectionInfo(o.get("id").getAsString(), o.get("name").getAsString()));
        }
        return result;
    }

    private void loadFolders(String key, String collectionId, JComboBox<FolderInfo> combo) throws Exception {
        JsonObject c = getCollection(key, collectionId);
        combo.removeAllItems();
        combo.addItem(new FolderInfo("", "Collection root"));
        addFolders(c.getAsJsonArray("item"), "", combo);
    }

    private void addFolders(JsonArray items, String parent, JComboBox<FolderInfo> combo) {
        if (items == null) return;
        for (JsonElement e : items) {
            JsonObject o = e.getAsJsonObject();
            if (!o.has("item")) continue;
            String name = o.has("name") ? o.get("name").getAsString() : "Unnamed folder";
            String path = parent.isBlank() ? name : parent + "/" + name;
            combo.addItem(new FolderInfo(path, path));
            addFolders(o.getAsJsonArray("item"), path, combo);
        }
    }

    private JsonObject getCollection(String key, String id) throws Exception {
        return getJson("https://api.getpostman.com/collections/" + id, key).getAsJsonObject("collection");
    }

    private JsonObject getJson(String url, String key) throws Exception {
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).header("X-Api-Key", key).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() / 100 != 2) throw new IOException("HTTP " + res.statusCode() + ": " + res.body());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private void putCollection(String key, String id, JsonObject collection) throws Exception {
        JsonObject payload = new JsonObject();
        payload.add("collection", collection);

        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(
                URI.create("https://api.getpostman.com/collections/" + id))
                .timeout(Duration.ofSeconds(60))
                .header("X-Api-Key", key)
                .header("Content-Type", "application/json")
                .PUT(BodyPublishers.ofString(
                        gson.toJson(payload),
                        StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(
                req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (res.statusCode() / 100 != 2) {
            throw new IOException(
                    "HTTP " + res.statusCode() + ": " + res.body());
        }
    }

    private JsonArray findDestinationItems(JsonObject collection, String path) {
        if (path == null || path.isBlank()) return collection.getAsJsonArray("item");
        String[] parts = path.split("/");
        JsonArray current = collection.getAsJsonArray("item");
        for (String wanted : parts) {
            JsonObject found = null;
            if (current != null) for (JsonElement e : current) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("item") && o.has("name") && wanted.equals(o.get("name").getAsString())) { found = o; break; }
            }
            if (found == null) return null;
            if (!found.has("item")) found.add("item", new JsonArray());
            current = found.getAsJsonArray("item");
        }
        return current;
    }

    private JsonObject toPostmanItem(
            HttpRequest request,
            String requestName,
            boolean includeContentTypeHeader,
            boolean includeContentLengthHeader) {

        JsonObject item = new JsonObject();
        item.addProperty("name", requestName);

        JsonObject r = new JsonObject();
        r.addProperty("method", request.method());

        JsonArray headers = new JsonArray();
        String originalContentType = null;

        for (HttpHeader h : request.headers()) {
            String name = h.name();

            if (name.equalsIgnoreCase("content-type")) {
                originalContentType = h.value();
                if (!includeContentTypeHeader) {
                    continue;
                }
            }

            if (name.equalsIgnoreCase("content-length") && !includeContentLengthHeader) {
                continue;
            }

            JsonObject x = new JsonObject();
            x.addProperty("key", name);
            x.addProperty("value", h.value());
            x.addProperty("type", "text");
            headers.add(x);
        }

        r.add("header", headers);
        r.addProperty("url", request.url());

        String body = request.bodyToString();
        if (!body.isEmpty()) {
            String ct = originalContentType == null
                    ? ""
                    : originalContentType.toLowerCase(Locale.ROOT);

            if (ct.contains("application/x-www-form-urlencoded")) {
                JsonObject b = new JsonObject();
                b.addProperty("mode", "urlencoded");

                JsonArray a = new JsonArray();
                for (String pair : body.split("&", -1)) {
                    String[] p = pair.split("=", 2);
                    JsonObject q = new JsonObject();
                    q.addProperty("key", p.length > 0 ? p[0] : "");
                    q.addProperty("value", p.length > 1 ? p[1] : "");
                    a.add(q);
                }

                b.add("urlencoded", a);
                r.add("body", b);
            } else {
                String lang = "text";
                if (ct.contains("application/json")) lang = "json";
                else if (ct.contains("application/xml") || ct.contains("text/xml")) lang = "xml";
                else if (ct.contains("text/html")) lang = "html";
                else if (ct.contains("javascript")) lang = "javascript";

                JsonObject b = new JsonObject();
                b.addProperty("mode", "raw");
                b.addProperty("raw", body);

                JsonObject options = new JsonObject();
                JsonObject raw = new JsonObject();
                raw.addProperty("language", lang);
                options.add("raw", raw);
                b.add("options", options);

                r.add("body", b);
            }
        }

        item.add("request", r);
        item.add("response", new JsonArray());
        return item;
    }

    private void saveBackup(JsonObject collection, String name) throws IOException {
        Files.createDirectories(backupDir);
        String safe = name.replaceAll("[^a-zA-Z0-9._-]+", "_");
        Path p = backupDir.resolve(safe + "_" + System.currentTimeMillis() + ".json");
        Files.writeString(p, gson.toJson(collection), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        log("Backup saved: " + p);
    }

    private void showError(Component parent, String title, Exception ex) {
        String msg = ex == null ? title : title + "\n\n" + ex.getMessage();
        if (ex != null) log(msg);
        JOptionPane.showMessageDialog(parent, msg, title, JOptionPane.ERROR_MESSAGE);
    }

    private static class CollectionInfo {
        final String id; final String name;
        CollectionInfo(String id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    private static class FolderInfo {
        final String path; final String display;
        FolderInfo(String path, String display) { this.path = path; this.display = display; }
        @Override public String toString() { return display; }
    }
}
