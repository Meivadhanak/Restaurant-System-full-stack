let foods = [];
let orders = [];

const money = (amount) => `€${Number(amount).toFixed(2)}`;
const escapeHtml = (value) => String(value).replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
}[character]));

async function apiRequest(path, method = "GET", data = null) {
    const options = { method, headers: {} };
    if (data !== null) {
        options.headers["Content-Type"] = "application/json";
        options.body = JSON.stringify(data);
    }

    const response = await fetch(path, options);
    const result = await response.json();
    if (!response.ok) {
        throw new Error(result.error || "The server could not complete the request.");
    }
    return result;
}

async function reloadData() {
    [foods, orders] = await Promise.all([
        apiRequest("/api/foods"),
        apiRequest("/api/orders")
    ]);
}

function render() {
    renderFoods();
    renderOrders();
    renderSummary();
}

function renderFoods() {
    const list = document.querySelector("#menu-list");
    if (foods.length === 0) {
        list.innerHTML = '<div class="empty-state">No food items yet. Add your first menu item.</div>';
        return;
    }

    list.innerHTML = foods.map((food) => `
        <article class="food-card">
            <div class="food-top">
                <div>
                    <h3>${escapeHtml(food.name)}</h3>
                    <span class="food-category">${escapeHtml(food.category)}</span>
                </div>
                <span class="food-price">${money(food.price)}</span>
            </div>
            <div class="order-actions">
                <button class="text-button" data-edit-food="${escapeHtml(food.id)}">Edit</button>
                <button class="text-button danger" data-delete-food="${escapeHtml(food.id)}">Delete</button>
            </div>
        </article>`).join("");
}

function renderOrders() {
    const list = document.querySelector("#orders-list");
    if (orders.length === 0) {
        list.innerHTML = '<div class="empty-state">No orders yet. Create an order to get started.</div>';
        return;
    }

    const sortedOrders = [...orders].sort((a, b) => b.createdAt - a.createdAt);
    list.innerHTML = sortedOrders.map((order) => {
        const itemNames = order.items.map((item) =>
            `${item.quantity} × ${escapeHtml(item.name)}`
        ).join(", ");
        const status = order.status === "closed" ? "closed" : "open";
        const guestContact = order.guestContact ? ` · ${escapeHtml(order.guestContact)}` : "";
        return `
            <article class="order-card">
                <div class="order-top">
                    <div>
                        <div class="order-title">${escapeHtml(order.guestName)} · Table ${escapeHtml(order.table)}</div>
                        <div class="order-meta">${new Date(order.createdAt).toLocaleString()}${guestContact}</div>
                    </div>
                    <span class="status ${status}">${status === "closed" ? "Closed" : "Open"}</span>
                </div>
                <div class="order-food">${itemNames}</div>
                <div class="order-actions">
                    <button class="text-button" data-edit-order="${escapeHtml(order.id)}">Edit</button>
                    ${status === "open" ? `<button class="text-button" data-close-order="${escapeHtml(order.id)}">Close order</button>` : ""}
                    <button class="text-button danger" data-delete-order="${escapeHtml(order.id)}">Delete</button>
                    <span class="order-total">${money(order.total)}</span>
                </div>
            </article>`;
    }).join("");
}

function renderSummary() {
    const openOrders = orders.filter((order) => order.status === "open");
    const usedTables = new Set(openOrders.map((order) => order.table));
    const today = new Date().toDateString();
    const sales = orders
        .filter((order) => order.status === "closed" && new Date(order.createdAt).toDateString() === today)
        .reduce((sum, order) => sum + Number(order.total), 0);

    document.querySelector("#food-count").textContent = foods.length;
    document.querySelector("#open-order-count").textContent = openOrders.length;
    document.querySelector("#tables-in-use").textContent = usedTables.size;
    document.querySelector("#sales-total").textContent = money(sales);
}

