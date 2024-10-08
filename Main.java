import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

// Main Class
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LoginGUI loginGUI = new LoginGUI();
            loginGUI.setVisible(true);
        });
    }
}

// Person Class (Abstract Class)
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

// User Class (Inheritance)
class User extends Person {
    protected int userId;

    public User(int userId, String name, String username, String password, String role) {
        super(name, username, password, role);
        this.userId = userId;
    }
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

// DatabaseManager Class (Implements AutoCloseable)
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

        // Set up main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Create components
        usernameField = new JTextField(15);
        passwordField = new JPasswordField(15);
        loginButton = new JButton("Login");
        registerButton = new JButton("Register");

        // Create input panel
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Username Label and Field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(5, 5, 5, 5);
        inputPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        inputPanel.add(usernameField, gbc);

        // Password Label and Field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        inputPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        inputPanel.add(passwordField, gbc);

        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        // Add components to main panel
        mainPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add main panel to frame
        setContentPane(mainPanel);

        // Add action listeners
        loginButton.addActionListener(e -> login());
        registerButton.addActionListener(e -> openRegistration());

        // Set default button
        getRootPane().setDefaultButton(loginButton);

        // Frame settings
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack(); // Adjust size based on components
        setLocationRelativeTo(null); // Center on screen
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
    private JRadioButton teacherRadioButton;
    private JRadioButton studentRadioButton;
    private ButtonGroup roleGroup;
    private JButton registerButton;
    private DatabaseManager databaseManager;

