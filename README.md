# Simple Restaurant Ordering System

A small restaurant website made with Java, HTML, CSS, and plain JavaScript. It is designed to be easy to run and explain for a beginner student project. MySQL stores its data in a database that you can manage with phpMyAdmin.

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
- XAMPP with MySQL/MariaDB
- A modern web browser

## Run the application

1. Install and start XAMPP, then start **MySQL** in the XAMPP Control Panel.
2. Open phpMyAdmin at [http://localhost/phpmyadmin](http://localhost/phpmyadmin). The XAMPP database server is configured on port `3307` because another MySQL server already uses `3306`. Create a database named `restaurant_system`, or use the **Import** tab to import the project's `schema.sql` file (which creates that database and its tables).
3. If your XAMPP MySQL user is not `root` with a blank password, set the connection variables below before starting the application.
4. Open a terminal in the folder containing `pom.xml`.
5. Start the server (Maven downloads the Java libraries the first time):

   ```text
   mvn compile exec:java
   ```

6. Open [http://localhost:8080](http://localhost:8080) in a browser.
7. To stop the server, press `Ctrl+C` in the terminal.

The app defaults to `jdbc:mysql://localhost:3307/restaurant_system` with user `root` and a blank password, as in this XAMPP setup. For another setup, configure these environment variables:

- `RESTAURANT_DB_URL`: JDBC URL (default: `jdbc:mysql://localhost:3307/restaurant_system?serverTimezone=UTC`)
- `RESTAURANT_DB_USER`: database user (default: `root`)
- `RESTAURANT_DB_PASSWORD`: database password (default: blank)

In PowerShell, set the user and password for the current terminal before starting the app:

```powershell
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
