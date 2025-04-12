package com.todolist;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class App extends Application {

    // Lista observable para las tareas. JavaFX la actualizará automáticamente en la UI
    private ObservableList<String> tasks;
    // El componente visual que muestra la lista
    private ListView<String> taskListView;
    // Campo de texto para añadir nuevas tareas
    private TextField taskInput;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Mi Simple ToDoList");

        // 1. Inicializar la lista de tareas
        //    FXCollections.observableArrayList crea una lista que notifica a la UI
        //    cuando se añaden o eliminan elementos.
        tasks = FXCollections.observableArrayList();

        // 2. Crear los componentes de la UI
        taskListView = new ListView<>(tasks); // Vincula la ListView a la lista observable
        taskInput = new TextField();
        taskInput.setPromptText("Escribe una nueva tarea aquí..."); // Texto de ayuda

        Button addButton = new Button("Añadir");
        Button removeButton = new Button("Eliminar Seleccionada");

        // 3. Definir acciones para los botones (Event Handling)

        // Acción para el botón "Añadir"
        addButton.setOnAction(e -> addTask());

        // Permite añadir también presionando Enter en el TextField
        taskInput.setOnAction(e -> addTask());

        // Acción para el botón "Eliminar"
        removeButton.setOnAction(e -> removeTask());


        // 4. Organizar los componentes en Layouts

        // Layout para el input y los botones (Horizontal)
        HBox inputLayout = new HBox(10); // 10px de espacio entre elementos
        inputLayout.setPadding(new Insets(10)); // 10px de margen alrededor
        inputLayout.setAlignment(Pos.CENTER); // Centrar elementos horizontalmente
        // Hacemos que el TextField ocupe el espacio extra
        HBox.setHgrow(taskInput, javafx.scene.layout.Priority.ALWAYS);
        inputLayout.getChildren().addAll(taskInput, addButton, removeButton);

        // Layout principal (BorderPane)
        // Coloca la lista en el centro y el input/botones en la parte inferior.
        BorderPane mainLayout = new BorderPane();
        mainLayout.setCenter(taskListView); // La lista ocupa el espacio principal
        mainLayout.setBottom(inputLayout);   // El HBox va abajo

        // 5. Crear la Escena y mostrarla
        Scene scene = new Scene(mainLayout, 500, 400); // Tamaño un poco más grande
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Método para añadir una tarea (llamado por el botón y el Enter)
    private void addTask() {
        String taskText = taskInput.getText().trim(); // Obtiene texto y quita espacios extra
        if (!taskText.isEmpty()) { // Solo añade si no está vacío
            tasks.add(taskText);       // Añade a la lista observable (actualiza la UI)
            taskInput.clear();         // Limpia el campo de texto
        }
        taskInput.requestFocus(); // Devuelve el foco al campo de texto
    }

    // Método para eliminar la tarea seleccionada
    private void removeTask() {
        String selectedTask = taskListView.getSelectionModel().getSelectedItem();
        if (selectedTask != null) { // Verifica si algo está seleccionado
            tasks.remove(selectedTask); // Elimina de la lista observable (actualiza la UI)
        }
    }
}