    public RegistrationGUI(DatabaseManager databaseManager) {
        super("Register");
        this.databaseManager = databaseManager;

        // Set up main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Create components
        nameField = new JTextField(15);
        usernameField = new JTextField(15);
        passwordField = new JPasswordField(15);
        teacherRadioButton = new JRadioButton("Teacher");
        studentRadioButton = new JRadioButton("Student");
        roleGroup = new ButtonGroup();
        roleGroup.add(teacherRadioButton);
        roleGroup.add(studentRadioButton);
        studentRadioButton.setSelected(true);
        registerButton = new JButton("Register");

        // Create input panel
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(5, 5, 5, 5); // Add spacing between components

        // Name Label and Field
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.EAST;
        inputPanel.add(new JLabel("Name:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        inputPanel.add(nameField, gbc);

        // Username Label and Field
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.EAST;
        inputPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        inputPanel.add(usernameField, gbc);

        // Password Label and Field
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        inputPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.WEST;
        inputPanel.add(passwordField, gbc);

        // Role Label and Radio Buttons
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.EAST;
        inputPanel.add(new JLabel("Role:"), gbc);

        JPanel rolePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        rolePanel.add(teacherRadioButton);
        rolePanel.add(studentRadioButton);

        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.anchor = GridBagConstraints.WEST;
        inputPanel.add(rolePanel, gbc);

        // Add components to main panel
        mainPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(registerButton, BorderLayout.SOUTH);

        // Add action listener
        registerButton.addActionListener(e -> register());

        // Add main panel to frame
        setContentPane(mainPanel);

        // Frame settings
        pack();
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private void register() {
        String name = nameField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        String role = teacherRadioButton.isSelected() ? "teacher" : "student";

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

// QuizAppGUI Class (Implements QuizOperations Interface)
class QuizAppGUI implements QuizOperations {
    private JFrame mainFrame;
    private JButton createQuizButton;
    private JButton attendQuizButton;
    private JButton viewResponsesButton;
    private JButton logoutButton;
    private JButton manageQuizzesButton;
    private DatabaseManager databaseManager;
    private User user;
    private String token;

    public QuizAppGUI(User user, String token, DatabaseManager databaseManager) {
        this.user = user;
        this.token = token;
        this.databaseManager = databaseManager;

        mainFrame = new JFrame("Quiz Management System");

        // Set up main panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Welcome Label
        JLabel welcomeLabel = new JLabel("Welcome, " + user.name + " (" + user.role + ")", JLabel.CENTER);
        welcomeLabel.setFont(new Font("Arial", Font.BOLD, 16));
        mainPanel.add(welcomeLabel, BorderLayout.NORTH);

        // Buttons Panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(4, 1, 10, 10)); // Add spacing between buttons

        createQuizButton = new JButton("Create Quiz");
        attendQuizButton = new JButton("Attend Quiz");
        viewResponsesButton = new JButton("View Responses");

        buttonPanel.add(createQuizButton);
        buttonPanel.add(attendQuizButton);
        buttonPanel.add(viewResponsesButton);

        // Adjust buttons based on user role
        if ("teacher".equals(user.role)) {
            createQuizButton.setEnabled(true);
            viewResponsesButton.setEnabled(true);
            attendQuizButton.setEnabled(false);
            manageQuizzesButton = new JButton("Manage Quizzes");
            buttonPanel.add(manageQuizzesButton);
            manageQuizzesButton.addActionListener(e -> manageQuizzes());
        } else if ("student".equals(user.role)) {
            createQuizButton.setEnabled(false);
            viewResponsesButton.setEnabled(false);
            attendQuizButton.setEnabled(true);
        }

        mainPanel.add(buttonPanel, BorderLayout.CENTER);

        // Logout Button
        logoutButton = new JButton("Logout");
        mainPanel.add(logoutButton, BorderLayout.SOUTH);

        // Add action listeners
        createQuizButton.addActionListener(e -> createQuiz());
        attendQuizButton.addActionListener(e -> attendQuiz());
        viewResponsesButton.addActionListener(e -> viewResponses());
        logoutButton.addActionListener(e -> logout());

        // Set up frame
        mainFrame.setContentPane(mainPanel);
        mainFrame.setSize(400, 300);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLocationRelativeTo(null);
    }

    public void display() {
        mainFrame.setVisible(true);
    }

    private void logout() {
        try {
            databaseManager.logoutUser(token);
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(mainFrame, "Logout Error", e.getMessage());
        }
        mainFrame.dispose();
        new LoginGUI().setVisible(true);
    }

    private void manageQuizzes() {
        new QuizManager(databaseManager, user).setVisible(true);
    }

    @Override
    public void createQuiz() {
        new QuizCreator(databaseManager, user).setVisible(true);
    }

    @Override
    public void attendQuiz() {
        new QuizAttender(databaseManager, user).setVisible(true);
    }

    @Override
    public void viewResponses() {
        new QuizResponseViewer(databaseManager, user).setVisible(true);
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

        // Set up main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Quiz Title Panel
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.add(new JLabel("Quiz Title:"), BorderLayout.WEST);
        quizTitleField = new JTextField(30);
        titlePanel.add(quizTitleField, BorderLayout.CENTER);

        // Questions Panel
        questionsPanel = new JPanel();
        questionsPanel.setLayout(new BoxLayout(questionsPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(questionsPanel);

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        addQuestionButton = new JButton("Add Question");
        saveButton = new JButton("Save Quiz");
        buttonPanel.add(addQuestionButton);
        buttonPanel.add(saveButton);

        // Add components to main panel
        mainPanel.add(titlePanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add action listeners
        addQuestionButton.addActionListener(e -> addQuestionPanel());
        saveButton.addActionListener(e -> saveQuiz());

        // Initialize question panels list
        questionPanels = new ArrayList<>();

        // Add initial question panel
        addQuestionPanel();

        // Set up frame
        setContentPane(mainPanel);
        setSize(700, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public void display() {
        setVisible(true);
    }

    private void addQuestionPanel() {
        int questionNumber = questionPanels.size() + 1;
        QuestionCreatorPanel questionPanel = new QuestionCreatorPanel(this, questionNumber);
        questionPanels.add(questionPanel);
        questionsPanel.add(questionPanel);
        questionsPanel.revalidate();
        questionsPanel.repaint();
    }

    public void removeQuestionPanel(QuestionCreatorPanel questionPanel) {
        questionPanels.remove(questionPanel);
        questionsPanel.remove(questionPanel);
        updateQuestionNumbers();
        questionsPanel.revalidate();
        questionsPanel.repaint();
    }

    private void updateQuestionNumbers() {
        int number = 1;
        for (QuestionCreatorPanel qPanel : questionPanels) {
            qPanel.updateQuestionNumber(number++);
        }
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

    public QuestionCreatorPanel(QuizCreator parent, int questionNumber) {
        this.parent = parent;
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Question " + questionNumber));

        // Question Text Panel
        JPanel questionTextPanel = new JPanel(new BorderLayout());
        questionTextPanel.add(new JLabel("Question:"), BorderLayout.WEST);
        questionField = new JTextField(30);
        questionTextPanel.add(questionField, BorderLayout.CENTER);

        // Question Type Panel
        JPanel questionTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        questionTypePanel.add(new JLabel("Type:"));
        questionTypeBox = new JComboBox<>(new String[] { "Short Answer", "True/False", "Multiple Choice" });
        questionTypePanel.add(questionTypeBox);

        // Options Panel
        optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        JScrollPane optionsScrollPane = new JScrollPane(optionsPanel);
        optionsScrollPane.setPreferredSize(new Dimension(400, 100));

        // Buttons Panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        addOptionButton = new JButton("Add Option");
        removeQuestionButton = new JButton("Remove Question");
        buttonsPanel.add(addOptionButton);
        buttonsPanel.add(removeQuestionButton);

        // Add components to main panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(questionTextPanel, BorderLayout.NORTH);
        topPanel.add(questionTypePanel, BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(optionsScrollPane, BorderLayout.CENTER);
        add(buttonsPanel, BorderLayout.SOUTH);

        // Initialize option fields list
        optionFields = new ArrayList<>();

        // Add action listeners
        questionTypeBox.addActionListener(e -> updateOptionFields());
        addOptionButton.addActionListener(e -> addOptionField());
        removeQuestionButton.addActionListener(e -> parent.removeQuestionPanel(this));

        // Initialize options
        updateOptionFields();
    }

    public void updateQuestionNumber(int questionNumber) {
        setBorder(BorderFactory.createTitledBorder("Question " + questionNumber));
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

        // Set up main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Quiz Selection Panel
        JPanel quizSelectPanel = new JPanel(new BorderLayout());
        quizSelectPanel.add(new JLabel("Select Quiz:"), BorderLayout.WEST);
        quizSelectBox = new JComboBox<>();
        quizSelectPanel.add(quizSelectBox, BorderLayout.CENTER);

        // Questions Panel
        questionsPanel = new JPanel();
        questionsPanel.setLayout(new BoxLayout(questionsPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(questionsPanel);

        // Submit Button
        submitButton = new JButton("Submit Responses");

        // Add components to main panel
        mainPanel.add(quizSelectPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(submitButton, BorderLayout.SOUTH);

        // Add action listeners
        quizSelectBox.addActionListener(e -> loadQuestions());
        submitButton.addActionListener(e -> submitResponses());

        // Initialize question panels list
        questionPanels = new ArrayList<>();

        // Load quizzes and questions
        loadQuizzes();
        loadQuestions();

        // Set up frame
        setContentPane(mainPanel);
        setSize(700, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public void display() {
        setVisible(true);
    }

    private void loadQuizzes() {
        try {
            quizSelectBox.removeAllItems();
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
            // Check if student has already submitted responses
            ResultSet rsCheck = databaseManager.executeQuery(
                    "SELECT * FROM responses WHERE user_id = ? AND quiz_id = ?", user.userId, quizId);
            if (rsCheck.next()) {
                // Student has already submitted responses
                questionsPanel.add(new JLabel("You have already attended this quiz."));
                submitButton.setEnabled(false);
            } else {
                submitButton.setEnabled(true);
                ResultSet rs = databaseManager.executeQuery(
                        "SELECT question_id, question_text, question_type, options FROM questions WHERE quiz_id = ?",
                        quizId);

                while (rs.next()) {
                    QuestionAttenderPanel qPanel = new QuestionAttenderPanel(rs.getInt("question_id"),
                            rs.getString("question_text"), rs.getString("question_type"), rs.getString("options"));
                    questionPanels.add(qPanel);
                    questionsPanel.add(qPanel);
                }
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

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Question"));

        JLabel questionLabel = new JLabel("<html><b>" + questionText + "</b></html>");
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
    private JTable responseTable;
    private JButton refreshButton;
    private JButton deleteResponseButton;
    private DatabaseManager databaseManager;
    private User user;

    public QuizResponseViewer(DatabaseManager databaseManager, User user) {
        super("View Responses");
        this.databaseManager = databaseManager;
        this.user = user;

        // Set up main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Selection Panel
        JPanel selectionPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        quizSelectBox = new JComboBox<>();
        studentSelectBox = new JComboBox<>();

        selectionPanel.add(new JLabel("Select Quiz:"));
        selectionPanel.add(quizSelectBox);
        selectionPanel.add(new JLabel("Select Student:"));
        selectionPanel.add(studentSelectBox);

        // Response Table
        responseTable = new JTable();
        JScrollPane scrollPane = new JScrollPane(responseTable);

        // Buttons Panel
        refreshButton = new JButton("Refresh");
        deleteResponseButton = new JButton("Delete Response");

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottomPanel.add(refreshButton);
        bottomPanel.add(deleteResponseButton);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Add components to main panel
        mainPanel.add(selectionPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Add action listeners
        quizSelectBox.addActionListener(e -> loadStudents());
        studentSelectBox.addActionListener(e -> loadResponses());
        refreshButton.addActionListener(e -> loadResponses());
        deleteResponseButton.addActionListener(e -> deleteResponse());

        // Load quizzes and initial data
        loadQuizzes();
        loadStudents();
        loadResponses();

        // Set up frame
        setContentPane(mainPanel);
        setSize(700, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    public void display() {
        setVisible(true);
    }

    private void loadQuizzes() {
        try {
            quizSelectBox.removeAllItems();
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

                DefaultTableModel model = new DefaultTableModel();
                model.addColumn("Question");
                model.addColumn("Answer");

                int i = 0;
                while (qs.next() && i < answers.length) {
                    model.addRow(new Object[] { qs.getString("question_text"), answers[i] });
                    i++;
                }

                responseTable.setModel(model);
                responseTable.setRowHeight(30);

            } else {
                DefaultTableModel model = new DefaultTableModel();
                model.addColumn("Message");
                model.addRow(new Object[] { "No responses found for selected student." });
                responseTable.setModel(model);
            }
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }

    private void deleteResponse() {
        String selectedQuiz = (String) quizSelectBox.getSelectedItem();
        String selectedStudent = (String) studentSelectBox.getSelectedItem();
        if (selectedQuiz == null || selectedStudent == null) {
            JOptionPane.showMessageDialog(this, "Please select a quiz and a student.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quizId = Integer.parseInt(selectedQuiz.split(":")[0]);
        int userId = Integer.parseInt(selectedStudent.split(":")[0]);

        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this student's response? The student will be able to reattend the quiz.", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            databaseManager.executeUpdate("DELETE FROM responses WHERE quiz_id = ? AND user_id = ?", quizId, userId);
            JOptionPane.showMessageDialog(this, "Response deleted successfully.");
            loadResponses();
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }
}

// QuizManager Class
class QuizManager extends JFrame {
    private JTable quizTable;
    private JButton deleteQuizButton;
    private DatabaseManager databaseManager;
    private User user;

    public QuizManager(DatabaseManager databaseManager, User user) {
        super("Manage Quizzes");
        this.databaseManager = databaseManager;
        this.user = user;

        // Set up main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Quiz Table
        quizTable = new JTable();
        JScrollPane scrollPane = new JScrollPane(quizTable);

        // Delete Button
        deleteQuizButton = new JButton("Delete Selected Quiz");

        // Add components to main panel
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(deleteQuizButton, BorderLayout.SOUTH);

        // Add action listeners
        deleteQuizButton.addActionListener(e -> deleteSelectedQuiz());

        // Load quizzes
        loadQuizzes();

        // Set up frame
        setContentPane(mainPanel);
        setSize(600, 400);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private void loadQuizzes() {
        try {
            DefaultTableModel model = new DefaultTableModel();
            model.addColumn("Quiz ID");
            model.addColumn("Title");

            ResultSet rs = databaseManager.executeQuery("SELECT quiz_id, title FROM quizzes");
            while (rs.next()) {
                model.addRow(new Object[] { rs.getInt("quiz_id"), rs.getString("title") });
            }

            quizTable.setModel(model);
            quizTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }

    private void deleteSelectedQuiz() {
        int selectedRow = quizTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a quiz to delete.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quizId = (int) quizTable.getValueAt(selectedRow, 0);
        String quizTitle = (String) quizTable.getValueAt(selectedRow, 1);

        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete quiz \"" + quizTitle + "\"?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            // Delete from responses
            databaseManager.executeUpdate("DELETE FROM responses WHERE quiz_id = ?", quizId);
            // Delete from questions
            databaseManager.executeUpdate("DELETE FROM questions WHERE quiz_id = ?", quizId);
            // Delete from quizzes
            databaseManager.executeUpdate("DELETE FROM quizzes WHERE quiz_id = ?", quizId);

            JOptionPane.showMessageDialog(this, "Quiz deleted successfully.");
            loadQuizzes();

        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }
}
