CREATE TABLE customers (
                           id BIGSERIAL PRIMARY KEY,
                           name VARCHAR(255) NOT NULL,
                           company VARCHAR(255),
                           email VARCHAR(255),
                           phone VARCHAR(50),
                           address TEXT
);

CREATE TABLE tickets (
                         id BIGSERIAL PRIMARY KEY,
                         ticket_key VARCHAR(50) UNIQUE NOT NULL,
                         issue_type VARCHAR(50) NOT NULL,
                         summary VARCHAR(255) NOT NULL,
                         description TEXT,
                         assignee VARCHAR(100),
                         reporter VARCHAR(100),
                         priority VARCHAR(50),
                         status VARCHAR(50) DEFAULT 'Open',
                         customer_id BIGINT REFERENCES customers(id),
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE comments (
                          id BIGSERIAL PRIMARY KEY,
                          ticket_id BIGINT REFERENCES tickets(id) ON DELETE CASCADE,
                          author VARCHAR(100) NOT NULL,
                          avatar VARCHAR(10),
                          content TEXT NOT NULL,
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tempo_bookings (
                                id BIGSERIAL PRIMARY KEY,
                                ticket_id BIGINT REFERENCES tickets(id) ON DELETE CASCADE,
                                user_name VARCHAR(100) NOT NULL,
                                duration VARCHAR(50) NOT NULL,
                                description TEXT,
                                booking_date DATE NOT NULL
);