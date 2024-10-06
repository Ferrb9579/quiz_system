import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            QuizAppGUI quizAppGUI = new QuizAppGUI();
            quizAppGUI.display();
        });
    }
}

abstract class Person {
    protected String name;

    public Person(String name) {
        this.name = name;
    }
}

interface QuizOperations {
    void createQuiz();

    void attendQuiz();

    void viewResponses();
}

class QuizAppGUI implements QuizOperations {
    private JFrame mainFrame;
    private JButton createQuizButton;
    private JButton attendQuizButton;
    private JButton viewResponsesButton;

    private DatabaseManager databaseManager;

    public QuizAppGUI() {
        mainFrame = new JFrame("Quiz Management System");
        mainFrame.setSize(400, 200);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLayout(new GridLayout(3, 1));

        createQuizButton = new JButton("Create Quiz");
        attendQuizButton = new JButton("Attend Quiz");
        viewResponsesButton = new JButton("View Responses");

        databaseManager = new DatabaseManager();

        mainFrame.add(createQuizButton);
        mainFrame.add(attendQuizButton);
        mainFrame.add(viewResponsesButton);

        createQuizButton.addActionListener(e -> createQuiz());
        attendQuizButton.addActionListener(e -> attendQuiz());
        viewResponsesButton.addActionListener(e -> viewResponses());
    }

    public void display() {
        mainFrame.setVisible(true);
    }

    @Override
    public void createQuiz() {
        new QuizCreator(databaseManager).display();
    }

    @Override
    public void attendQuiz() {
        new QuizAttender(databaseManager).display();
    }

    @Override
    public void viewResponses() {
        new QuizResponseViewer(databaseManager).display();
    }
}

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
}

class QuizCreator extends JFrame {
    private JTextField quizTitleField;
    private JPanel questionsPanel;
    private JButton addQuestionButton;
    private JButton saveButton;
    private DatabaseManager databaseManager;
    private List<QuestionCreatorPanel> questionPanels;

    public QuizCreator(DatabaseManager databaseManager) {
        super("Create Quiz");
        this.databaseManager = databaseManager;
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
        QuestionCreatorPanel questionPanel = new QuestionCreatorPanel();
        questionPanels.add(questionPanel);
        questionsPanel.add(questionPanel);
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

class QuestionCreatorPanel extends JPanel {
    private JTextField questionField;
    private JComboBox<String> questionTypeBox;
    private JPanel optionsPanel;
    private JButton addOptionButton;
    private List<JTextField> optionFields;

    public QuestionCreatorPanel() {
        setLayout(new BorderLayout());
        questionField = new JTextField();
        questionTypeBox = new JComboBox<>(new String[] { "Short Answer", "True/False", "Multiple Choice" });
        optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        addOptionButton = new JButton("Add Option");
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
        add(addOptionButton, BorderLayout.SOUTH);

        questionTypeBox.addActionListener(e -> updateOptionFields());
        addOptionButton.addActionListener(e -> addOptionField());

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

class QuizAttender extends JFrame {
    private JTextField nameField;
    private JComboBox<String> quizSelectBox;
    private JPanel questionsPanel;
    private JButton submitButton;
    private DatabaseManager databaseManager;
    private List<QuestionAttenderPanel> questionPanels;

    public QuizAttender(DatabaseManager databaseManager) {
        super("Attend Quiz");
        this.databaseManager = databaseManager;
        setSize(600, 600);
        setLayout(new BorderLayout());

        nameField = new JTextField();
        quizSelectBox = new JComboBox<>();
        questionsPanel = new JPanel();
        questionsPanel.setLayout(new BoxLayout(questionsPanel, BoxLayout.Y_AXIS));
        submitButton = new JButton("Submit Responses");
        questionPanels = new ArrayList<>();

        loadQuizzes();

        JPanel topPanel = new JPanel(new GridLayout(2, 2));
        topPanel.add(new JLabel("Name:"));
        topPanel.add(nameField);
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
                QuestionAttenderPanel qPanel = new QuestionAttenderPanel(
                        rs.getInt("question_id"),
                        rs.getString("question_text"),
                        rs.getString("question_type"),
                        rs.getString("options"));
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
        String name = nameField.getText().trim();
        String selectedQuiz = (String) quizSelectBox.getSelectedItem();

        if (name.isEmpty() || selectedQuiz == null) {
            JOptionPane.showMessageDialog(this, "Please enter your name and select a quiz.", "Input Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quizId = Integer.parseInt(selectedQuiz.split(":")[0]);
        List<String> answers = new ArrayList<>();

        for (QuestionAttenderPanel qPanel : questionPanels) {
            answers.add(qPanel.getAnswer());
        }

        try {
            databaseManager.executeUpdate("INSERT INTO responses (participant_name, quiz_id, answers) VALUES (?, ?, ?)",
                    name, quizId, String.join("~", answers));
            JOptionPane.showMessageDialog(this, "Responses submitted successfully.");
            dispose();
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }
}

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

class QuizResponseViewer extends JFrame {
    private JTextArea responseArea;
    private JButton refreshButton;
    private DatabaseManager databaseManager;

    public QuizResponseViewer(DatabaseManager databaseManager) {
        super("View Responses");
        this.databaseManager = databaseManager;
        setSize(600, 600);
        setLayout(new BorderLayout());

        responseArea = new JTextArea();
        responseArea.setEditable(false);
        refreshButton = new JButton("Refresh");

        add(new JScrollPane(responseArea), BorderLayout.CENTER);
        add(refreshButton, BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> loadResponses());

        loadResponses();
    }

    public void display() {
        setVisible(true);
    }

    private void loadResponses() {
        responseArea.setText("");
        try {
            ResultSet rs = databaseManager.executeQuery(
                    "SELECT r.participant_name, q.title, r.answers, r.quiz_id " +
                            "FROM responses r JOIN quizzes q ON r.quiz_id = q.quiz_id");

            while (rs.next()) {
                responseArea.append("Name: " + rs.getString("participant_name") + "\n");
                responseArea.append("Quiz Title: " + rs.getString("title") + "\n");
                String[] answers = rs.getString("answers").split("~");

                int quizId = rs.getInt("quiz_id");
                ResultSet qs = databaseManager.executeQuery(
                        "SELECT question_text FROM questions WHERE quiz_id = ?", quizId);

                int i = 0;
                while (qs.next() && i < answers.length) {
                    responseArea.append("Question: " + qs.getString("question_text") + "\n");
                    responseArea.append("Answer: " + answers[i] + "\n\n");
                    i++;
                }
            }
        } catch (SQLException e) {
            DatabaseManager.showErrorDialog(this, "Database Error", e.getMessage());
        }
    }

}
