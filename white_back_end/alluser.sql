CREATE DATABASE white_web;
USE white_web;

CREATE TABLE user (
    username VARCHAR(20) NOT NULL,
    password VARCHAR(200) NOT NULL,
    phonenumber VARCHAR(11) NOT NULL,
    usertype INTEGER NOT NULL,
    PRIMARY KEY (username),
    UNIQUE (username)
);

CREATE TABLE orders (
    order_id INTEGER NOT NULL AUTO_INCREMENT,
    user1 VARCHAR(20),
    user2 VARCHAR(20),
    user3 VARCHAR(20),
    user4 VARCHAR(20),
    departure VARCHAR(100) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    date DATE NOT NULL,
    earliest_departure_time TIME NOT NULL,
    latest_departure_time TIME NOT NULL,
    driver VARCHAR(20),
    remark VARCHAR(100),
    PRIMARY KEY (order_id)
);

CREATE TABLE order_status (
    order_id INTEGER NOT NULL,
    status INTEGER DEFAULT 0, -- 0: 未开始, 1: 进行中, 2: 已完成
    user1_arrived BOOLEAN DEFAULT FALSE,
    user2_arrived BOOLEAN DEFAULT FALSE,
    user3_arrived BOOLEAN DEFAULT FALSE,
    user4_arrived BOOLEAN DEFAULT FALSE,
    driver_arrived BOOLEAN DEFAULT FALSE,
    completed_at DATETIME NULL,
    PRIMARY KEY (order_id),
    FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

CREATE TABLE conversation (
    conversation_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL UNIQUE,
    status TINYINT NOT NULL DEFAULT 0,
    next_seq INT NOT NULL DEFAULT 1,
    last_seq INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    close_at DATETIME NULL,
    CONSTRAINT fk_conversation_order
        FOREIGN KEY (order_id)
        REFERENCES orders(order_id)
        ON DELETE CASCADE
);

CREATE TABLE conversation_member (
    conversation_id INT NOT NULL,
    username VARCHAR(20) NOT NULL,
    role TINYINT NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_read_seq INT NOT NULL DEFAULT 0,
    clear_before_seq INT NOT NULL DEFAULT 0,
    hidden_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, username),
    CONSTRAINT fk_cm_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES conversation(conversation_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cm_user
        FOREIGN KEY (username)
        REFERENCES user(username)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

CREATE TABLE message (
    message_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id INT NOT NULL,
    seq INT NOT NULL,
    sender_username VARCHAR(20) NULL,
    message_type TINYINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    client_msg_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_msg_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES conversation(conversation_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_msg_sender
        FOREIGN KEY (sender_username)
        REFERENCES user(username)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    CONSTRAINT uk_msg_conversation_seq
        UNIQUE (conversation_id, seq),
    CONSTRAINT uk_msg_client_msg
        UNIQUE (conversation_id, sender_username, client_msg_id)
);

CREATE INDEX idx_cm_username_hidden
    ON conversation_member(username, hidden_at);

CREATE INDEX idx_conversation_status_close
    ON conversation(status, close_at);

CREATE TABLE driver_rating (
    rating_id INTEGER NOT NULL AUTO_INCREMENT,
    order_id INTEGER,
    driver_username VARCHAR(20),
    user_username VARCHAR(20),
    rating FLOAT NOT NULL, -- 1-5分
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rating_id),
    FOREIGN KEY (order_id) REFERENCES orders (order_id),
    FOREIGN KEY (driver_username) REFERENCES user (username),
    FOREIGN KEY (user_username) REFERENCES user (username)
);

CREATE TABLE driver_average_rating (
    driver_username VARCHAR(20) NOT NULL,
    average_rating FLOAT DEFAULT 5.0,
    rating_count INTEGER DEFAULT 0,
    PRIMARY KEY (driver_username),
    FOREIGN KEY (driver_username) REFERENCES user (username)
);

SELECT * FROM user;

SELECT * FROM orders;

DROP TABLE user;
DROP TABLE orders;
