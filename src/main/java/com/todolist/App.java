package com.todolist;

// --- IMPORTANWEISUNGEN ---
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class App extends Application {

    // --- Enum für die Werkzeuge ---
    // Hinweis: Enum-Konstanten bleiben auf Englisch, um Codeänderungen zu minimieren.
    private enum Tool { PEN, ERASER, RECTANGLE, OVAL }

    // --- Zustandvariablen ---
    private Tool currentTool = Tool.PEN;        // Aktuell ausgewähltes Werkzeug
    private Color currentColor = Color.BLACK;   // Aktuelle Zeichenfarbe
    private Color backgroundColor = Color.WHITE;// Hintergrundfarbe (für Radierer/Löschen)
    private double currentLineWidth = 2.0;      // Aktuelle Linienbreite
    private double startX, startY;             // Startkoordinaten für Formen/Linien
    private double currentScale = 1.0;          // Aktueller Zoomfaktor
    private static final double MIN_SCALE = 0.1; // Minimaler Zoomfaktor
    private static final double MAX_SCALE = 10.0;// Maximaler Zoomfaktor
    private static final double ZOOM_FACTOR = 1.1;// Zoom-Schrittfaktor

    // --- UI-Komponenten ---
    private Canvas canvas, previewCanvas;         // Haupt-Canvas und Vorschau-Canvas
    private GraphicsContext gc, previewGc;       // Zeichenkontexte für die Canvases
    private Label widthValueLabel;                // Label zur Anzeige der Linienbreite
    private ColorPicker colorPicker;              // Farbwähler
    private Slider lineWidthSlider;               // Schieberegler für Linienbreite
    private ToggleGroup toolGroup;                // Gruppe für Werkzeug-Buttons
    private ToggleButton penButton, eraserButton, rectButton, ovalButton; // Werkzeug-Buttons
    private Button clearButton, saveButton, loadButton; // Aktions-Buttons
    private StackPane canvasPane;                 // Container, der Canvases übereinanderlegt
    private ScrollPane scrollPane;                // Container mit Scrollbars für canvasPane
    private PauseTransition debounceTimer;        // Timer für verzögertes Neuzeichnen nach Resize
    private Stage primaryStage;                   // Referenz auf das Hauptfenster


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
         this.primaryStage = primaryStage; // Referenz speichern
        primaryStage.setTitle("Modernes Malprogramm - v3.1"); // Fenstertitel
        // Canvases mit Standardgröße erstellen (wird ggf. beim Laden angepasst)
        canvas = new Canvas(1024, 768); gc = canvas.getGraphicsContext2D();
        previewCanvas = new Canvas(1024, 768); previewGc = previewCanvas.getGraphicsContext2D();
        // Zeichenkontexte konfigurieren
        setupGC();
        setupPreviewGC();
        // Steuerelemente erstellen
        setupControls();
        // Layouts und Szene erstellen (inkl. Bindings/Listener)
        setupLayoutsAndScene(primaryStage);
        // Maus-Ereignisbehandler registrieren
        setupMouseHandlers();
        // Zoom-Ereignisbehandler registrieren
        setupZoomHandler();
        // Initiales Löschen nach Anzeigen des Fensters (verzögert)
    }

    // --- Konfiguriert den Haupt-Zeichenkontext ---
    private void setupGC() {
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND); // Linienenden rund
        gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);// Linienecken rund
        gc.setLineWidth(currentLineWidth); // Aktuelle Breite
        gc.setStroke(currentColor);        // Aktuelle Farbe
    }
    // --- Konfiguriert den Vorschau-Zeichenkontext ---
    private void setupPreviewGC() {
        if (previewGc == null) return; // Sicherheitsprüfung
        previewGc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        previewGc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        previewGc.setLineWidth(currentLineWidth);
        previewGc.setStroke(currentColor);
        // Optional: Gestrichelte Linie für Vorschau
        // previewGc.setLineDashes(10, 5);
    }

    // --- Registriert Maus-Ereignisse auf dem Vorschau-Canvas ---
    private void setupMouseHandlers() {
        if (previewCanvas == null || scrollPane == null) {
            System.err.println("Fehler: UI-Komponente ist null in setupMouseHandlers");
            return;
        }
        // Maus gedrückt
        previewCanvas.setOnMousePressed(e -> {
            scrollPane.setPannable(false); // Scrollen per Ziehen deaktivieren
            // Koordinaten durch aktuellen Maßstab korrigieren
            startX = e.getX() / currentScale;
            startY = e.getY() / currentScale;
            // Einstellungen auf beide GCs anwenden
            gc.setLineWidth(currentLineWidth); previewGc.setLineWidth(currentLineWidth);
            gc.setStroke(currentColor); previewGc.setStroke(currentColor);
            // Aktion je nach Werkzeug
            switch(currentTool){
                case PEN: // Stift: Neuen Pfad beginnen
                    gc.beginPath(); gc.moveTo(startX, startY); gc.stroke(); break;
                case ERASER: // Radierer: Mit Hintergrundfarbe zeichnen
                    gc.setStroke(backgroundColor); gc.beginPath(); gc.moveTo(startX, startY); gc.stroke(); gc.setStroke(currentColor); break;
                case RECTANGLE: case OVAL: // Formen: Vorschau löschen
                    if(previewCanvas.getWidth()>0 && previewCanvas.getHeight()>0) previewGc.clearRect(0,0,previewCanvas.getWidth(),previewCanvas.getHeight()); break;
            }
        });
        // Maus gezogen
        previewCanvas.setOnMouseDragged(e -> {
            double cX = e.getX() / currentScale; double cY = e.getY() / currentScale;
            gc.setLineWidth(currentLineWidth); // Für Stift/Radierer wichtig
            switch(currentTool){
                case PEN: // Stift: Linie zum Punkt ziehen
                    gc.lineTo(cX, cY); gc.stroke(); break;
                case ERASER: // Radierer: Linie mit Hintergrundfarbe ziehen
                    gc.setStroke(backgroundColor); gc.lineTo(cX, cY); gc.stroke(); gc.setStroke(currentColor); break;
                case RECTANGLE: case OVAL: // Formen: Alte Vorschau löschen, neue zeichnen
                    if(previewCanvas.getWidth()>0 && previewCanvas.getHeight()>0) previewGc.clearRect(0,0,previewCanvas.getWidth(),previewCanvas.getHeight());
                    drawPreviewShape(startX, startY, cX, cY, currentTool); break;
            }
        });
        // Maus losgelassen
        previewCanvas.setOnMouseReleased(e -> {
            try { // try-finally für sicheres Reaktivieren des Pannings
                double endX = e.getX() / currentScale; double endY = e.getY() / currentScale;
                gc.setLineWidth(currentLineWidth); gc.setStroke(currentColor);
                switch(currentTool){
                    case PEN: case ERASER: break; // Nichts zu tun
                    case RECTANGLE: case OVAL: // Formen: Vorschau löschen, finale Form zeichnen
                        if(previewCanvas.getWidth()>0 && previewCanvas.getHeight()>0) previewGc.clearRect(0,0,previewCanvas.getWidth(),previewCanvas.getHeight());
                        if(currentColor != backgroundColor && currentColor != Color.TRANSPARENT) // Nur zeichnen, wenn sichtbar
                            drawPermanentShape(startX, startY, endX, endY, currentTool);
                        break;
                }
                if(currentTool == Tool.ERASER) gc.setStroke(currentColor); // Farbe für GC zurücksetzen
            } finally {
                scrollPane.setPannable(true); // Scrollen per Ziehen wieder aktivieren
            }
        });
        updateCursor(); // Initialen Cursor setzen
    }

    // --- Zeichenmethoden ---
    private void drawPreviewShape(double x1, double y1, double x2, double y2, Tool shapeType) {
        if(previewGc==null)return; previewGc.setLineWidth(currentLineWidth); previewGc.setStroke(currentColor);
        double tX=Math.min(x1,x2),tY=Math.min(y1,y2),w=Math.abs(x1-x2),h=Math.abs(y1-y2);
        if(w>0&&h>0){if(shapeType==Tool.RECTANGLE)previewGc.strokeRect(tX,tY,w,h); else if(shapeType==Tool.OVAL)previewGc.strokeOval(tX,tY,w,h);}
    }
    private void drawPermanentShape(double x1, double y1, double x2, double y2, Tool shapeType) {
        if(gc==null)return; gc.setLineWidth(currentLineWidth); gc.setStroke(currentColor);
        double tX=Math.min(x1,x2),tY=Math.min(y1,y2),w=Math.abs(x1-x2),h=Math.abs(y1-y2);
        if(w>0&&h>0){if(shapeType==Tool.RECTANGLE)gc.strokeRect(tX,tY,w,h); else if(shapeType==Tool.OVAL)gc.strokeOval(tX,tY,w,h);}
    }


    // --- Erstellt und konfiguriert die Steuerelemente ---
     private void setupControls() {
        toolGroup = new ToggleGroup();
        // Werkzeug-Buttons erstellen (deutscher Text!)
        penButton = createToolButton("Stift", Tool.PEN, toolGroup, true);
        eraserButton = createToolButton("Radierer", Tool.ERASER, toolGroup, false);
        rectButton = createToolButton("Rechteck", Tool.RECTANGLE, toolGroup, false);
        ovalButton = createToolButton("Oval", Tool.OVAL, toolGroup, false);

        // Farbwähler
        colorPicker = new ColorPicker(currentColor); colorPicker.getStyleClass().add("control-element"); colorPicker.setTooltip(new Tooltip("Farbe wählen"));
        // **** KORREKTUR HIER ****
        colorPicker.setOnAction(e -> {currentColor = colorPicker.getValue(); if(gc!=null)gc.setStroke(currentColor); if(previewGc!=null)previewGc.setStroke(currentColor);});

        // Linienbreiten-Regler
        lineWidthSlider = new Slider(1,30,currentLineWidth); lineWidthSlider.setShowTickMarks(true); lineWidthSlider.setShowTickLabels(true); lineWidthSlider.setMajorTickUnit(10); lineWidthSlider.setMinorTickCount(4); lineWidthSlider.setBlockIncrement(1); lineWidthSlider.getStyleClass().add("control-element"); lineWidthSlider.setPrefWidth(150); lineWidthSlider.setTooltip(new Tooltip("Linienbreite anpassen"));

        // Label für Linienbreite
        widthValueLabel = new Label(String.format("%.1f",currentLineWidth)); widthValueLabel.getStyleClass().add("info-label"); widthValueLabel.setMinWidth(35);
        // **** KORREKTUR HIER ****
        lineWidthSlider.valueProperty().addListener((obs, ov, nV) -> {currentLineWidth=nV.doubleValue(); widthValueLabel.setText(String.format("%.1f",currentLineWidth)); if(gc!=null)gc.setLineWidth(currentLineWidth); if(previewGc!=null)previewGc.setLineWidth(currentLineWidth);});

        // Weitere Buttons
        // **** KORREKTUR HIER ****
        clearButton = new Button("Leeren"); clearButton.getStyleClass().addAll("control-element","clear-button"); clearButton.setTooltip(new Tooltip("Zeichnung löschen")); clearButton.setOnAction(e -> clearCanvas());
        saveButton = new Button("Speichern"); saveButton.getStyleClass().add("control-element"); saveButton.setTooltip(new Tooltip("Als PNG speichern")); saveButton.setOnAction(e->onSave());
        loadButton = new Button("Laden"); loadButton.getStyleClass().add("control-element"); loadButton.setTooltip(new Tooltip("PNG laden")); loadButton.setOnAction(e->onLoad());

        // Listener für Werkzeugwechsel
        // **** KORREKTUR HIER ****
        toolGroup.selectedToggleProperty().addListener((obs, ov, nT) -> {
            if(previewGc!=null) previewGc.clearRect(0,0,previewCanvas.getWidth(),previewCanvas.getHeight()); // Vorschau löschen
            if(nT!=null) currentTool=(Tool)nT.getUserData(); // Neues Werkzeug setzen
            else{currentTool=Tool.PEN; if(penButton!=null) penButton.setSelected(true);} // Fallback
            updateCursor(); // Cursor anpassen
            System.out.println("Werkzeug gewählt: "+currentTool); // Konsolenausgabe
        });
     }
    // --- Hilfsmethode zum Erstellen der Werkzeug-Buttons ---
    private ToggleButton createToolButton(String text, Tool tool, ToggleGroup group, boolean selected) {
        ToggleButton button = new ToggleButton(text);
        button.setUserData(tool);
        button.setToggleGroup(group);
        button.setSelected(selected);
        button.getStyleClass().add("tool-button");
        button.setTooltip(new Tooltip(text)); // Tooltip mit Button-Text
        return button;
    }


    // --- Löscht beide Canvases ---
    private void clearCanvas() {
        double w=canvas.getWidth(), h=canvas.getHeight();
        if(gc!=null && w>0 && h>0){gc.setFill(backgroundColor); gc.fillRect(0,0,w,h); gc.setStroke(currentColor);}
        if(previewGc!=null && previewCanvas!=null){double pw=previewCanvas.getWidth(),ph=previewCanvas.getHeight(); if(pw>0 && ph>0)previewGc.clearRect(0,0,pw,ph);}
        // System.out.println("Canvases gelöscht: "+w+"x"+h);
    }
    // --- Aktualisiert den Mauscursor ---
    private void updateCursor() {
        if(previewCanvas == null) return;
        switch(currentTool){
            case PEN: previewCanvas.setCursor(Cursor.CROSSHAIR); break; // Fadenkreuz
            case ERASER: previewCanvas.setCursor(Cursor.HAND); break;    // Hand (oder spezifischer Radierercursor)
            case RECTANGLE: case OVAL: default: previewCanvas.setCursor(Cursor.DEFAULT); break; // Standardpfeil
        }
    }


    // --- Speichert die Zeichnung als PNG ---
    private void onSave() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Zeichnung speichern"); // Dialogtitel
        fileChooser.setInitialFileName("meine_zeichnung.png"); // Standard-Dateiname
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Dateien", "*.png")); // Dateifilter

        // Fenstereigentümer ermitteln
        Window ownerWindow = (scrollPane != null && scrollPane.getScene() != null) ? scrollPane.getScene().getWindow() : primaryStage;
        if (ownerWindow == null) { showErrorAlert("Interner Fehler", "Fenster konnte nicht ermittelt werden."); return; }
        File file = fileChooser.showSaveDialog(ownerWindow);

        if (file != null) { // Nur fortfahren, wenn Datei ausgewählt wurde
            Platform.runLater(() -> { // Grafikoperationen im FX-Thread
                try {
                    double cw = canvas.getWidth(), ch = canvas.getHeight();
                    if (cw <= 0 || ch <= 0 || !Double.isFinite(cw) || !Double.isFinite(ch)) { showErrorAlert("Speichern Fehler", "Ungültige Canvas-Größe ("+cw+"x"+ch+")."); return; }

                    // Snapshot erstellen
                    SnapshotParameters params = new SnapshotParameters(); params.setFill(Color.TRANSPARENT);
                    WritableImage wi = new WritableImage((int)Math.ceil(cw), (int)Math.ceil(ch));
                    canvas.snapshot(params, wi);
                    if (wi.isError()) { showErrorAlert("Snapshot Fehler", "Konnte keine Momentaufnahme erstellen: " + wi.getException()); return; }

                    // Konvertieren & Hintergrund hinzufügen
                    BufferedImage bi = SwingFXUtils.fromFXImage(wi, null);
                    if (bi == null) { showErrorAlert("Konvertierungsfehler", "Bild konnte nicht konvertiert werden."); return; }
                    BufferedImage finalImage = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g2d = finalImage.createGraphics(); g2d.setColor(java.awt.Color.WHITE); g2d.fillRect(0,0,finalImage.getWidth(),finalImage.getHeight()); g2d.drawImage(bi,0,0,null); g2d.dispose();

                    // Schreiben
                    boolean success = ImageIO.write(finalImage, "png", file);
                    if (!success) { showErrorAlert("Schreibfehler", "ImageIO konnte PNG nicht schreiben."); }
                    else { System.out.println("Gespeichert: " + file.getAbsolutePath()); }

                } catch (IOException ex) { showErrorAlert("I/O Fehler", "Speichern:\n" + ex.getMessage()); ex.printStackTrace(); }
                  catch (Exception ex) { showErrorAlert("Unerwarteter Fehler", "Speichern:\n" + ex.getMessage()); ex.printStackTrace(); }
            });
        } else { System.out.println("Speichern abgebrochen."); }
    }

    // --- Lädt eine Zeichnung aus einer PNG-Datei ---
    private void onLoad() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Zeichnung laden"); // Dialogtitel
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Dateien", "*.png")); // Filter

        Window ownerWindow = (scrollPane != null && scrollPane.getScene() != null) ? scrollPane.getScene().getWindow() : primaryStage;
         if (ownerWindow == null) { showErrorAlert("Interner Fehler", "Fenster konnte nicht ermittelt werden."); return; }
        File file = fileChooser.showOpenDialog(ownerWindow);

        if (file != null) {
            System.out.println("Lade: " + file.getAbsolutePath());
            try {
                // Bilddatei lesen (kann im Hintergrund geschehen, hier aber einfach)
                BufferedImage bufferedImage = ImageIO.read(file);
                if (bufferedImage != null) {
                     System.out.println("Bilddatei gelesen.");
                     Platform.runLater(() -> { // UI-Updates im FX-Thread
                        try {
                             Image loadedImage = SwingFXUtils.toFXImage(bufferedImage, null); // Konvertieren
                            if(loadedImage == null) { showErrorAlert("Konvertierungsfehler", "Laden: Bild konnte nicht konvertiert werden."); return; }
                            double imgW = loadedImage.getWidth(), imgH = loadedImage.getHeight();
                            if (imgW <= 0 || imgH <= 0 || !Double.isFinite(imgW) || !Double.isFinite(imgH)) { showErrorAlert("Größenfehler", "Geladenes Bild hat ungültige Größe."); return; }

                            // Canvas-Größe an Bild anpassen
                            canvas.setWidth(imgW); canvas.setHeight(imgH);
                            previewCanvas.setWidth(imgW); previewCanvas.setHeight(imgH);
                            // Beide Canvases leeren
                            clearCanvas();
                             // Bild auf Haupt-Canvas zeichnen
                            if(gc!=null) gc.drawImage(loadedImage, 0, 0);
                            // Zoom zurücksetzen
                            setScale(1.0);
                            System.out.println("Bild geladen und gezeichnet.");
                        } catch (Exception ex) { showErrorAlert("Verarbeitungsfehler", "Laden (intern):\n" + ex.getMessage()); ex.printStackTrace(); }
                    });
                } else { showErrorAlert("Lesefehler", "Konnte Datei nicht als Bild lesen (Format?)."); }
            } catch (IOException ex) { showErrorAlert("I/O Fehler", "Laden:\n" + ex.getMessage()); ex.printStackTrace(); }
              catch (Exception ex) { showErrorAlert("Unerwarteter Fehler", "Laden (extern):\n" + ex.getMessage()); ex.printStackTrace(); }
        } else { System.out.println("Laden abgebrochen."); }
    }

    // --- Zeigt Fehlermeldungen an ---
    private void showErrorAlert(String title, String message) {
        // Sicherstellen, dass dies im FX Application Thread ausgeführt wird
        Platform.runLater(() -> {
             Alert alert = new Alert(Alert.AlertType.ERROR);
             alert.setTitle(title);
             alert.setHeaderText(null); // Kein Header-Text
             alert.setContentText(message);
             alert.showAndWait(); // Warten bis der Benutzer schließt
        });
    }
    // --- Zeigt Info-Meldungen an (momentan nicht verwendet) ---
    /*
     private void showInfoAlert(String title, String message) {
        Platform.runLater(() -> {
             Alert alert = new Alert(Alert.AlertType.INFORMATION);
             alert.setTitle(title);
             alert.setHeaderText(null);
             alert.setContentText(message);
             alert.showAndWait();
         });
    }
    */


    // --- Zoom-Handling für das ScrollPane ---
    private void setupZoomHandler() {
        if(scrollPane==null)return; // Sicherheitscheck
         scrollPane.setOnScroll((ScrollEvent event) -> {
             if (event.isControlDown()) { // Nur zoomen, wenn Strg/Cmd gedrückt ist
                 event.consume(); // Verhindert normales Scrollen
                 double zoomFactor = (event.getDeltaY() > 0) ? ZOOM_FACTOR : 1 / ZOOM_FACTOR;
                 double newScale = currentScale * zoomFactor;
                 // Zoom-Grenzen anwenden
                 newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
                 setScale(newScale); // Skalierung anwenden
             }
             // Wenn Strg nicht gedrückt ist, wird das Event *nicht* konsumiert
             // und das ScrollPane führt das normale Scrollen aus.
         });
    }
    // --- Wendet die Skalierung auf den canvasPane an ---
    private void setScale(double newScale) {
        if(canvasPane==null)return; // Sicherheitscheck
        canvasPane.setScaleX(newScale);
        canvasPane.setScaleY(newScale);
        currentScale = newScale; // Neuen Maßstab speichern
         // Konsolenausgabe (optional)
         // System.out.println("Maßstab gesetzt auf: "+currentScale);
    }

    // --- Erstellt das Layout, die Szene, Bindings und Debounce Listener ---
    private void setupLayoutsAndScene(Stage pStage) { // Variable pStage statt primaryStage
        // --- Obere Kontrollleiste ---
        HBox controlsLayout = new HBox(10); controlsLayout.setPadding(new Insets(10)); controlsLayout.setAlignment(Pos.CENTER_LEFT); controlsLayout.getStyleClass().add("controls-bar");
        HBox widthLayout = new HBox(5, lineWidthSlider, widthValueLabel); widthLayout.setAlignment(Pos.CENTER_LEFT);
        Separator s1=new Separator(javafx.geometry.Orientation.VERTICAL), s2=new Separator(javafx.geometry.Orientation.VERTICAL), s3=new Separator(javafx.geometry.Orientation.VERTICAL);
        // Elemente hinzufügen (Validierung, dass Buttons existieren, ist gut, aber hier vereinfacht)
        controlsLayout.getChildren().addAll(
                new Label("Datei:"), saveButton, loadButton, // Datei-Operationen
                s1, new Label("Werkzeuge:"), penButton, eraserButton, rectButton, ovalButton, // Werkzeuge
                s2, new Label("Farbe:"), colorPicker, // Farbe
                s3, new Label("Breite:"), widthLayout, // Breite
                new Spacer(), clearButton); // Abstand und Leeren-Button

        // --- Hauptlayout ---
        BorderPane root = new BorderPane();
        // StackPane für die Canvases
        canvasPane = new StackPane(canvas, previewCanvas); // previewCanvas über canvas
        canvasPane.getStyleClass().add("canvas-container");
        canvasPane.setStyle("-fx-background-color: "+cssColor(backgroundColor)+";"); // Hintergrund setzen

        // ScrollPane um das StackPane
        scrollPane = new ScrollPane(canvasPane);
        scrollPane.setPannable(true); // Scrollen per Ziehen initial aktivieren
        root.setCenter(scrollPane); // ScrollPane in die Mitte
        root.setTop(controlsLayout); // Kontrollleiste oben

        // --- KEINE Bindings für Canvas-Größe mehr! ---

        // --- Debounce Timer für verzögertes Neuzeichnen nach Größenänderung ---
        debounceTimer = new PauseTransition(Duration.millis(150)); // 150ms Wartezeit
        debounceTimer.setOnFinished(evt -> {
            System.out.println("Debounce Timer beendet. ScrollPane Größe: " + scrollPane.getViewportBounds().getWidth() + "x" + scrollPane.getViewportBounds().getHeight());
            // Optional: Canvas neu zeichnen oder Aktionen nach Resize hier, z.B.:
             // clearCanvas(); // Nur aktivieren, wenn Löschen bei Resize gewünscht ist!
        });
        // Listener, der den Timer bei Größenänderung des ScrollPanes neu startet
        ChangeListener<Number> resizeListener = (o, ov, nv) -> {
            if (nv != null && nv.doubleValue() >= 0) { // Nur bei gültigen Werten
                debounceTimer.playFromStart(); // Timer neustarten
            }
        };
        scrollPane.widthProperty().addListener(resizeListener); // Auf Breite hören
        scrollPane.heightProperty().addListener(resizeListener);// Auf Höhe hören

        // --- Szene erstellen ---
        Scene scene = new Scene(root, 1024, 768); // Anfangsgröße des Fensters

        // --- CSS laden ---
        try {
            String cssPath = getClass().getResource("/styles.css").toExternalForm();
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
                System.out.println("CSS geladen von: " + cssPath);
            } else { System.err.println("FEHLER: styles.css nicht im Ressourcenordner gefunden."); }
        } catch (Exception e) { System.err.println("Fehler beim Laden von styles.css."); }

        // --- Fenster konfigurieren und anzeigen ---
        pStage.setScene(scene);
        pStage.show();

        // --- Initiale Aktionen nach dem Anzeigen (verzögert) ---
        Platform.runLater(()->{
            System.out.println("Initiale Verzögerung: clearCanvas / setup GCs.");
            clearCanvas(); // Initiales Löschen
            setupGC();     // GCs initialisieren, nachdem Canvas Größe hat
            setupPreviewGC();
        });
    }

    // --- Hilfsmethode für CSS-Farbstring ---
    private String cssColor(Color c) {
        return String.format("#%02X%02X%02X", (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255));
    }
    // --- Hilfsklasse für flexiblen Abstand ---
    public static class Spacer extends Region {
        public Spacer() { super(); HBox.setHgrow(this, Priority.ALWAYS); VBox.setVgrow(this, Priority.ALWAYS); setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE); }
    }

} // Ende der Klasse App