CREATE TABLE IF NOT EXISTS sample_sales (
    id BIGINT PRIMARY KEY,
    region VARCHAR(40) NOT NULL,
    product VARCHAR(80) NOT NULL,
    units INTEGER NOT NULL,
    revenue DECIMAL(12, 2) NOT NULL
);