function showFoodForm(food) {
    const form = document.querySelector("#food-form");
    form.reset();
    document.querySelector("#food-id").value = food ? food.id : "";
    document.querySelector("#food-name").value = food ? food.name : "";
    document.querySelector("#food-category").value = food ? food.category : "Main course";
    document.querySelector("#food-price").value = food ? food.price : "";
    document.querySelector("#food-form-title").textContent = food ? "Edit food item" : "Add food item";
    form.classList.remove("hidden");
    document.querySelector("#food-name").focus();
}

function addOrderItem(item) {
    const row = document.createElement("div");
    row.className = "order-item-row";

    const foodSelect = document.createElement("select");
    foodSelect.className = "order-food-select";
    foodSelect.required = true;
    foodSelect.setAttribute("aria-label", "Food item");
    foodSelect.innerHTML = '<option value="">Choose food</option>' + foods.map((food) =>
        `<option value="${escapeHtml(food.id)}">${escapeHtml(food.name)} — ${money(food.price)}</option>`
    ).join("");

    const quantity = document.createElement("input");
    quantity.className = "order-quantity";
    quantity.type = "number";
    quantity.min = "1";
    quantity.max = "99";
    quantity.step = "1";
    quantity.value = item ? item.quantity : "1";
    quantity.required = true;
    quantity.setAttribute("aria-label", "Quantity");

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "remove-item";
    removeButton.textContent = "×";
    removeButton.setAttribute("aria-label", "Remove food item");
    removeButton.addEventListener("click", () => {
        row.remove();
        updateOrderTotal();
    });

    foodSelect.addEventListener("change", updateOrderTotal);
    quantity.addEventListener("input", updateOrderTotal);
    row.append(foodSelect, quantity, removeButton);
    document.querySelector("#order-items").appendChild(row);

    if (item) foodSelect.value = item.foodId;
}

function updateOrderTotal() {
    let total = 0;
    document.querySelectorAll(".order-item-row").forEach((row) => {
        const food = foods.find((item) => item.id === row.querySelector(".order-food-select").value);
        const quantity = Number(row.querySelector(".order-quantity").value) || 0;
        if (food) total += Number(food.price) * quantity;
    });
    document.querySelector("#order-total").textContent = money(total);
}

function showOrderForm(order) {
    if (foods.length === 0) {
        alert("Add at least one food item to the menu before creating an order.");
        return;
    }

    const form = document.querySelector("#order-form");
    form.reset();
    document.querySelector("#order-id").value = order ? order.id : "";
    document.querySelector("#guest-name").value = order ? order.guestName : "";
    document.querySelector("#guest-contact").value = order ? order.guestContact || "" : "";
    document.querySelector("#table-number").value = order ? order.table : "1";
    document.querySelector("#order-form-title").textContent = order ? "Edit order" : "New order";
    document.querySelector("#order-items").innerHTML = "";

    if (order) {
        order.items.forEach(addOrderItem);
    } else {
        addOrderItem();
    }

    updateOrderTotal();
    form.classList.remove("hidden");
    form.scrollIntoView({ behavior: "smooth", block: "start" });
}

document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => {
        document.querySelectorAll(".tab").forEach((button) => button.classList.remove("active"));
        tab.classList.add("active");
        document.querySelectorAll(".panel").forEach((panel) => panel.classList.add("hidden"));
        document.querySelector(`#${tab.dataset.panel}`).classList.remove("hidden");
    });
});

document.querySelector("#new-food-button").addEventListener("click", () => showFoodForm());
document.querySelector("#new-order-button").addEventListener("click", () => showOrderForm());
document.querySelector("#add-order-item").addEventListener("click", () => addOrderItem());

document.querySelectorAll("[data-cancel]").forEach((button) => {
    button.addEventListener("click", () => {
        document.querySelector(`#${button.dataset.cancel}-form`).classList.add("hidden");
    });
});

