-- Records which pricing package a payment buys (nullable for rows created before this column).
ALTER TABLE payments ADD COLUMN package_code VARCHAR(20) NULL AFTER user_id;
