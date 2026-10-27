ALTER TABLE orders ADD COLUMN idempotency_key UUID;

CREATE UNIQUE INDEX uk_orders_user_id_idempotency_key ON orders (user_id, idempotency_key);
