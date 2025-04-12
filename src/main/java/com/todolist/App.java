package com.todolist;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform; // Asegúrate de tener esta importación
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

public class App extends Application {

    // --- Enum ---
    private enum Tool { PEN, ERASER, RECTANGLE, OVAL }

    // --- Estado ---
    private Tool currentTool = Tool.PEN;
    private Color currentColor = Color.BLACK;
    private Color backgroundColor = Color.WHITE;
    private double currentLineWidth = 2.0;
    private double startX, startY;

    // --- UI ---
    private Canvas canvas, previewCanvas;
    private GraphicsContext gc, previewGc;
    private Label widthValueLabel;
    private ColorPicker colorPicker;
    private Slider lineWidthSlider;
    private ToggleGroup toolGroup;
    private ToggleButton penButton, eraserButton, rectButton, ovalButton;
    private Button clearButton;
    private StackPane canvasPane;
    private PauseTransition debounceTimer;


    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Modern PaintFX - Robust Resize");
        canvas = new Canvas(); gc = canvas.getGraphicsContext2D();
        previewCanvas = new Canvas(); previewGc = previewCanvas.getGraphicsContext2D();
        setupGC(); setupPreviewGC();
        setupControls();
        setupLayoutsAndScene(primaryStage); // Configura bindings y listener de debounce
        setupMouseHandlers(); // Listeners de ratón
    }

    // --- Configuración GCs ---
    private void setupGC() {
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND); gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        gc.setLineWidth(currentLineWidth); gc.setStroke(currentColor);
    }
    private void setupPreviewGC() {
        if (previewGc == null) return;
        previewGc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND); previewGc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        previewGc.setLineWidth(currentLineWidth); previewGc.setStroke(currentColor);
    }

    // --- Eventos Ratón ---
    private void setupMouseHandlers() {
        if (previewCanvas == null) { System.err.println("PreviewCanvas es null en setupMouseHandlers"); return; }
        previewCanvas.setOnMousePressed(e -> { /* ... código como antes ... */
            startX = e.getX(); startY = e.getY();
            gc.setLineWidth(currentLineWidth); previewGc.setLineWidth(currentLineWidth);
            gc.setStroke(currentColor); previewGc.setStroke(currentColor);
            switch (currentTool) {
                case PEN: gc.beginPath(); gc.moveTo(startX, startY); gc.stroke(); break;
                case ERASER: gc.setStroke(backgroundColor); gc.beginPath(); gc.moveTo(startX, startY); gc.stroke(); gc.setStroke(currentColor); break;
                case RECTANGLE: case OVAL: previewGc.clearRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight()); break;
            }
         });
        previewCanvas.setOnMouseDragged(e -> { /* ... código como antes ... */
            double currentX = e.getX(); double currentY = e.getY();
            gc.setLineWidth(currentLineWidth);
            switch (currentTool) {
                case PEN: gc.lineTo(currentX, currentY); gc.stroke(); break;
                case ERASER: gc.setStroke(backgroundColor); gc.lineTo(currentX, currentY); gc.stroke(); gc.setStroke(currentColor); break;
                case RECTANGLE: case OVAL:
                    previewGc.clearRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());
                    drawPreviewShape(startX, startY, currentX, currentY, currentTool); break;
            }
         });
        previewCanvas.setOnMouseReleased(e -> { /* ... código como antes ... */
            double endX = e.getX(); double endY = e.getY();
            gc.setLineWidth(currentLineWidth); gc.setStroke(currentColor);
            switch (currentTool) {
                case PEN: case ERASER: break;
                case RECTANGLE: case OVAL:
                    previewGc.clearRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());
                    if(currentColor != backgroundColor && currentColor != Color.TRANSPARENT) {
                       drawPermanentShape(startX, startY, endX, endY, currentTool);
                    } break;
            }
            if(currentTool == Tool.ERASER) { gc.setStroke(currentColor); }
        });
        updateCursor();
    }

    // --- Métodos Dibujo ---
    private void drawPreviewShape(double x1, double y1, double x2, double y2, Tool shapeType) { /* ... como antes ... */
         if (previewGc == null) return; previewGc.setLineWidth(currentLineWidth); previewGc.setStroke(currentColor);
         double tlX = Math.min(x1, x2), tlY = Math.min(y1, y2), w = Math.abs(x1 - x2), h = Math.abs(y1 - y2);
         if(w > 0 && h > 0) { // Solo dibujar si hay tamaño
              if (shapeType == Tool.RECTANGLE) previewGc.strokeRect(tlX, tlY, w, h);
              else if (shapeType == Tool.OVAL) previewGc.strokeOval(tlX, tlY, w, h);
         }
    }
    private void drawPermanentShape(double x1, double y1, double x2, double y2, Tool shapeType) { /* ... como antes ... */
         if (gc == null) return; gc.setLineWidth(currentLineWidth); gc.setStroke(currentColor);
         double tlX = Math.min(x1, x2), tlY = Math.min(y1, y2), w = Math.abs(x1 - x2), h = Math.abs(y1 - y2);
          if(w > 0 && h > 0) { // Solo dibujar si hay tamaño
             if (shapeType == Tool.RECTANGLE) gc.strokeRect(tlX, tlY, w, h);
             else if (shapeType == Tool.OVAL) gc.strokeOval(tlX, tlY, w, h);
         }
    }

    // --- Configuración Controles ---
     private void setupControls() { /* ... código como antes, con verificaciones null si es necesario ... */
        this.toolGroup = new ToggleGroup();
        this.penButton = createToolButton("Lápiz", Tool.PEN, this.toolGroup, true);
        this.eraserButton = createToolButton("Borrador", Tool.ERASER, this.toolGroup, false);
        this.rectButton = createToolButton("Rectángulo", Tool.RECTANGLE, this.toolGroup, false);
        this.ovalButton = createToolButton("Óvalo", Tool.OVAL, this.toolGroup, false);

        this.colorPicker = new ColorPicker(currentColor); this.colorPicker.getStyleClass().add("control-element");
        this.colorPicker.setTooltip(new Tooltip("Selecciona Color"));
        this.colorPicker.setOnAction(_ -> {
            currentColor = colorPicker.getValue(); if (gc != null) gc.setStroke(currentColor); if (previewGc != null) previewGc.setStroke(currentColor);
        });

        this.lineWidthSlider = new Slider(1, 30, currentLineWidth); this.lineWidthSlider.setShowTickMarks(true); this.lineWidthSlider.setShowTickLabels(true);
        this.lineWidthSlider.setMajorTickUnit(10); this.lineWidthSlider.setMinorTickCount(4); this.lineWidthSlider.setBlockIncrement(1);
        this.lineWidthSlider.getStyleClass().add("control-element"); this.lineWidthSlider.setPrefWidth(150); this.lineWidthSlider.setTooltip(new Tooltip("Ajusta Grosor (1-30px)"));

        this.widthValueLabel = new Label(String.format("%.1f", currentLineWidth)); this.widthValueLabel.getStyleClass().add("info-label"); this.widthValueLabel.setMinWidth(35);

        this.lineWidthSlider.valueProperty().addListener((_, _, newVal) -> {
            currentLineWidth = newVal.doubleValue(); widthValueLabel.setText(String.format("%.1f", currentLineWidth));
             if (gc != null) gc.setLineWidth(currentLineWidth); if (previewGc != null) previewGc.setLineWidth(currentLineWidth);
        });

        this.clearButton = new Button("Limpiar"); this.clearButton.getStyleClass().addAll("control-element", "clear-button");
        this.clearButton.setTooltip(new Tooltip("Borra todo el dibujo")); this.clearButton.setOnAction(_ -> clearCanvas());

        this.toolGroup.selectedToggleProperty().addListener((_, _, newToggle) -> {
             if (previewGc != null) previewGc.clearRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());
             if (newToggle != null) { currentTool = (Tool) newToggle.getUserData(); }
             else { currentTool = Tool.PEN; if(this.penButton != null) this.penButton.setSelected(true); }
             updateCursor(); System.out.println("Herramienta seleccionada: " + currentTool);
        });
     }
    // --- Método crear botón ---
    private ToggleButton createToolButton(String text, Tool tool, ToggleGroup group, boolean selected) { /* ... como antes ... */
        ToggleButton button = new ToggleButton(text); button.setUserData(tool); button.setToggleGroup(group);
        button.setSelected(selected); button.getStyleClass().add("tool-button"); button.setTooltip(new Tooltip(text)); return button;
    }

    // --- LIMPIEZA ROBUSTA ---
    private void clearCanvas() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        // Solo intentar limpiar/rellenar si el tamaño es válido (>0)
        if (gc != null && width > 0 && height > 0) {
             gc.setFill(backgroundColor);
             gc.fillRect(0, 0, width, height);
             gc.setStroke(currentColor); // Restaurar trazo
            // System.out.println("Cleared main canvas: " + width + "x" + height);
        }

        if (previewGc != null && previewCanvas != null) {
            double previewWidth = previewCanvas.getWidth();
            double previewHeight = previewCanvas.getHeight();
             if (previewWidth > 0 && previewHeight > 0) {
                 previewGc.clearRect(0, 0, previewWidth, previewHeight);
                 // System.out.println("Cleared preview canvas: " + previewWidth + "x" + previewHeight);
             }
        }
         // Quitar o comentar esta línea para reducir ruido en consola
         System.out.println("clearCanvas() called for size: " + width + "x" + height);
    }


    // --- Actualiza cursor ---
    private void updateCursor() { /* ... como antes ... */
        if(previewCanvas == null) return;
        switch (currentTool) {
            case PEN: previewCanvas.setCursor(Cursor.CROSSHAIR); break; case ERASER: previewCanvas.setCursor(Cursor.HAND); break;
            case RECTANGLE: case OVAL: previewCanvas.setCursor(Cursor.DEFAULT); break; default: previewCanvas.setCursor(Cursor.DEFAULT); break;
        }
    }

    // --- Organiza Layouts, Escena, BINDINGS y DEBOUNCE LISTENER ---
    private void setupLayoutsAndScene(Stage primaryStage) {
        // --- Layout Controles (igual que antes) ---
        HBox controlsLayout = new HBox(10); controlsLayout.setPadding(new Insets(10)); controlsLayout.setAlignment(Pos.CENTER_LEFT);
        controlsLayout.getStyleClass().add("controls-bar");
        HBox widthLayout = new HBox(5, this.lineWidthSlider, this.widthValueLabel); widthLayout.setAlignment(Pos.CENTER_LEFT);
        Separator s1 = new Separator(javafx.geometry.Orientation.VERTICAL), s2 = new Separator(javafx.geometry.Orientation.VERTICAL);
        controlsLayout.getChildren().addAll(new Label("Herramientas:"), this.penButton, this.eraserButton, this.rectButton, this.ovalButton,
                s1, new Label("Color:"), this.colorPicker, s2, new Label("Grosor:"), widthLayout, new Spacer(), this.clearButton);

        // --- Layout Principal ---
        BorderPane root = new BorderPane();
        // --- StackPane ---
        canvasPane = new StackPane(); canvasPane.getChildren().addAll(this.canvas, this.previewCanvas);
        canvasPane.getStyleClass().add("canvas-container");
        root.setCenter(canvasPane); root.setTop(controlsLayout);

        // --- BINDINGS (igual que antes) ---
        canvas.widthProperty().bind(canvasPane.widthProperty()); canvas.heightProperty().bind(canvasPane.heightProperty());
        previewCanvas.widthProperty().bind(canvasPane.widthProperty()); previewCanvas.heightProperty().bind(canvasPane.heightProperty());

        // --- DEBOUNCE TIMER ---
        debounceTimer = new PauseTransition(Duration.millis(150)); // <<-- Puedes probar a aumentar slightly (ej: 150ms)
        debounceTimer.setOnFinished(event -> {
            System.out.println("Debounce finished -> clearCanvas(). Size: " + canvas.getWidth() + "x" + canvas.getHeight());
            clearCanvas(); // Llama a la limpieza robusta
        });

        // --- RESIZE LISTENER (reinicia timer) ---
        ChangeListener<Number> resizeListener = (observable, oldValue, newValue) -> {
             if(newValue != null && newValue.doubleValue() >= 0){ // Aceptar tamaño 0 aquí, la limpieza lo manejará
                 debounceTimer.playFromStart(); // Reiniciar timer en cada cambio
             }
        };
        canvasPane.widthProperty().addListener(resizeListener); canvasPane.heightProperty().addListener(resizeListener);

        // --- Crear Escena ---
        Scene scene = new Scene(root, 1024, 768);

        // --- Cargar CSS ---
        try { String cssPath = getClass().getResource("/styles.css").toExternalForm();
             if (cssPath != null) { scene.getStylesheets().add(cssPath); System.out.println("CSS cargado desde: " + cssPath); }
             else { System.err.println("Error: styles.css no encontrado."); }
        } catch (Exception e) { System.err.println("Error al cargar styles.css."); }

        // --- Mostrar Ventana ---
        primaryStage.setScene(scene);
        primaryStage.show();

        // --- Limpieza/Configuración Inicial Aplazada ---
        Platform.runLater(() -> {
            System.out.println("Initial Platform.runLater clear/setup GCs scheduled.");
             // Forzar una limpieza inicial robusta DESPUÉS de que todo esté visible
            clearCanvas();
            // Configurar GCs después de que el canvas tiene tamaño inicial y está limpio
            setupGC();
            setupPreviewGC();
        });
    }

    // --- Clase Spacer ---
    public static class Spacer extends Region {
        public Spacer() { super(); HBox.setHgrow(this, Priority.ALWAYS); VBox.setVgrow(this, Priority.ALWAYS); setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE); }
    }
} // Fin de la clase App