package nodegraph;

import javafx.application.Application;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class NodeGraphEditor extends Application {

    // ========================== DATA MODEL ==========================

    /**
     * Represents a connection port on a node (input or output).
     */
    static class Port extends StackPane {
        enum Type { INPUT, OUTPUT }

        final Type type;
        final GraphNode parentNode;
        final Circle circle;
        final List<Connection> connections = new ArrayList<>();

        Port(Type type, GraphNode parentNode) {
            this.type = type;
            this.parentNode = parentNode;
            this.circle = new Circle(6);
            circle.setFill(Color.web("#555555"));
            circle.setStroke(Color.web("#333333"));
            circle.setStrokeWidth(1.5);
            getChildren().add(circle);
            setCursor(Cursor.CROSSHAIR);
        }

        /** Get center position in scene coordinates */
        Point2D getCenterInScene() {
            Bounds bounds = circle.localToScene(circle.getBoundsInLocal());
            return new Point2D(
                    (bounds.getMinX() + bounds.getMaxX()) / 2,
                    (bounds.getMinY() + bounds.getMaxY()) / 2
            );
        }

        /** Get center position relative to the canvas group */
        Point2D getCenterInCanvas(Group canvas) {
            Point2D scenePos = getCenterInScene();
            return canvas.sceneToLocal(scenePos);
        }

        void setHighlight(boolean on) {
            circle.setFill(on ? Color.web("#00cc66") : Color.web("#555555"));
            circle.setRadius(on ? 8 : 6);
        }
    }

    /**
     * A draggable node on the graph canvas.
     */
    static class GraphNode extends VBox {
        String title;
        Color baseColor;
        final List<Port> inputPorts = new ArrayList<>();
        final List<Port> outputPorts = new ArrayList<>();

        private double dragOffsetX, dragOffsetY;
        private GraphEditorPane editor;

        // UI parts
        private final Label titleLabel;
        private final HBox portsRow;
        private final VBox inputBox;
        private final VBox outputBox;

        GraphNode(String title, Color baseColor, int inputs, int outputs, GraphEditorPane editor) {
            this.title = title;
            this.baseColor = baseColor;
            this.editor = editor;

            // Styling
            String colorHex = toHex(baseColor);
            String darkerHex = toHex(baseColor.darker());
            setStyle(String.format(
                    "-fx-background-color: %s; -fx-background-radius: 8; " +
                            "-fx-border-color: %s; -fx-border-radius: 8; -fx-border-width: 2; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 2, 2);",
                    colorHex, darkerHex));
            setPadding(new Insets(0));
            setMinWidth(140);
            setAlignment(Pos.CENTER);
            setSpacing(0);

            // Title bar
            titleLabel = new Label(title);
            titleLabel.setFont(Font.font("System", FontWeight.BOLD, 13));
            titleLabel.setTextFill(Color.web("#222222"));
            titleLabel.setPadding(new Insets(8, 16, 4, 16));
            titleLabel.setMaxWidth(Double.MAX_VALUE);
            titleLabel.setAlignment(Pos.CENTER);

            // Ports row
            inputBox = new VBox(6);
            inputBox.setAlignment(Pos.CENTER_LEFT);
            outputBox = new VBox(6);
            outputBox.setAlignment(Pos.CENTER_RIGHT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            portsRow = new HBox(spacer);
            portsRow.setPadding(new Insets(4, 6, 8, 6));
            portsRow.getChildren().setAll(inputBox, spacer, outputBox);

            getChildren().addAll(titleLabel, portsRow);

            // Create ports
            for (int i = 0; i < inputs; i++) {
                Port p = new Port(Port.Type.INPUT, this);
                inputPorts.add(p);
                inputBox.getChildren().add(p);
            }
            for (int i = 0; i < outputs; i++) {
                Port p = new Port(Port.Type.OUTPUT, this);
                outputPorts.add(p);
                outputBox.getChildren().add(p);
            }

            // If no ports, still keep some padding
            if (inputs == 0 && outputs == 0) {
                portsRow.setPadding(new Insets(0, 6, 8, 6));
            }

            setupDrag();
            setupContextMenu();
        }

        private void setupDrag() {
            titleLabel.setCursor(Cursor.MOVE);
            // Drag on title
            titleLabel.setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    dragOffsetX = e.getSceneX() - getLayoutX();
                    dragOffsetY = e.getSceneY() - getLayoutY();
                    toFront();
                    e.consume();
                }
            });
            titleLabel.setOnMouseDragged(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    // Account for canvas transform
                    Group canvas = editor.canvas;
                    double scale = canvas.getScaleX();
                    double newX = getLayoutX() + (e.getSceneX() - dragOffsetX - getLayoutX());
                    double newY = getLayoutY() + (e.getSceneY() - dragOffsetY - getLayoutY());

                    // Simpler: use scene delta divided by scale
                    setLayoutX(e.getSceneX() - dragOffsetX);
                    setLayoutY(e.getSceneY() - dragOffsetY);
                    dragOffsetX = e.getSceneX() - getLayoutX();
                    dragOffsetY = e.getSceneY() - getLayoutY();

                    editor.updateConnections();
                    e.consume();
                }
            });
        }

        private void setupContextMenu() {
            ContextMenu menu = new ContextMenu();
            MenuItem rename = new MenuItem("Rename");
            rename.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog(title);
                dialog.setTitle("Rename Node");
                dialog.setHeaderText(null);
                dialog.setContentText("New name:");
                dialog.showAndWait().ifPresent(name -> {
                    title = name;
                    titleLabel.setText(name);
                });
            });
            MenuItem addInput = new MenuItem("Add Input Port");
            addInput.setOnAction(e -> {
                Port p = new Port(Port.Type.INPUT, this);
                inputPorts.add(p);
                inputBox.getChildren().add(p);
                editor.setupPortHandlers(p);
            });
            MenuItem addOutput = new MenuItem("Add Output Port");
            addOutput.setOnAction(e -> {
                Port p = new Port(Port.Type.OUTPUT, this);
                outputPorts.add(p);
                outputBox.getChildren().add(p);
                editor.setupPortHandlers(p);
            });
            MenuItem changeColor = new MenuItem("Change Color");
            changeColor.setOnAction(e -> {
                ColorPicker picker = new ColorPicker(baseColor);
                Dialog<Color> colorDialog = new Dialog<>();
                colorDialog.setTitle("Choose Color");
                colorDialog.getDialogPane().setContent(picker);
                colorDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
                colorDialog.setResultConverter(btn -> btn == ButtonType.OK ? picker.getValue() : null);
                colorDialog.showAndWait().ifPresent(c -> {
                    baseColor = c;
                    String colorHex = toHex(c);
                    String darkerHex = toHex(c.darker());
                    setStyle(String.format(
                            "-fx-background-color: %s; -fx-background-radius: 8; " +
                                    "-fx-border-color: %s; -fx-border-radius: 8; -fx-border-width: 2; " +
                                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 2, 2);",
                            colorHex, darkerHex));
                });
            });
            MenuItem delete = new MenuItem("Delete Node");
            delete.setOnAction(e -> editor.removeNode(this));

            menu.getItems().addAll(rename, new SeparatorMenuItem(), addInput, addOutput,
                    new SeparatorMenuItem(), changeColor, new SeparatorMenuItem(), delete);
            setOnContextMenuRequested(e -> {
                menu.show(this, e.getScreenX(), e.getScreenY());
                e.consume();
            });
        }

        static String toHex(Color c) {
            return String.format("#%02x%02x%02x",
                    (int) (c.getRed() * 255),
                    (int) (c.getGreen() * 255),
                    (int) (c.getBlue() * 255));
        }
    }

    /**
     * A connection (edge) between two ports, drawn as a cubic curve with an arrowhead.
     */
    static class Connection {
        final Port source; // output port
        final Port target; // input port
        final CubicCurve curve;
        final Polygon arrowHead;
        final Group group;

        Connection(Port source, Port target) {
            this.source = source;
            this.target = target;

            curve = new CubicCurve();
            curve.setStroke(Color.web("#444444"));
            curve.setStrokeWidth(2.5);
            curve.setFill(Color.TRANSPARENT);
            curve.setStrokeLineCap(StrokeLineCap.ROUND);

            arrowHead = new Polygon();
            arrowHead.setFill(Color.web("#444444"));

            group = new Group(curve, arrowHead);

            source.connections.add(this);
            target.connections.add(this);
        }

        void update(Group canvas) {
            Point2D start = source.getCenterInCanvas(canvas);
            Point2D end = target.getCenterInCanvas(canvas);

            double dx = Math.abs(end.getX() - start.getX()) * 0.5;
            dx = Math.max(dx, 50);

            curve.setStartX(start.getX());
            curve.setStartY(start.getY());
            curve.setControlX1(start.getX() + dx);
            curve.setControlY1(start.getY());
            curve.setControlX2(end.getX() - dx);
            curve.setControlY2(end.getY());
            curve.setEndX(end.getX());
            curve.setEndY(end.getY());

            // Arrowhead at end
            double arrowSize = 10;
            // Tangent at endpoint: derivative of cubic bezier at t=1
            double tx = 3 * (end.getX() - (end.getX() - dx));
            double ty = 3 * (end.getY() - end.getY());
            // Simpler: direction from control point 2 to end
            double dirX = end.getX() - (end.getX() - dx);
            double dirY = end.getY() - end.getY();
            double len = Math.sqrt(dirX * dirX + dirY * dirY);
            if (len < 0.001) { dirX = 1; dirY = 0; len = 1; }
            dirX /= len;
            dirY /= len;

            double perpX = -dirY;
            double perpY = dirX;

            double tipX = end.getX();
            double tipY = end.getY();
            double baseX = tipX - dirX * arrowSize;
            double baseY = tipY - dirY * arrowSize;

            arrowHead.getPoints().setAll(
                    tipX, tipY,
                    baseX + perpX * arrowSize * 0.4, baseY + perpY * arrowSize * 0.4,
                    baseX - perpX * arrowSize * 0.4, baseY - perpY * arrowSize * 0.4
            );
        }

        void removeFromPorts() {
            source.connections.remove(this);
            target.connections.remove(this);
        }
    }

    // ========================== EDITOR PANE ==========================

    /**
     * The main editor pane with canvas, zoom/pan, and interaction logic.
     */
    static class GraphEditorPane extends BorderPane {
        final Group canvas = new Group();
        final Pane canvasPane = new Pane(canvas);
        final List<GraphNode> nodes = new ArrayList<>();
        final List<Connection> connections = new ArrayList<>();

        // For creating new connections by dragging from a port
        private Port dragSourcePort = null;
        private CubicCurve tempCurve = null;

        // For panning
        private double panStartX, panStartY;
        private double canvasTranslateStartX, canvasTranslateStartY;

        // Grid
        private final Pane gridPane = new Pane();

        GraphEditorPane() {
            // Background
            canvasPane.setStyle("-fx-background-color: #f0f0f0;");

            // Draw grid
            drawGrid();
            canvasPane.getChildren().add(0, gridPane);

            setCenter(canvasPane);

            // Toolbar
            ToolBar toolbar = createToolbar();
            setTop(toolbar);

            setupPanAndZoom();
            setupCanvasContextMenu();
        }

        private ToolBar createToolbar() {
            Button addNodeBtn = new Button("Add Node");
            addNodeBtn.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog("NewNode");
                dialog.setTitle("Add Node");
                dialog.setHeaderText(null);
                dialog.setContentText("Node name:");
                dialog.showAndWait().ifPresent(name -> {
                    GraphNode node = new GraphNode(name, Color.web("#a8c8e8"), 1, 1, this);
                    node.setLayoutX(200);
                    node.setLayoutY(200);
                    addNode(node);
                });
            });

            Button fitBtn = new Button("Fit View");
            fitBtn.setOnAction(e -> fitView());

            Button resetBtn = new Button("Reset Zoom");
            resetBtn.setOnAction(e -> {
                canvas.setScaleX(1);
                canvas.setScaleY(1);
                canvas.setTranslateX(0);
                canvas.setTranslateY(0);
            });

            Label hint = new Label("  Right-click canvas to add nodes | Drag from ports to connect | Scroll to zoom | Middle-click to pan");
            hint.setTextFill(Color.GRAY);
            hint.setFont(Font.font(11));

            return new ToolBar(addNodeBtn, new Separator(), fitBtn, resetBtn, new Separator(), hint);
        }

        private void drawGrid() {
            gridPane.setMouseTransparent(true);
            // Grid will be drawn in layoutChildren or we use a simple approach
            canvasPane.widthProperty().addListener((o, a, b) -> rebuildGrid());
            canvasPane.heightProperty().addListener((o, a, b) -> rebuildGrid());
        }

        private void rebuildGrid() {
            gridPane.getChildren().clear();
            double w = canvasPane.getWidth();
            double h = canvasPane.getHeight();
            double step = 30;
            for (double x = 0; x < w; x += step) {
                Line line = new Line(x, 0, x, h);
                line.setStroke(Color.web("#e0e0e0"));
                line.setStrokeWidth(0.5);
                gridPane.getChildren().add(line);
            }
            for (double y = 0; y < h; y += step) {
                Line line = new Line(0, y, w, y);
                line.setStroke(Color.web("#e0e0e0"));
                line.setStrokeWidth(0.5);
                gridPane.getChildren().add(line);
            }
        }

        private void setupPanAndZoom() {
            // Zoom with scroll
            canvasPane.addEventFilter(ScrollEvent.SCROLL, e -> {
                double zoomFactor = e.getDeltaY() > 0 ? 1.1 : 0.9;
                double oldScale = canvas.getScaleX();
                double newScale = oldScale * zoomFactor;
                newScale = Math.max(0.2, Math.min(3.0, newScale));

                // Zoom towards mouse position
                Point2D mouseInCanvas = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                canvas.setScaleX(newScale);
                canvas.setScaleY(newScale);

                Point2D mouseInCanvasAfter = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                double dx = mouseInCanvasAfter.getX() - mouseInCanvas.getX();
                double dy = mouseInCanvasAfter.getY() - mouseInCanvas.getY();
                canvas.setTranslateX(canvas.getTranslateX() + dx * newScale);
                canvas.setTranslateY(canvas.getTranslateY() + dy * newScale);

                e.consume();
            });

            // Pan with middle mouse button or Ctrl+left click
            canvasPane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (e.getButton() == MouseButton.MIDDLE ||
                        (e.getButton() == MouseButton.PRIMARY && e.isControlDown())) {
                    panStartX = e.getSceneX();
                    panStartY = e.getSceneY();
                    canvasTranslateStartX = canvas.getTranslateX();
                    canvasTranslateStartY = canvas.getTranslateY();
                    canvasPane.setCursor(Cursor.CLOSED_HAND);
                    e.consume();
                }
            });
            canvasPane.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
                if (e.getButton() == MouseButton.MIDDLE ||
                        (e.getButton() == MouseButton.PRIMARY && e.isControlDown())) {
                    canvas.setTranslateX(canvasTranslateStartX + (e.getSceneX() - panStartX));
                    canvas.setTranslateY(canvasTranslateStartY + (e.getSceneY() - panStartY));
                    e.consume();
                }
            });
            canvasPane.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
                if (e.getButton() == MouseButton.MIDDLE ||
                        (e.getButton() == MouseButton.PRIMARY && e.isControlDown())) {
                    canvasPane.setCursor(Cursor.DEFAULT);
                }
            });
        }

        private void setupCanvasContextMenu() {
            ContextMenu canvasMenu = new ContextMenu();

            MenuItem addBlue = new MenuItem("Add Blue Node");
            addBlue.setOnAction(e -> promptAndAddNode(Color.web("#a8c8e8")));
            MenuItem addRed = new MenuItem("Add Red Node");
            addRed.setOnAction(e -> promptAndAddNode(Color.web("#e8b8b8")));
            MenuItem addGreen = new MenuItem("Add Green Node");
            addGreen.setOnAction(e -> promptAndAddNode(Color.web("#b8e8b8")));
            MenuItem addYellow = new MenuItem("Add Yellow Node");
            addYellow.setOnAction(e -> promptAndAddNode(Color.web("#e8e8a8")));

            canvasMenu.getItems().addAll(addBlue, addRed, addGreen, addYellow);

            canvasPane.setOnContextMenuRequested(e -> {
                // Only show if click was on canvas, not on a node
                if (e.getTarget() == canvasPane || e.getTarget() instanceof Line) {
                    canvasMenu.show(canvasPane, e.getScreenX(), e.getScreenY());
                }
                e.consume();
            });
        }

        private double lastContextX = 300, lastContextY = 200;

        private void promptAndAddNode(Color color) {
            TextInputDialog dialog = new TextInputDialog("Node");
            dialog.setTitle("New Node");
            dialog.setHeaderText(null);
            dialog.setContentText("Name:");
            dialog.showAndWait().ifPresent(name -> {
                GraphNode node = new GraphNode(name, color, 1, 1, this);
                // Place near center of visible area
                Point2D center = canvas.sceneToLocal(
                        canvasPane.getWidth() / 2, canvasPane.getHeight() / 2);
                node.setLayoutX(center.getX() + Math.random() * 80 - 40);
                node.setLayoutY(center.getY() + Math.random() * 80 - 40);
                addNode(node);
            });
        }

        void addNode(GraphNode node) {
            nodes.add(node);
            canvas.getChildren().add(node);

            // Setup port handlers for all ports
            for (Port p : node.inputPorts) setupPortHandlers(p);
            for (Port p : node.outputPorts) setupPortHandlers(p);
        }

        void removeNode(GraphNode node) {
            // Remove all connections to/from this node
            List<Connection> toRemove = new ArrayList<>();
            for (Port p : node.inputPorts) toRemove.addAll(p.connections);
            for (Port p : node.outputPorts) toRemove.addAll(p.connections);
            for (Connection c : toRemove) removeConnection(c);

            nodes.remove(node);
            canvas.getChildren().remove(node);
        }

        void removeConnection(Connection conn) {
            conn.removeFromPorts();
            canvas.getChildren().remove(conn.group);
            connections.remove(conn);
        }

        void setupPortHandlers(Port port) {
            port.setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    // Start dragging a new connection
                    if (port.type == Port.Type.OUTPUT) {
                        dragSourcePort = port;
                    } else {
                        // Allow dragging from input too (will be reversed)
                        dragSourcePort = port;
                    }
                    tempCurve = new CubicCurve();
                    tempCurve.setStroke(Color.web("#00aa55"));
                    tempCurve.setStrokeWidth(2.5);
                    tempCurve.setStrokeDashOffset(0);
                    tempCurve.getStrokeDashArray().addAll(8.0, 4.0);
                    tempCurve.setFill(Color.TRANSPARENT);
                    tempCurve.setMouseTransparent(true);

                    Point2D start = port.getCenterInCanvas(canvas);
                    tempCurve.setStartX(start.getX());
                    tempCurve.setStartY(start.getY());
                    tempCurve.setEndX(start.getX());
                    tempCurve.setEndY(start.getY());
                    tempCurve.setControlX1(start.getX());
                    tempCurve.setControlY1(start.getY());
                    tempCurve.setControlX2(start.getX());
                    tempCurve.setControlY2(start.getY());

                    canvas.getChildren().add(tempCurve);
                    e.consume();
                }
            });

            port.setOnMouseDragged(e -> {
                if (tempCurve != null && dragSourcePort != null) {
                    Point2D mouse = canvas.sceneToLocal(e.getSceneX(), e.getSceneY());
                    Point2D start = dragSourcePort.getCenterInCanvas(canvas);

                    tempCurve.setEndX(mouse.getX());
                    tempCurve.setEndY(mouse.getY());

                    double dx = Math.abs(mouse.getX() - start.getX()) * 0.5;
                    dx = Math.max(dx, 40);
                    if (dragSourcePort.type == Port.Type.OUTPUT) {
                        tempCurve.setControlX1(start.getX() + dx);
                        tempCurve.setControlX2(mouse.getX() - dx);
                    } else {
                        tempCurve.setControlX1(start.getX() - dx);
                        tempCurve.setControlX2(mouse.getX() + dx);
                    }
                    tempCurve.setControlY1(start.getY());
                    tempCurve.setControlY2(mouse.getY());

                    // Highlight nearby compatible ports
                    highlightCompatiblePort(e.getSceneX(), e.getSceneY());
                    e.consume();
                }
            });

            port.setOnMouseReleased(e -> {
                if (tempCurve != null && dragSourcePort != null) {
                    // Check if released over a compatible port
                    Port target = findPortAt(e.getSceneX(), e.getSceneY());
                    if (target != null && target != dragSourcePort &&
                            target.parentNode != dragSourcePort.parentNode &&
                            target.type != dragSourcePort.type) {

                        Port src, dst;
                        if (dragSourcePort.type == Port.Type.OUTPUT) {
                            src = dragSourcePort;
                            dst = target;
                        } else {
                            src = target;
                            dst = dragSourcePort;
                        }

                        // Check if connection already exists
                        boolean exists = connections.stream().anyMatch(
                                c -> c.source == src && c.target == dst);
                        if (!exists) {
                            createConnection(src, dst);
                        }
                    }

                    canvas.getChildren().remove(tempCurve);
                    tempCurve = null;
                    dragSourcePort = null;
                    clearPortHighlights();
                    e.consume();
                }
            });

            // Right-click on port to remove connections
            port.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.SECONDARY && !port.connections.isEmpty()) {
                    ContextMenu portMenu = new ContextMenu();
                    for (Connection c : new ArrayList<>(port.connections)) {
                        String label = c.source.parentNode.title + " -> " + c.target.parentNode.title;
                        MenuItem item = new MenuItem("Remove: " + label);
                        item.setOnAction(ev -> removeConnection(c));
                        portMenu.getItems().add(item);
                    }
                    portMenu.show(port, e.getScreenX(), e.getScreenY());
                    e.consume();
                }
            });
        }

        void createConnection(Port source, Port target) {
            Connection conn = new Connection(source, target);
            connections.add(conn);
            canvas.getChildren().add(0, conn.group); // Add behind nodes
            conn.update(canvas);

            // Setup click to delete on the curve
            conn.curve.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.SECONDARY) {
                    ContextMenu menu = new ContextMenu();
                    MenuItem del = new MenuItem("Delete Connection");
                    del.setOnAction(ev -> removeConnection(conn));
                    menu.getItems().add(del);
                    menu.show(conn.curve, e.getScreenX(), e.getScreenY());
                    e.consume();
                }
            });
            conn.curve.setCursor(Cursor.HAND);
            conn.curve.setStrokeWidth(2.5);
            conn.curve.setOnMouseEntered(e -> conn.curve.setStroke(Color.web("#cc4444")));
            conn.curve.setOnMouseExited(e -> conn.curve.setStroke(Color.web("#444444")));
        }

        private Port findPortAt(double sceneX, double sceneY) {
            for (GraphNode node : nodes) {
                for (Port p : node.inputPorts) {
                    if (isNear(p, sceneX, sceneY)) return p;
                }
                for (Port p : node.outputPorts) {
                    if (isNear(p, sceneX, sceneY)) return p;
                }
            }
            return null;
        }

        private boolean isNear(Port port, double sceneX, double sceneY) {
            Point2D center = port.getCenterInScene();
            double dist = center.distance(sceneX, sceneY);
            return dist < 20;
        }

        private void highlightCompatiblePort(double sceneX, double sceneY) {
            clearPortHighlights();
            if (dragSourcePort == null) return;
            for (GraphNode node : nodes) {
                if (node == dragSourcePort.parentNode) continue;
                List<Port> targets = dragSourcePort.type == Port.Type.OUTPUT ?
                        node.inputPorts : node.outputPorts;
                for (Port p : targets) {
                    if (isNear(p, sceneX, sceneY)) {
                        p.setHighlight(true);
                    }
                }
            }
        }

        private void clearPortHighlights() {
            for (GraphNode node : nodes) {
                for (Port p : node.inputPorts) p.setHighlight(false);
                for (Port p : node.outputPorts) p.setHighlight(false);
            }
        }

        void updateConnections() {
            for (Connection c : connections) {
                c.update(canvas);
            }
        }

        void fitView() {
            if (nodes.isEmpty()) return;
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (GraphNode n : nodes) {
                minX = Math.min(minX, n.getLayoutX());
                minY = Math.min(minY, n.getLayoutY());
                maxX = Math.max(maxX, n.getLayoutX() + n.getWidth());
                maxY = Math.max(maxY, n.getLayoutY() + n.getHeight());
            }
            double graphW = maxX - minX + 100;
            double graphH = maxY - minY + 100;
            double paneW = canvasPane.getWidth();
            double paneH = canvasPane.getHeight();
            double scale = Math.min(paneW / graphW, paneH / graphH);
            scale = Math.min(scale, 1.5);
            canvas.setScaleX(scale);
            canvas.setScaleY(scale);
            canvas.setTranslateX(paneW / 2 - (minX + maxX) / 2 * scale);
            canvas.setTranslateY(paneH / 2 - (minY + maxY) / 2 * scale);
        }
    }

    // ========================== MAIN APP ==========================

    @Override
    public void start(Stage stage) {
        GraphEditorPane editor = new GraphEditorPane();

        // Create demo nodes matching the uploaded image
        GraphNode sfPlayer = new GraphNode("sfPlayer", Color.web("#a8c8e8"), 1, 2, editor);
        sfPlayer.setLayoutX(50);
        sfPlayer.setLayoutY(80);

        GraphNode fabric = new GraphNode("Fabric\n(Not Real)", Color.web("#e8b0b0"), 1, 0, editor);
        fabric.setLayoutX(50);
        fabric.setLayoutY(250);

        GraphNode industrialCraft = new GraphNode("IndustrialCraft", Color.web("#a8c8e8"), 2, 2, editor);
        industrialCraft.setLayoutX(300);
        industrialCraft.setLayoutY(80);

        GraphNode gregTech3 = new GraphNode("GregTech 3", Color.web("#a8c8e8"), 1, 0, editor);
        gregTech3.setLayoutX(550);
        gregTech3.setLayoutY(50);

        GraphNode gregTech4 = new GraphNode("GregTech 4", Color.web("#a8c8e8"), 1, 0, editor);
        gregTech4.setLayoutX(550);
        gregTech4.setLayoutY(220);

        // Add nodes
        editor.addNode(sfPlayer);
        editor.addNode(fabric);
        editor.addNode(industrialCraft);
        editor.addNode(gregTech3);
        editor.addNode(gregTech4);

        // Create connections matching the image
        // sfPlayer output 0 -> IndustrialCraft input 0
        editor.createConnection(sfPlayer.outputPorts.get(0), industrialCraft.inputPorts.get(0));
        // sfPlayer output 1 -> Fabric input 0
        editor.createConnection(sfPlayer.outputPorts.get(1), fabric.inputPorts.get(0));
        // IndustrialCraft output 0 -> GregTech 3 input 0
        editor.createConnection(industrialCraft.outputPorts.get(0), gregTech3.inputPorts.get(0));
        // IndustrialCraft output 1 -> GregTech 4 input 0
        editor.createConnection(industrialCraft.outputPorts.get(1), gregTech4.inputPorts.get(0));

        // Update connections after layout
        javafx.application.Platform.runLater(editor::updateConnections);
        // Delayed update to ensure layout is done
        javafx.application.Platform.runLater(() ->
                javafx.application.Platform.runLater(editor::updateConnections));

        Scene scene = new Scene(editor, 900, 550);
        stage.setTitle("Node Graph Editor");
        stage.setScene(scene);
        stage.show();

        // Final connection update after window is shown
        javafx.application.Platform.runLater(editor::updateConnections);
    }

    public static void main(String[] args) {
        launch(args);
    }
}