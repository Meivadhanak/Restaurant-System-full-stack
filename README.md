# Simple Restaurant Ordering System

A small restaurant website made with Java, HTML, CSS, and plain JavaScript. It is designed to be easy to run and explain for a beginner student project. It uses a local SQLite database by default so it can run immediately on a developer machine, while MySQL remains available as an optional environment override.

## What it can do

- Add, list, edit, and delete food menu items.
- Create, view, edit, close, and delete orders.
- Add a guest name and optional contact information to an order.
- Choose a table and add food items with quantities.
- See a simple dashboard with open orders, tables in use, and today's sales.
- Save food, guest, table, order, and order item data in MySQL.

The app creates the required tables automatically when it first connects to MySQL. It adds four sample foods and eight restaurant tables; orders start empty. You can also create the database and tables by importing `schema.sql` through phpMyAdmin.

## Requirements

- Java 25 or newer
- Maven 3.6.3 or newer
- A modern web browser
- Optional: XAMPP with MySQL/MariaDB if you want to switch the app to MySQL

## Run the application

1. Open a terminal in the folder containing `pom.xml`.
2. Start the server (Maven downloads the Java libraries the first time):

   ```text
   mvn compile exec:java
   ```

3. Open [http://localhost:8080](http://localhost:8080) in a browser.
4. To stop a manually started server, press `Ctrl+C` in the terminal. If the Windows auto-start monitor is installed, it will restart the server.

The server's `GET /api/health` endpoint returns `{"status":"ok"}` when the HTTP service is running.

### Start automatically on Windows

To keep the server running after closing VS Code and start it again at Windows sign-in, run this once from PowerShell in the project folder:

```powershell
.\setup-autostart.ps1
```

This registers a background task for the current Windows user. It keeps the server running after VS Code closes and restarts it if it stops. After a Windows restart, it starts when you sign in. Logs are written to `%LOCALAPPDATA%\RestaurantSystem\server.log`. To remove the automatic startup task, run:

```powershell
Unregister-ScheduledTask -TaskName "RestaurantSystem-LocalServer"
```

By default, the app uses a local SQLite database file at `restaurant.db` in the project folder. This runs without extra setup and is the easiest option for local development.

If you want to switch to MySQL instead, set these environment variables before starting the app:

- `RESTAURANT_DB_URL`: JDBC URL (example: `jdbc:mysql://localhost:3307/restaurant_system?serverTimezone=UTC`)
- `RESTAURANT_DB_USER`: database user (default: `root`)
- `RESTAURANT_DB_PASSWORD`: database password (default: blank)

In PowerShell, for example:

```powershell
$env:RESTAURANT_DB_URL = "jdbc:mysql://localhost:3307/restaurant_system?serverTimezone=UTC"
$env:RESTAURANT_DB_USER = "root"
$env:RESTAURANT_DB_PASSWORD = "your MySQL password"
mvn compile exec:java
```

Use the username and password configured for your MySQL server; do not put real passwords in source code.

## How it works

- `Main.java` starts a small Java web server, handles the website's CRUD requests, and reads and writes the MySQL database.
- `schema.sql` creates the MySQL database and tables for importing through phpMyAdmin; the app can also create its tables automatically.
- `pom.xml` lists the MySQL Connector/J and JSON libraries.
- `index.html` contains the page and forms.
- `styles.css` controls the layout and colors.
- `app.js` sends food and order requests to the Java server.

The database has `categories`, `foods`, `restaurant_tables`, `guests`, `orders`, and `order_items` tables. This is a simple school-project version, not a production system. The previous SQLite database file is not changed or automatically imported; back it up and migrate its data separately if you need to keep existing records.
