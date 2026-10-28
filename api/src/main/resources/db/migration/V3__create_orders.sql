CREATE TABLE orders (
                        id         UUID          PRIMARY KEY,
                        user_id    UUID          NOT NULL REFERENCES users (id),
                        status     VARCHAR(30)   NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'CANCELLED')),
                        total      NUMERIC(12,2) NOT NULL CHECK (total > 0),
                        created_at TIMESTAMPTZ   NOT NULL,
                        updated_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_orders_user_id_created_at ON orders (user_id, created_at DESC);

CREATE TABLE order_items (
                             id           UUID          PRIMARY KEY,
                             order_id     UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
                             product_id   UUID          NOT NULL REFERENCES products (id),
                             product_name VARCHAR(150)  NOT NULL,
                             unit_price   NUMERIC(10,2) NOT NULL CHECK (unit_price > 0),
                             quantity     INTEGER       NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
