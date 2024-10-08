-- Create users table
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL
);

-- Create sessions table
CREATE TABLE sessions (
    session_id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
    token VARCHAR(36) UNIQUE NOT NULL,
    expiry_time TIMESTAMP NOT NULL
);

-- Create quizzes table
CREATE TABLE quizzes (
    quiz_id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL
);

-- Create questions table
CREATE TABLE questions (
    question_id SERIAL PRIMARY KEY,
    quiz_id INTEGER REFERENCES quizzes(quiz_id) ON DELETE CASCADE,
    question_text TEXT NOT NULL,
    question_type VARCHAR(50) NOT NULL,
    options TEXT
);

-- Create responses table
CREATE TABLE responses (
    response_id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(user_id) ON DELETE CASCADE,
    quiz_id INTEGER REFERENCES quizzes(quiz_id) ON DELETE CASCADE,
    answers TEXT,
    UNIQUE (user_id, quiz_id)
);
