import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

// Main Class
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginGUI loginGUI = new LoginGUI();
            loginGUI.setVisible(true);
        });
    }
}

// Person Class
abstract class Person {
    protected String name;
    protected String username;
    protected String password;
    protected String role; // "teacher" or "student"

    public Person(String name, String username, String password, String role) {
        this.name = name;
        this.username = username;
        this.password = password;
        this.role = role;
    }
}

// User Class
class User extends Person {
    protected int userId;

    public User(int userId, String name, String username, String password, String role) {
        super(name, username, password, role);
        this.userId = userId;
    }

    // Getters and Setters (if needed)
}

// User-defined Exceptions
class UserAlreadyExistsException extends Exception {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}

class UserNotFoundException extends Exception {
    public UserNotFoundException(String message) {
        super(message);
    }
}

class InvalidPasswordException extends Exception {
    public InvalidPasswordException(String message) {
        super(message);
    }
}

class SessionExpiredException extends Exception {
    public SessionExpiredException(String message) {
        super(message);
    }
}

// DatabaseManager Class
class DatabaseManager implements AutoCloseable {
    private static final String URL = "jdbc:postgresql://localhost:5432/quizapp";
    private static final String USER = "postgres";
    private static final String PASSWORD = "root";

    private Connection connection;

    public DatabaseManager() {
        try {
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Database connected successfully.");
        } catch (SQLException e) {
            showErrorDialog(null, "Database Connection Error", e.getMessage());
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void executeUpdate(String query, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query);
        setStatementParams(statement, params);
        statement.executeUpdate();
    }

    public ResultSet executeQuery(String query, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query);
        setStatementParams(statement, params);
        return statement.executeQuery();
    }

    private void setStatementParams(PreparedStatement statement, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
    }

    public static void showErrorDialog(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void close() throws Exception {
        if (connection != null)
            connection.close();
    }

    // User registration
    public void registerUser(String name, String username, String password, String role)
            throws SQLException, UserAlreadyExistsException {
        // Check if username already exists
        ResultSet rs = executeQuery("SELECT username FROM users WHERE username = ?", username);
        if (rs.next()) {
            throw new UserAlreadyExistsException("Username already exists");
        }
        // Hash password
        String hashedPassword = hashPassword(password);
        executeUpdate("INSERT INTO users (name, username, password, role) VALUES (?, ?, ?, ?)", name, username,
                hashedPassword, role);
    }

    // User login
    public String loginUser(String username, String password)
            throws SQLException, UserNotFoundException, InvalidPasswordException {
        ResultSet rs = executeQuery("SELECT user_id, password FROM users WHERE username = ?", username);
        if (rs.next()) {
            String hashedPassword = rs.getString("password");
            int userId = rs.getInt("user_id");
            if (verifyPassword(password, hashedPassword)) {
                // Generate token
                String token = generateToken();
                // Set expiry time (e.g., 1 hour from now)
                Timestamp expiryTime = new Timestamp(System.currentTimeMillis() + 3600 * 1000);
                executeUpdate("INSERT INTO sessions (user_id, token, expiry_time) VALUES (?, ?, ?)", userId, token,
                        expiryTime);
                return token;
            } else {
                throw new InvalidPasswordException("Invalid password");
            }
        } else {
            throw new UserNotFoundException("User not found");
        }
    }

    // Validate session
    public User validateSession(String token) throws SQLException, SessionExpiredException {
        ResultSet rs = executeQuery(
                "SELECT s.user_id, s.expiry_time, u.name, u.username, u.role FROM sessions s JOIN users u ON s.user_id = u.user_id WHERE s.token = ?",
                token);
        if (rs.next()) {
            Timestamp expiryTime = rs.getTimestamp("expiry_time");
            if (expiryTime.after(new Timestamp(System.currentTimeMillis()))) {
                int userId = rs.getInt("user_id");
                String name = rs.getString("name");
                String username = rs.getString("username");
                String role = rs.getString("role");
                return new User(userId, name, username, "", role); // password not needed
            } else {
                throw new SessionExpiredException("Session expired");
            }
        } else {
            return null; // Invalid token
        }
    }

    // Logout user
    public void logoutUser(String token) throws SQLException {
        executeUpdate("DELETE FROM sessions WHERE token = ?", token);
    }

    // Hash password (simple MD5 hash for demonstration)
    private String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] array = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : array) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // Should not happen
            e.printStackTrace();
            return null;
        }
    }

    private boolean verifyPassword(String password, String hashedPassword) {
        return hashPassword(password).equals(hashedPassword);
    }

    // Generate token
    private String generateToken() {
        return java.util.UUID.randomUUID().toString();
    }
}