document.querySelector("#food-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const id = document.querySelector("#food-id").value;
    const food = {
        name: document.querySelector("#food-name").value.trim(),
        category: document.querySelector("#food-category").value,
        price: Number(document.querySelector("#food-price").value)
    };

    if (!food.name || !Number.isFinite(food.price) || food.price <= 0) {
        alert("Enter a food name and a price greater than zero.");
        return;
    }

    try {
        await apiRequest(id ? `/api/foods/${encodeURIComponent(id)}` : "/api/foods",
            id ? "PUT" : "POST", food);
        await reloadData();
        render();
        form.classList.add("hidden");
    } catch (error) {
        console.error("Could not save food item.", error);
        alert(error.message);
    }
});

document.querySelector("#order-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const rows = [...document.querySelectorAll(".order-item-row")];
    if (rows.length === 0) {
        alert("Add at least one food item to the order.");
        return;
    }

    const items = [];
    for (const row of rows) {
        const food = foods.find((item) => item.id === row.querySelector(".order-food-select").value);
        const quantity = Number(row.querySelector(".order-quantity").value);
        if (!food || !Number.isInteger(quantity) || quantity < 1 || quantity > 99) {
            alert("Choose a food item and enter a quantity from 1 to 99 for every row.");
            return;
        }
        items.push({ foodId: food.id, quantity });
    }

    const id = document.querySelector("#order-id").value;
    const order = {
        guestName: document.querySelector("#guest-name").value.trim(),
        guestContact: document.querySelector("#guest-contact").value.trim(),
        table: document.querySelector("#table-number").value,
        items
    };

    if (!order.guestName) {
        alert("Enter the guest's name.");
        return;
    }

    try {
        await apiRequest(id ? `/api/orders/${encodeURIComponent(id)}` : "/api/orders",
            id ? "PUT" : "POST", order);
        await reloadData();
        render();
        form.classList.add("hidden");
    } catch (error) {
        console.error("Could not save order.", error);
        alert(error.message);
    }
});

document.querySelector("#orders-list").addEventListener("click", async (event) => {
    const button = event.target.closest("button");
    if (!button) return;

    if (button.dataset.editOrder) {
        const order = orders.find((item) => item.id === button.dataset.editOrder);
        if (order) showOrderForm(order);
    }
    try {
        if (button.dataset.closeOrder) {
            await apiRequest(`/api/orders/${encodeURIComponent(button.dataset.closeOrder)}/close`, "PATCH");
            await reloadData();
            render();
        }
        if (button.dataset.deleteOrder && confirm("Delete this order?")) {
            await apiRequest(`/api/orders/${encodeURIComponent(button.dataset.deleteOrder)}`, "DELETE");
            await reloadData();
            render();
        }
    } catch (error) {
        console.error("Could not update order.", error);
        alert(error.message);
    }
});

document.querySelector("#menu-list").addEventListener("click", async (event) => {
    const button = event.target.closest("button");
    if (!button) return;

    if (button.dataset.editFood) {
        const food = foods.find((item) => item.id === button.dataset.editFood);
        if (food) showFoodForm(food);
    }
    if (button.dataset.deleteFood && confirm("Delete this food item?")) {
        try {
            await apiRequest(`/api/foods/${encodeURIComponent(button.dataset.deleteFood)}`, "DELETE");
            await reloadData();
            render();
        } catch (error) {
            console.error("Could not delete food item.", error);
            alert(error.message);
        }
    }
});

document.querySelector("#today-date").textContent = new Date().toLocaleDateString(undefined, {
    weekday: "long", year: "numeric", month: "long", day: "numeric"
});

reloadData()
    .then(render)
    .catch((error) => {
        console.error("Could not load restaurant data.", error);
        document.querySelector("#orders-list").innerHTML =
            '<div class="empty-state">Could not connect to the database. Restart the Java server and reload this page.</div>';
        document.querySelector("#menu-list").innerHTML =
            '<div class="empty-state">Menu could not be loaded from the database.</div>';
    });
