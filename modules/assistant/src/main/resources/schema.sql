CREATE TABLE IF NOT EXISTS chat_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    sequence INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conversation ON chat_memory (conversation_id, sequence);

CREATE TABLE IF NOT EXISTS task (
    id VARCHAR(255) PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    prompt TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    result TEXT,
    channel_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_task_status ON task (status);

CREATE TABLE IF NOT EXISTS recurring_task (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    cron_expression VARCHAR(100) NOT NULL,
    prompt TEXT NOT NULL,
    model_id VARCHAR(255)
);

ALTER TABLE recurring_task ADD COLUMN IF NOT EXISTS model_id VARCHAR(255);
ALTER TABLE recurring_task ADD COLUMN IF NOT EXISTS last_status VARCHAR(50);
ALTER TABLE recurring_task ADD COLUMN IF NOT EXISTS last_execution_at TIMESTAMP;
ALTER TABLE recurring_task ADD COLUMN IF NOT EXISTS execution_results TEXT DEFAULT '[]';