// LoginGUI Class
class LoginGUI extends JFrame {
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerButton;
    private DatabaseManager databaseManager;

    public LoginGUI() {
        super("Login");
        databaseManager = new DatabaseManager();
        setSize(300, 200);
        setLayout(new GridLayout(3, 2));

        usernameField = new JTextField();
        passwordField = new JPasswordField();

        loginButton = new JButton("Login");
        registerButton = new JButton("Register");

        add(new JLabel("Username:"));
        add(usernameField);

        add(new JLabel("Password:"));
        add(passwordField);

        add(loginButton);
        add(registerButton);

        loginButton.addActionListener(e -> login());
        registerButton.addActionListener(e -> openRegistration());

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        try {
            String token = databaseManager.loginUser(username, password);
            User user = databaseManager.validateSession(token);
            // Close login window
            dispose();
            // Open main application GUI
            new QuizAppGUI(user, token, databaseManager).display();
        } catch (UserNotFoundException | InvalidPasswordException | SQLException | SessionExpiredException ex) {
            DatabaseManager.showErrorDialog(this, "Login Error", ex.getMessage());
        }
    }

    private void openRegistration() {
        new RegistrationGUI(databaseManager).setVisible(true);
    }
}

// RegistrationGUI Class
class RegistrationGUI extends JFrame {
    private JTextField nameField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JComboBox<String> roleBox;
    private JButton registerButton;
    private DatabaseManager databaseManager;

    public RegistrationGUI(DatabaseManager databaseManager) {
        super("Register");
        this.databaseManager = databaseManager;
        setSize(300, 250);
        setLayout(new GridLayout(5, 2));

        nameField = new JTextField();
        usernameField = new JTextField();
        passwordField = new JPasswordField();
        roleBox = new JComboBox<>(new String[] { "teacher", "student" });
        registerButton = new JButton("Register");

        add(new JLabel("Name:"));
        add(nameField);

        add(new JLabel("Username:"));
        add(usernameField);

        add(new JLabel("Password:"));
        add(passwordField);

        add(new JLabel("Role:"));
        add(roleBox);

        add(new JLabel());
        add(registerButton);

        registerButton.addActionListener(e -> register());

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
    }

    private void register() {
        String name = nameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String role = (String) roleBox.getSelectedItem();

        try {
            databaseManager.registerUser(name, username, password, role);
            JOptionPane.showMessageDialog(this, "Registration successful. Please login.");
            dispose();
        } catch (UserAlreadyExistsException ex) {
            DatabaseManager.showErrorDialog(this, "Registration Error", ex.getMessage());
        } catch (SQLException ex) {
            DatabaseManager.showErrorDialog(this, "Database Error", ex.getMessage());
        }
    }
}

// QuizAppGUI Class
class QuizAppGUI implements QuizOperations {
    private JFrame mainFrame;
    private JButton createQuizButton;
    private JButton attendQuizButton;
    private JButton viewResponsesButton;
    private DatabaseManager databaseManager;
    private User user;
    private String token;

    public QuizAppGUI(User user, String token, DatabaseManager databaseManager) {
        this.user = user;
        this.token = token;
        this.databaseManager = databaseManager;

        mainFrame = new JFrame("Quiz Management System");
        mainFrame.setSize(400, 200);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLayout(new GridLayout(3, 1));

        createQuizButton = new JButton("Create Quiz");
        attendQuizButton = new JButton("Attend Quiz");
        viewResponsesButton = new JButton("View Responses");

        mainFrame.add(createQuizButton);
        mainFrame.add(attendQuizButton);
        mainFrame.add(viewResponsesButton);

        // Adjust buttons based on user role
        if ("teacher".equals(user.role)) {
            createQuizButton.setEnabled(true);
            viewResponsesButton.setEnabled(true);
            attendQuizButton.setEnabled(false);
        } else if ("student".equals(user.role)) {
            createQuizButton.setEnabled(false);
            viewResponsesButton.setEnabled(false);
            attendQuizButton.setEnabled(true);
        }

        createQuizButton.addActionListener(e -> createQuiz());
        attendQuizButton.addActionListener(e -> attendQuiz());
        viewResponsesButton.addActionListener(e -> viewResponses());
    }

    public void display() {
        mainFrame.setVisible(true);
    }

    @Override
    public void createQuiz() {
        new QuizCreator(databaseManager, user).display();
    }

    @Override
    public void attendQuiz() {
        new QuizAttender(databaseManager, user).display();
    }

    @Override
    public void viewResponses() {
        new QuizResponseViewer(databaseManager, user).display();
    }
}

