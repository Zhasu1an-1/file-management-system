INSERT INTO users (username, display_name, role)
VALUES ('alice', 'Alice', 'OWNER'), ('bob', 'Bob', 'VIEWER')
ON CONFLICT (username) DO NOTHING;
