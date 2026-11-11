import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main {
    private static final Path APP_FOLDER = Paths.get("").toAbsolutePath();
    private static final String DATABASE_URL = getSetting(
            "RESTAURANT_DB_URL", "jdbc:mysql://localhost:3307/restaurant_system?serverTimezone=UTC");
    private static final String DATABASE_USER = getSetting("RESTAURANT_DB_USER", "root");
    private static final String DATABASE_PASSWORD = getSetting("RESTAURANT_DB_PASSWORD", "");
    private static final Gson GSON = new Gson();
    private static final Map<String, String> PAGES = new HashMap<String, String>();

    static {
        PAGES.put("/", "index.html");
        PAGES.put("/index.html", "index.html");
        PAGES.put("/styles.css", "styles.css");
        PAGES.put("/app.js", "app.js");
    }

    public static void main(String[] args) throws IOException {
        initializeDatabase();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8080), 0);
        server.createContext("/", Main::handleRequest);
        server.start();
        System.out.println("Restaurant Ordering System is running at http://localhost:8080");
        System.out.println("MySQL database: " + DATABASE_URL);
    }

    private static String getSetting(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Connection openDatabase() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD);
    }

    private static void initializeDatabase() {
        try (Connection connection = openDatabase();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS categories ("
                    + "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE) ENGINE=InnoDB");
            statement.execute("CREATE TABLE IF NOT EXISTS restaurant_tables ("
                    + "id INT PRIMARY KEY, name VARCHAR(50) NOT NULL UNIQUE) ENGINE=InnoDB");
            statement.execute("CREATE TABLE IF NOT EXISTS guests ("
                    + "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(120) NOT NULL, "
                    + "contact VARCHAR(255)) ENGINE=InnoDB");
            statement.execute("CREATE TABLE IF NOT EXISTS foods ("
                    + "id VARCHAR(36) PRIMARY KEY, name VARCHAR(150) NOT NULL, category_id INT NOT NULL, "
                    + "price DECIMAL(10, 2) NOT NULL CHECK(price > 0), "
                    + "FOREIGN KEY(category_id) REFERENCES categories(id)) ENGINE=InnoDB");
            statement.execute("CREATE TABLE IF NOT EXISTS orders ("
                    + "id VARCHAR(36) PRIMARY KEY, guest_id INT NOT NULL, table_id INT NOT NULL, "
                    + "status VARCHAR(10) NOT NULL CHECK(status IN ('open', 'closed')), created_at BIGINT NOT NULL, "
                    + "FOREIGN KEY(guest_id) REFERENCES guests(id) ON DELETE CASCADE, "
                    + "FOREIGN KEY(table_id) REFERENCES restaurant_tables(id)) ENGINE=InnoDB");
            statement.execute("CREATE TABLE IF NOT EXISTS order_items ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id VARCHAR(36) NOT NULL, "
                    + "food_id VARCHAR(36), food_name VARCHAR(150) NOT NULL, "
                    + "unit_price DECIMAL(10, 2) NOT NULL, quantity INT NOT NULL, "
                    + "FOREIGN KEY(order_id) REFERENCES orders(id) ON DELETE CASCADE, "
                    + "FOREIGN KEY(food_id) REFERENCES foods(id) ON DELETE SET NULL) ENGINE=InnoDB");

            String[] categories = {"Starter", "Main course", "Dessert", "Drink", "Other"};
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT IGNORE INTO categories(name) VALUES (?)")) {
                for (String category : categories) {
                    insert.setString(1, category);
                    insert.executeUpdate();
                }
            }

            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT IGNORE INTO restaurant_tables(id, name) VALUES (?, ?)")) {
                for (int table = 1; table <= 8; table++) {
                    insert.setInt(1, table);
                    insert.setString(2, "Table " + table);
                    insert.executeUpdate();
                }
            }

            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM foods")) {
                if (result.next() && result.getInt(1) == 0) {
                    addStarterFood(connection, "Tomato soup", "Starter", 5.50);
                    addStarterFood(connection, "Margherita pizza", "Main course", 12.50);
                    addStarterFood(connection, "Chocolate cake", "Dessert", 6.00);
                    addStarterFood(connection, "Lemonade", "Drink", 3.00);
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException(
                    "Could not connect to or initialize MySQL. Start MySQL in XAMPP and check "
                            + "RESTAURANT_DB_URL, RESTAURANT_DB_USER, and RESTAURANT_DB_PASSWORD.",
                    error);
        }
    }

    private static void addStarterFood(Connection connection, String name, String category, double price)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO foods(id, name, category_id, price) "
                        + "SELECT ?, ?, id, ? FROM categories WHERE name = ?")) {
            insert.setString(1, UUID.randomUUID().toString());
            insert.setString(2, name);
            insert.setDouble(3, price);
            insert.setString(4, category);
            insert.executeUpdate();
        }
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
                return;
            }

            String fileName = PAGES.get(path);
            if (fileName == null) {
                sendText(exchange, 404, "Page not found");
                return;
            }

            Path file = APP_FOLDER.resolve(fileName);
            if (!Files.exists(file)) {
                sendText(exchange, 500, "The website files are missing.");
                return;
            }

            String contentType = fileName.endsWith(".css")
                    ? "text/css; charset=UTF-8"
                    : fileName.endsWith(".js")
                    ? "text/javascript; charset=UTF-8"
                    : "text/html; charset=UTF-8";
            byte[] content = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream response = exchange.getResponseBody()) {
                response.write(content);
            }
        } catch (ApiException error) {
            sendJson(exchange, error.status, errorJson(error.getMessage()));
        } catch (SQLException error) {
            error.printStackTrace();
            sendJson(exchange, 500, errorJson("A database error occurred."));
        } catch (RuntimeException error) {
            error.printStackTrace();
            sendJson(exchange, 400, errorJson("The request contains invalid data."));
        } finally {
            exchange.close();
        }
    }

    private static void handleApi(HttpExchange exchange, String path) throws SQLException, IOException {
        String method = exchange.getRequestMethod();

        if ("/api/foods".equals(path)) {
            if ("GET".equals(method)) {
                sendJson(exchange, 200, getFoods());
            } else if ("POST".equals(method)) {
                JsonObject food = readJson(exchange);
                String id = UUID.randomUUID().toString();
                saveFood(id, food, false);
                sendJson(exchange, 201, getFood(id));
            } else {
                throw new ApiException(405, "Method not allowed.");
            }
            return;
        }

        if (path.startsWith("/api/foods/")) {
            String id = path.substring("/api/foods/".length());
            if ("PUT".equals(method)) {
                saveFood(id, readJson(exchange), true);
                sendJson(exchange, 200, getFood(id));
            } else if ("DELETE".equals(method)) {
                deleteFood(id);
                sendJson(exchange, 200, new JsonObject());
            } else {
                throw new ApiException(405, "Method not allowed.");
            }
            return;
        }

        if ("/api/orders".equals(path)) {
            if ("GET".equals(method)) {
                sendJson(exchange, 200, getOrders());
            } else if ("POST".equals(method)) {
                String id = UUID.randomUUID().toString();
                saveOrder(id, readJson(exchange), false);
                sendJson(exchange, 201, getOrder(id));
            } else {
                throw new ApiException(405, "Method not allowed.");
            }
            return;
        }

        if (path.startsWith("/api/orders/")) {
            String id = path.substring("/api/orders/".length());
            if (id.endsWith("/close") && "PATCH".equals(method)) {
                closeOrder(id.substring(0, id.length() - "/close".length()));
                sendJson(exchange, 200, getOrder(id.substring(0, id.length() - "/close".length())));
            } else if ("PUT".equals(method)) {
                saveOrder(id, readJson(exchange), true);
                sendJson(exchange, 200, getOrder(id));
            } else if ("DELETE".equals(method)) {
                deleteOrder(id);
                sendJson(exchange, 200, new JsonObject());
            } else {
                throw new ApiException(405, "Method not allowed.");
            }
            return;
        }

        throw new ApiException(404, "API endpoint not found.");
    }

    private static JsonArray getFoods() throws SQLException {
        JsonArray foods = new JsonArray();
        try (Connection connection = openDatabase();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT foods.id, foods.name, categories.name AS category, foods.price "
                             + "FROM foods JOIN categories ON foods.category_id = categories.id "
                             + "ORDER BY foods.name");
             ResultSet result = query.executeQuery()) {
            while (result.next()) {
                JsonObject food = new JsonObject();
                food.addProperty("id", result.getString("id"));
                food.addProperty("name", result.getString("name"));
                food.addProperty("category", result.getString("category"));
                food.addProperty("price", result.getDouble("price"));
                foods.add(food);
            }
        }
        return foods;
    }

    private static JsonObject getFood(String id) throws SQLException {
        try (Connection connection = openDatabase();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT foods.id, foods.name, categories.name AS category, foods.price "
                             + "FROM foods JOIN categories ON foods.category_id = categories.id "
                             + "WHERE foods.id = ?")) {
            query.setString(1, id);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new ApiException(404, "Food item not found.");
                }
                JsonObject food = new JsonObject();
                food.addProperty("id", result.getString("id"));
                food.addProperty("name", result.getString("name"));
                food.addProperty("category", result.getString("category"));
                food.addProperty("price", result.getDouble("price"));
                return food;
            }
        }
    }

    private static void saveFood(String id, JsonObject food, boolean update) throws SQLException {
        String name = requiredText(food, "name");
        String category = requiredText(food, "category");
        double price = requiredPositiveNumber(food, "price");
        try (Connection connection = openDatabase()) {
            Integer categoryId = null;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT id FROM categories WHERE name = ?")) {
                query.setString(1, category);
                try (ResultSet result = query.executeQuery()) {
                    if (result.next()) {
                        categoryId = result.getInt("id");
                    }
                }
            }
            if (categoryId == null) {
                throw new ApiException(400, "Choose a valid food category.");
            }

            if (update) {
                try (PreparedStatement save = connection.prepareStatement(
                        "UPDATE foods SET name = ?, category_id = ?, price = ? WHERE id = ?")) {
                    save.setString(1, name);
                    save.setInt(2, categoryId);
                    save.setDouble(3, price);
                    save.setString(4, id);
                    if (save.executeUpdate() == 0) {
                        throw new ApiException(404, "Food item not found.");
                    }
                }
            } else {
                try (PreparedStatement save = connection.prepareStatement(
                        "INSERT INTO foods(id, name, category_id, price) VALUES (?, ?, ?, ?)")) {
                    save.setString(1, id);
                    save.setString(2, name);
                    save.setInt(3, categoryId);
                    save.setDouble(4, price);
                    save.executeUpdate();
                }
            }
        }
    }

    private static void deleteFood(String id) throws SQLException {
        try (Connection connection = openDatabase();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM foods WHERE id = ?")) {
            delete.setString(1, id);
            if (delete.executeUpdate() == 0) {
                throw new ApiException(404, "Food item not found.");
            }
        }
    }

    private static JsonArray getOrders() throws SQLException {
        JsonArray orders = new JsonArray();
        try (Connection connection = openDatabase();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT orders.id, guests.name AS guest_name, guests.contact, "
                             + "restaurant_tables.id AS table_number, orders.status, orders.created_at "
                             + "FROM orders JOIN guests ON orders.guest_id = guests.id "
                             + "JOIN restaurant_tables ON orders.table_id = restaurant_tables.id "
                             + "ORDER BY orders.created_at DESC");
             ResultSet result = query.executeQuery()) {
            while (result.next()) {
                orders.add(readOrder(connection, result));
            }
        }
        return orders;
    }

    private static JsonObject getOrder(String id) throws SQLException {
        try (Connection connection = openDatabase();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT orders.id, guests.name AS guest_name, guests.contact, "
                             + "restaurant_tables.id AS table_number, orders.status, orders.created_at "
                             + "FROM orders JOIN guests ON orders.guest_id = guests.id "
                             + "JOIN restaurant_tables ON orders.table_id = restaurant_tables.id "
                             + "WHERE orders.id = ?")) {
            query.setString(1, id);
            try (ResultSet result = query.executeQuery()) {
                if (!result.next()) {
                    throw new ApiException(404, "Order not found.");
                }
                return readOrder(connection, result);
            }
        }
    }

    private static JsonObject readOrder(Connection connection, ResultSet result) throws SQLException {
        String id = result.getString("id");
        JsonObject order = new JsonObject();
        order.addProperty("id", id);
        order.addProperty("guestName", result.getString("guest_name"));
        order.addProperty("guestContact", result.getString("contact") == null ? "" : result.getString("contact"));
        order.addProperty("table", result.getString("table_number"));
        order.addProperty("status", result.getString("status"));
        order.addProperty("createdAt", result.getLong("created_at"));

        JsonArray items = new JsonArray();
        double total = 0;
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT food_id, food_name, unit_price, quantity FROM order_items WHERE order_id = ?")) {
            query.setString(1, id);
            try (ResultSet itemResult = query.executeQuery()) {
                while (itemResult.next()) {
                    JsonObject item = new JsonObject();
                    String foodId = itemResult.getString("food_id");
                    if (foodId == null) {
                        item.add("foodId", null);
                    } else {
                        item.addProperty("foodId", foodId);
                    }
                    item.addProperty("name", itemResult.getString("food_name"));
                    item.addProperty("price", itemResult.getDouble("unit_price"));
                    item.addProperty("quantity", itemResult.getInt("quantity"));
                    total += itemResult.getDouble("unit_price") * itemResult.getInt("quantity");
                    items.add(item);
                }
            }
        }
        order.add("items", items);
        order.addProperty("total", total);
        return order;
    }

    private static void saveOrder(String id, JsonObject order, boolean update) throws SQLException {
        String guestName = requiredText(order, "guestName");
        String guestContact = optionalText(order, "guestContact");
        int tableNumber = requiredInteger(order, "table");
        if (tableNumber < 1 || tableNumber > 8) {
            throw new ApiException(400, "Choose a table from 1 to 8.");
        }
        JsonElement itemElement = order.get("items");
        if (itemElement == null || !itemElement.isJsonArray() || itemElement.getAsJsonArray().size() == 0) {
            throw new ApiException(400, "Add at least one food item to the order.");
        }

        try (Connection connection = openDatabase()) {
            connection.setAutoCommit(false);
            try {
                int guestId;
                if (update) {
                    try (PreparedStatement findGuest = connection.prepareStatement(
                            "SELECT guest_id FROM orders WHERE id = ?")) {
                        findGuest.setString(1, id);
                        try (ResultSet result = findGuest.executeQuery()) {
                            if (!result.next()) {
                                throw new ApiException(404, "Order not found.");
                            }
                            guestId = result.getInt("guest_id");
                        }
                    }
                    try (PreparedStatement updateGuest = connection.prepareStatement(
                            "UPDATE guests SET name = ?, contact = ? WHERE id = ?")) {
                        updateGuest.setString(1, guestName);
                        updateGuest.setString(2, guestContact);
                        updateGuest.setInt(3, guestId);
                        updateGuest.executeUpdate();
                    }
                    try (PreparedStatement updateOrder = connection.prepareStatement(
                            "UPDATE orders SET table_id = ? WHERE id = ?")) {
                        updateOrder.setInt(1, tableNumber);
                        updateOrder.setString(2, id);
                        updateOrder.executeUpdate();
                    }
                    try (PreparedStatement deleteItems = connection.prepareStatement(
                            "DELETE FROM order_items WHERE order_id = ?")) {
                        deleteItems.setString(1, id);
                        deleteItems.executeUpdate();
                    }
                } else {
                    try (PreparedStatement addGuest = connection.prepareStatement(
                            "INSERT INTO guests(name, contact) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                        addGuest.setString(1, guestName);
                        addGuest.setString(2, guestContact);
                        addGuest.executeUpdate();
                        try (ResultSet result = addGuest.getGeneratedKeys()) {
                            if (!result.next()) {
                                throw new SQLException("Could not create guest record.");
                            }
                            guestId = result.getInt(1);
                        }
                    }
                    try (PreparedStatement addOrder = connection.prepareStatement(
                            "INSERT INTO orders(id, guest_id, table_id, status, created_at) "
                                    + "VALUES (?, ?, ?, 'open', ?)")) {
                        addOrder.setString(1, id);
                        addOrder.setInt(2, guestId);
                        addOrder.setInt(3, tableNumber);
                        addOrder.setLong(4, System.currentTimeMillis());
                        addOrder.executeUpdate();
                    }
                }

                for (JsonElement element : itemElement.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        throw new ApiException(400, "Order items must be valid food items.");
                    }
                    JsonObject item = element.getAsJsonObject();
                    String foodId = requiredText(item, "foodId");
                    int quantity = requiredInteger(item, "quantity");
                    if (quantity < 1 || quantity > 99) {
                        throw new ApiException(400, "Food quantity must be between 1 and 99.");
                    }

                    try (PreparedStatement addItem = connection.prepareStatement(
                            "INSERT INTO order_items(order_id, food_id, food_name, unit_price, quantity) "
                                    + "SELECT ?, foods.id, foods.name, foods.price, ? "
                                    + "FROM foods WHERE foods.id = ?")) {
                        addItem.setString(1, id);
                        addItem.setInt(2, quantity);
                        addItem.setString(3, foodId);
                        if (addItem.executeUpdate() == 0) {
                            throw new ApiException(400, "A selected food item no longer exists.");
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private static void closeOrder(String id) throws SQLException {
        try (Connection connection = openDatabase();
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE orders SET status = 'closed' WHERE id = ?")) {
            update.setString(1, id);
            if (update.executeUpdate() == 0) {
                throw new ApiException(404, "Order not found.");
            }
        }
    }

    private static void deleteOrder(String id) throws SQLException {
        try (Connection connection = openDatabase();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM orders WHERE id = ?")) {
            delete.setString(1, id);
            if (delete.executeUpdate() == 0) {
                throw new ApiException(404, "Order not found.");
            }
        }
    }

    private static JsonObject readJson(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = exchange.getRequestBody().read(buffer)) != -1) {
            body.write(buffer, 0, count);
        }
        try {
            JsonElement value = JsonParser.parseString(
                    new String(body.toByteArray(), StandardCharsets.UTF_8));
            if (!value.isJsonObject()) {
                throw new ApiException(400, "A JSON object is required.");
            }
            return value.getAsJsonObject();
        } catch (com.google.gson.JsonParseException error) {
            throw new ApiException(400, "The request contains invalid JSON.");
        }
    }

    private static String requiredText(JsonObject object, String property) {
        JsonElement value = object.get(property);
        if (value == null || value.isJsonNull()) {
            throw new ApiException(400, "Missing required value: " + property + ".");
        }
        String text = value.getAsString().trim();
        if (text.isEmpty()) {
            throw new ApiException(400, "Missing required value: " + property + ".");
        }
        return text;
    }

    private static String optionalText(JsonObject object, String property) {
        JsonElement value = object.get(property);
        return value == null || value.isJsonNull() ? "" : value.getAsString().trim();
    }

    private static double requiredPositiveNumber(JsonObject object, String property) {
        try {
            double number = object.get(property).getAsDouble();
            if (Double.isFinite(number) && number > 0 && number <= 9999) {
                return number;
            }
        } catch (RuntimeException ignored) {
            // Return a clear validation response below.
        }
        throw new ApiException(400, "Enter a valid " + property + " greater than zero.");
    }

    private static int requiredInteger(JsonObject object, String property) {
        try {
            return Integer.parseInt(object.get(property).getAsString());
        } catch (RuntimeException ignored) {
            throw new ApiException(400, "Enter a valid " + property + ".");
        }
    }

    private static JsonObject errorJson(String message) {
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        return error;
    }

    private static void sendJson(HttpExchange exchange, int status, JsonElement value) throws IOException {
        byte[] content = GSON.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(content);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String message) throws IOException {
        byte[] content = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(status, content.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(content);
        }
    }

    private static class ApiException extends RuntimeException {
        private final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