// QuizOperations Interface
interface QuizOperations {
    void createQuiz();

    void attendQuiz();

    void viewResponses();
}

// QuizCreator Class
class QuizCreator extends JFrame {
    private JTextField quizTitleField;
    private JPanel questionsPanel;
    private JButton addQuestionButton;
    private JButton saveButton;
    private DatabaseManager databaseManager;
    private List<QuestionCreatorPanel> questionPanels;
    private User user;

    public QuizCreator(DatabaseManager databaseManager, User user) {
        super("Create Quiz");
        this.databaseManager = databaseManager;
        this.user = user;
        setSize(600, 600);
        setLayout(new BorderLayout());

        quizTitleField = new JTextField();
        questionsPanel = new JPanel();
        questionsPanel.setLayout(new BoxLayout(questionsPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(questionsPanel);

        addQuestionButton = new JButton("Add Question");
        saveButton = new JButton("Save Quiz");

        questionPanels = new ArrayList<>();

        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        topPanel.add(new JLabel("Quiz Title:"));
        topPanel.add(quizTitleField);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(addQuestionButton);
        buttonPanel.add(saveButton);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        addQuestionButton.addActionListener(e -> addQuestionPanel());
        saveButton.addActionListener(e -> saveQuiz());

        addQuestionPanel();
    }

    public void display() {
        setVisible(true);
    }

    private void addQuestionPanel() {
        QuestionCreatorPanel questionPanel = new QuestionCreatorPanel(this);
        questionPanels.add(questionPanel);
        questionsPanel.add(questionPanel);
        questionsPanel.revalidate();
        questionsPanel.repaint();
    }

    public void removeQuestionPanel(QuestionCreatorPanel questionPanel) {
        questionPanels.remove(questionPanel);
        questionsPanel.remove(questionPanel);
        questionsPanel.revalidate();
        questionsPanel.repaint();
    }

    private void saveQuiz() {
        String title = quizTitleField.getText().trim();

        if (title.isEmpty() || questionPanels.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter quiz title and at least one question.", "Input Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            databaseManager.executeUpdate("INSERT INTO quizzes (title) VALUES (?)", title);
            ResultSet rs = databaseManager.executeQuery("SELECT currval('quizzes_quiz_id_seq') AS quiz_id");
            rs.next();
            int quizId = rs.getInt("quiz_id");

            for (QuestionCreatorPanel qPanel : questionPanels) {
                String questionText = qPanel.getQuestionText().trim();
                String questionType = qPanel.getQuestionType();
                String options = String.join("~", qPanel.getOptions());

                if (questionText.isEmpty()) {
                    continue; // Skip empty questions
                }

                databaseManager.executeUpdate(
                        "INSERT INTO questions (quiz_id, question_text, question_type, options) VALUES (?, ?, ?, ?)",
                        quizId, questionText, questionType, options);
            }

            JOptionPane.showMessageDialog(this, "Quiz saved successfully.");
            dispose();
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }
}

// QuestionCreatorPanel Class
class QuestionCreatorPanel extends JPanel {
    private JTextField questionField;
    private JComboBox<String> questionTypeBox;
    private JPanel optionsPanel;
    private JButton addOptionButton;
    private JButton removeQuestionButton;
    private List<JTextField> optionFields;
    private QuizCreator parent;

    public QuestionCreatorPanel(QuizCreator parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        questionField = new JTextField();
        questionTypeBox = new JComboBox<>(new String[] { "Short Answer", "True/False", "Multiple Choice" });
        optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        addOptionButton = new JButton("Add Option");
        removeQuestionButton = new JButton("Remove Question");
        optionFields = new ArrayList<>();

        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        topPanel.add(new JLabel("Question:"));
        topPanel.add(questionField);

        JPanel typePanel = new JPanel(new GridLayout(2, 1));
        typePanel.add(new JLabel("Question Type:"));
        typePanel.add(questionTypeBox);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(topPanel, BorderLayout.CENTER);
        headerPanel.add(typePanel, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);
        add(optionsPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(addOptionButton);
        buttonPanel.add(removeQuestionButton);

        add(buttonPanel, BorderLayout.SOUTH);

        questionTypeBox.addActionListener(e -> updateOptionFields());
        addOptionButton.addActionListener(e -> addOptionField());
        removeQuestionButton.addActionListener(e -> parent.removeQuestionPanel(this));

        updateOptionFields();
    }

    private void updateOptionFields() {
        optionsPanel.removeAll();
        optionFields.clear();
        String selectedType = (String) questionTypeBox.getSelectedItem();

        if ("Multiple Choice".equals(selectedType)) {
            addOptionButton.setEnabled(true);
            addOptionField();
        } else if ("True/False".equals(selectedType)) {
            addOptionButton.setEnabled(false);
            JTextField trueOption = new JTextField("True");
            trueOption.setEditable(false);
            optionFields.add(trueOption);
            optionsPanel.add(trueOption);

            JTextField falseOption = new JTextField("False");
            falseOption.setEditable(false);
            optionFields.add(falseOption);
            optionsPanel.add(falseOption);
        } else {
            addOptionButton.setEnabled(false);
        }

        optionsPanel.revalidate();
        optionsPanel.repaint();
    }

    private void addOptionField() {
        JTextField optionField = new JTextField();
        optionFields.add(optionField);
        optionsPanel.add(optionField);
        optionsPanel.revalidate();
        optionsPanel.repaint();
    }

    public String getQuestionText() {
        return questionField.getText();
    }

    public String getQuestionType() {
        return (String) questionTypeBox.getSelectedItem();
    }

    public List<String> getOptions() {
        List<String> options = new ArrayList<>();
        for (JTextField field : optionFields) {
            options.add(field.getText());
        }
        return options;
    }
}

// QuizAttender Class
class QuizAttender extends JFrame {
    private JComboBox<String> quizSelectBox;
    private JPanel questionsPanel;
    private JButton submitButton;
    private DatabaseManager databaseManager;
    private List<QuestionAttenderPanel> questionPanels;
    private User user;

    public QuizAttender(DatabaseManager databaseManager, User user) {
        super("Attend Quiz");
        this.databaseManager = databaseManager;
        this.user = user;
        setSize(600, 600);
        setLayout(new BorderLayout());

        quizSelectBox = new JComboBox<>();
        questionsPanel = new JPanel();
        questionsPanel.setLayout(new BoxLayout(questionsPanel, BoxLayout.Y_AXIS));
        submitButton = new JButton("Submit Responses");
        questionPanels = new ArrayList<>();

        loadQuizzes();

        JPanel topPanel = new JPanel(new GridLayout(1, 2));
        topPanel.add(new JLabel("Select Quiz:"));
        topPanel.add(quizSelectBox);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(questionsPanel), BorderLayout.CENTER);
        add(submitButton, BorderLayout.SOUTH);

        quizSelectBox.addActionListener(e -> loadQuestions());
        submitButton.addActionListener(e -> submitResponses());

        loadQuestions();
    }

    public void display() {
        setVisible(true);
    }

    private void loadQuizzes() {
        try {
            ResultSet rs = databaseManager.executeQuery("SELECT quiz_id, title FROM quizzes");
            while (rs.next()) {
                quizSelectBox.addItem(rs.getInt("quiz_id") + ": " + rs.getString("title"));
            }
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }

    private void loadQuestions() {
        questionsPanel.removeAll();
        questionPanels.clear();
        String selectedQuiz = (String) quizSelectBox.getSelectedItem();
        if (selectedQuiz == null)
            return;

        int quizId = Integer.parseInt(selectedQuiz.split(":")[0]);

        try {
            ResultSet rs = databaseManager.executeQuery(
                    "SELECT question_id, question_text, question_type, options FROM questions WHERE quiz_id = ?",
                    quizId);

            while (rs.next()) {
                QuestionAttenderPanel qPanel = new QuestionAttenderPanel(rs.getInt("question_id"),
                        rs.getString("question_text"), rs.getString("question_type"), rs.getString("options"));
                questionPanels.add(qPanel);
                questionsPanel.add(qPanel);
            }

            questionsPanel.revalidate();
            questionsPanel.repaint();
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }

    private void submitResponses() {
        String selectedQuiz = (String) quizSelectBox.getSelectedItem();

        if (selectedQuiz == null) {
            JOptionPane.showMessageDialog(this, "Please select a quiz.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quizId = Integer.parseInt(selectedQuiz.split(":")[0]);
        List<String> answers = new ArrayList<>();

        for (QuestionAttenderPanel qPanel : questionPanels) {
            answers.add(qPanel.getAnswer());
        }

        try {
            databaseManager.executeUpdate("INSERT INTO responses (user_id, quiz_id, answers) VALUES (?, ?, ?)",
                    user.userId, quizId, String.join("~", answers));
            JOptionPane.showMessageDialog(this, "Responses submitted successfully.");
            dispose();
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }
}

// QuestionAttenderPanel Class
class QuestionAttenderPanel extends JPanel {
    private int questionId;
    private String questionText;
    private String questionType;
    private String options;
    private JComponent answerComponent;

    public QuestionAttenderPanel(int questionId, String questionText, String questionType, String options) {
        this.questionId = questionId;
        this.questionText = questionText;
        this.questionType = questionType;
        this.options = options;

        setLayout(new BorderLayout());
        JLabel questionLabel = new JLabel(questionText);
        add(questionLabel, BorderLayout.NORTH);

        switch (questionType) {
            case "Short Answer":
                answerComponent = new JTextField();
                break;
            case "True/False":
                answerComponent = new JComboBox<>(new String[] { "True", "False" });
                break;
            case "Multiple Choice":
                String[] optionArray = options.split("~");
                answerComponent = new JComboBox<>(optionArray);
                break;
            default:
                answerComponent = new JTextField();
                break;
        }

        add(answerComponent, BorderLayout.CENTER);
    }

    public String getAnswer() {
        if (answerComponent instanceof JTextField) {
            return ((JTextField) answerComponent).getText().trim();
        } else if (answerComponent instanceof JComboBox) {
            return (String) ((JComboBox<?>) answerComponent).getSelectedItem();
        }
        return "";
    }
}

// QuizResponseViewer Class
class QuizResponseViewer extends JFrame {
    private JComboBox<String> quizSelectBox;
    private JComboBox<String> studentSelectBox;
    private JTextArea responseArea;
    private JButton refreshButton;
    private DatabaseManager databaseManager;
    private User user;

    public QuizResponseViewer(DatabaseManager databaseManager, User user) {
        super("View Responses");
        this.databaseManager = databaseManager;
        this.user = user;
        setSize(600, 600);
        setLayout(new BorderLayout());

        quizSelectBox = new JComboBox<>();
        studentSelectBox = new JComboBox<>();
        responseArea = new JTextArea();
        responseArea.setEditable(false);
        refreshButton = new JButton("Refresh");

        loadQuizzes();

        JPanel topPanel = new JPanel(new GridLayout(2, 2));
        topPanel.add(new JLabel("Select Quiz:"));
        topPanel.add(quizSelectBox);
        topPanel.add(new JLabel("Select Student:"));
        topPanel.add(studentSelectBox);

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(responseArea), BorderLayout.CENTER);
        add(refreshButton, BorderLayout.SOUTH);

        quizSelectBox.addActionListener(e -> loadStudents());
        studentSelectBox.addActionListener(e -> loadResponses());
        refreshButton.addActionListener(e -> loadResponses());

        loadStudents();
        loadResponses();
    }

    public void display() {
        setVisible(true);
    }

    private void loadQuizzes() {
        try {
            ResultSet rs = databaseManager.executeQuery("SELECT quiz_id, title FROM quizzes");
            while (rs.next()) {
                quizSelectBox.addItem(rs.getInt("quiz_id") + ": " + rs.getString("title"));
            }
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }

    private void loadStudents() {
        studentSelectBox.removeAllItems();
        String selectedQuiz = (String) quizSelectBox.getSelectedItem();
        if (selectedQuiz == null)
            return;

        int quizId = Integer.parseInt(selectedQuiz.split(":")[0]);

        try {
            ResultSet rs = databaseManager.executeQuery(
                    "SELECT DISTINCT u.user_id, u.name FROM responses r JOIN users u ON r.user_id = u.user_id WHERE r.quiz_id = ?",
                    quizId);
            while (rs.next()) {
                studentSelectBox.addItem(rs.getInt("user_id") + ": " + rs.getString("name"));
            }
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }

    private void loadResponses() {
        responseArea.setText("");
        String selectedQuiz = (String) quizSelectBox.getSelectedItem();
        String selectedStudent = (String) studentSelectBox.getSelectedItem();
        if (selectedQuiz == null || selectedStudent == null)
            return;

        int quizId = Integer.parseInt(selectedQuiz.split(":")[0]);
        int userId = Integer.parseInt(selectedStudent.split(":")[0]);

        try {
            ResultSet rs = databaseManager.executeQuery(
                    "SELECT r.answers FROM responses r WHERE r.quiz_id = ? AND r.user_id = ?",
                    quizId, userId);
            if (rs.next()) {
                String[] answers = rs.getString("answers").split("~");

                ResultSet qs = databaseManager.executeQuery(
                        "SELECT question_text FROM questions WHERE quiz_id = ?", quizId);

                int i = 0;
                while (qs.next() && i < answers.length) {
                    responseArea.append("Question: " + qs.getString("question_text") + "\n");
                    responseArea.append("Answer: " + answers[i] + "\n\n");
                    i++;
                }
            } else {
                responseArea.setText("No responses found for selected student.");
            }
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }
}
