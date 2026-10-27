CREATE TABLE categories (
                            id   UUID         PRIMARY KEY,
                            name VARCHAR(100) NOT NULL,
                            slug VARCHAR(120) NOT NULL
);

CREATE UNIQUE INDEX uk_categories_slug ON categories (slug);

CREATE TABLE products (
                          id          UUID          PRIMARY KEY,
                          name        VARCHAR(150)  NOT NULL,
                          slug        VARCHAR(170)  NOT NULL,
                          description TEXT          NOT NULL,
                          price       NUMERIC(10,2) NOT NULL CHECK (price > 0),
                          stock       INTEGER       NOT NULL CHECK (stock >= 0),
                          active      BOOLEAN       NOT NULL DEFAULT TRUE,
                          category_id UUID          NOT NULL REFERENCES categories (id),
                          created_at  TIMESTAMPTZ   NOT NULL,
                          updated_at  TIMESTAMPTZ   NOT NULL
);

CREATE UNIQUE INDEX uk_products_slug ON products (slug);
CREATE INDEX idx_products_category_id ON products (category_id);
