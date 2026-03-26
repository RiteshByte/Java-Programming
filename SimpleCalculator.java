// SimpleCalculator.java

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.border.EmptyBorder;

public class SimpleCalculator extends JFrame implements ActionListener {

    private JTextField tf;
    private JButton[] numBtns = new JButton[10];
    private JButton add, sub, mul, div, eq, clr, backspace, decimal;
    private JButton mAdd, mSub, mRecall, mClear; // Memory buttons
    
    private double first = 0, second = 0, memory = 0;
    private String op = "";
    private JButton activeOperator = null;

    // Constructor
    public SimpleCalculator() {
        setTitle("Simple Calculator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(300, 450));
        
        // Create main panel with padding
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Text Field
        tf = new JTextField();
        tf.setEditable(false);
        tf.setHorizontalAlignment(JTextField.RIGHT);
        tf.setFont(new Font("Arial", Font.BOLD, 24));
        tf.setBackground(Color.WHITE);
        tf.setPreferredSize(new Dimension(300, 60));
        
        // Add keyboard support
        setupKeyboardSupport();
        
        // Create button panels
        JPanel buttonPanel = new JPanel(new GridLayout(5, 4, 8, 8));
        
        // Initialize Number Buttons (0-9)
        for (int i = 0; i <= 9; i++) {
            numBtns[i] = new JButton(String.valueOf(i));
            numBtns[i].setFont(new Font("Arial", Font.BOLD, 18));
            numBtns[i].addActionListener(this);
            numBtns[i].setFocusPainted(false);
        }
        
        // Operator Buttons
        add = createOperatorButton("+");
        sub = createOperatorButton("-");
        mul = createOperatorButton("×");  // Using multiplication symbol
        div = createOperatorButton("÷");  // Using division symbol
        eq = createOperatorButton("=");
        clr = createOperatorButton("C");
        backspace = createOperatorButton("⌫");
        decimal = createOperatorButton(".");
        
        // Memory Buttons
        mAdd = createMemoryButton("M+");
        mSub = createMemoryButton("M-");
        mRecall = createMemoryButton("MR");
        mClear = createMemoryButton("MC");
        
        // Layout buttons (5 rows, 4 columns)
        // Row 1: Memory buttons
        buttonPanel.add(mClear);
        buttonPanel.add(mRecall);
        buttonPanel.add(mSub);
        buttonPanel.add(mAdd);
        
        // Row 2: 7, 8, 9, ÷
        buttonPanel.add(numBtns[7]);
        buttonPanel.add(numBtns[8]);
        buttonPanel.add(numBtns[9]);
        buttonPanel.add(div);
        
        // Row 3: 4, 5, 6, ×
        buttonPanel.add(numBtns[4]);
        buttonPanel.add(numBtns[5]);
        buttonPanel.add(numBtns[6]);
        buttonPanel.add(mul);
        
        // Row 4: 1, 2, 3, -
        buttonPanel.add(numBtns[1]);
        buttonPanel.add(numBtns[2]);
        buttonPanel.add(numBtns[3]);
        buttonPanel.add(sub);
        
        // Row 5: 0, ., C, ⌫, +
        buttonPanel.add(numBtns[0]);
        buttonPanel.add(decimal);
        buttonPanel.add(clr);
        buttonPanel.add(backspace);
        buttonPanel.add(eq);
        buttonPanel.add(add);
        
        // Special styling for equals button
        eq.setBackground(new Color(66, 133, 244));
        eq.setForeground(Color.WHITE);
        eq.setFont(new Font("Arial", Font.BOLD, 20));
        
        // Style clear and backspace buttons
        clr.setBackground(new Color(220, 53, 69));
        clr.setForeground(Color.WHITE);
        backspace.setBackground(new Color(108, 117, 125));
        backspace.setForeground(Color.WHITE);
        
        // Add components to main panel
        mainPanel.add(tf, BorderLayout.NORTH);
        mainPanel.add(buttonPanel, BorderLayout.CENTER);
        
        add(mainPanel);
        pack();
    }
    
    private JButton createOperatorButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Arial", Font.BOLD, 18));
        btn.addActionListener(this);
        btn.setFocusPainted(false);
        return btn;
    }
    
    private JButton createMemoryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Arial", Font.PLAIN, 14));
        btn.addActionListener(this);
        btn.setFocusPainted(false);
        btn.setBackground(new Color(240, 240, 240));
        return btn;
    }
    
    private void setupKeyboardSupport() {
        // Add key bindings to the root pane
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getRootPane().getActionMap();
        
        // Number keys
        for (int i = 0; i <= 9; i++) {
            final int digit = i;
            inputMap.put(KeyStroke.getKeyStroke(Character.forDigit(i, 10)), "digit" + i);
            actionMap.put("digit" + i, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    appendNumber(digit);
                }
            });
        }
        
        // Operators
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "add");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "add");
        actionMap.put("add", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleOperator("+");
            }
        });
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "subtract");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "subtract");
        actionMap.put("subtract", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleOperator("-");
            }
        });
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MULTIPLY, 0), "multiply");
        actionMap.put("multiply", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleOperator("×");
            }
        });
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DIVIDE, 0), "divide");
        actionMap.put("divide", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleOperator("÷");
            }
        });
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "equals");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), "equals");
        actionMap.put("equals", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                calculate();
            }
        });
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "backspace");
        actionMap.put("backspace", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleBackspace();
            }
        });
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear");
        actionMap.put("clear", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleClear();
            }
        });
        
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DECIMAL, 0), "decimal");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, 0), "decimal");
        actionMap.put("decimal", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleDecimal();
            }
        });
    }
    
    private void appendNumber(int num) {
        String current = tf.getText();
        if (current.equals("0") && num != 0) {
            tf.setText(String.valueOf(num));
        } else if (!current.equals("0") || num != 0) {
            tf.setText(current + num);
        }
    }
    
    private void handleDecimal() {
        String current = tf.getText();
        if (!current.contains(".")) {
            tf.setText(current + ".");
        }
    }
    
    private void handleBackspace() {
        String current = tf.getText();
        if (current.length() > 0) {
            tf.setText(current.substring(0, current.length() - 1));
        }
    }
    
    private void handleClear() {
        tf.setText("");
        first = second = 0;
        op = "";
        if (activeOperator != null) {
            resetOperatorColors();
        }
    }
    
    private void handleOperator(String operator) {
        // Complete any pending operation first
        if (!op.isEmpty() && !tf.getText().isEmpty()) {
            calculate();
        }
        
        try {
            if (!tf.getText().isEmpty()) {
                first = Double.parseDouble(tf.getText());
            } else {
                first = 0;
            }
        } catch (NumberFormatException ex) {
            first = 0;
        }
        
        op = operator;
        tf.setText("");
        
        // Visual feedback for active operator
        resetOperatorColors();
        JButton active = null;
        switch (operator) {
            case "+": active = add; break;
            case "-": active = sub; break;
            case "×": active = mul; break;
            case "÷": active = div; break;
        }
        if (active != null) {
            active.setBackground(new Color(200, 200, 255));
            activeOperator = active;
        }
    }
    
    private void resetOperatorColors() {
        Color defaultColor = UIManager.getColor("Button.background");
        if (defaultColor == null) defaultColor = new Color(240, 240, 240);
        for (JButton btn : new JButton[]{add, sub, mul, div}) {
            btn.setBackground(defaultColor);
        }
        activeOperator = null;
    }
    
    private void calculate() {
        if (op.isEmpty()) return;
        
        try {
            if (!tf.getText().isEmpty()) {
                second = Double.parseDouble(tf.getText());
            } else {
                second = 0;
            }
        } catch (NumberFormatException ex) {
            second = 0;
        }
        
        double res = 0;
        boolean error = false;
        
        switch (op) {
            case "+":
                res = first + second;
                break;
            case "-":
                res = first - second;
                break;
            case "×":
                res = first * second;
                break;
            case "÷":
                if (second == 0) {
                    tf.setText("Error: Division by zero");
                    error = true;
                } else {
                    res = first / second;
                }
                break;
            default:
                res = second;
        }
        
        if (!error) {
            // Format result nicely
            if (res == (long) res) {
                tf.setText(String.valueOf((long) res));
            } else {
                String formatted = String.format("%.10f", res).replaceAll("0*$", "").replaceAll("\\.$", "");
                tf.setText(formatted);
            }
            first = res;
        }
        
        op = "";
        resetOperatorColors();
    }
    
    private void handleMemoryAdd() {
        try {
            if (!tf.getText().isEmpty()) {
                memory += Double.parseDouble(tf.getText());
                tf.setText("");
                showMemoryStatus();
            }
        } catch (NumberFormatException ex) {
            // Ignore invalid input
        }
    }
    
    private void handleMemorySubtract() {
        try {
            if (!tf.getText().isEmpty()) {
                memory -= Double.parseDouble(tf.getText());
                tf.setText("");
                showMemoryStatus();
            }
        } catch (NumberFormatException ex) {
            // Ignore invalid input
        }
    }
    
    private void handleMemoryRecall() {
        if (memory != 0) {
            if (memory == (long) memory) {
                tf.setText(String.valueOf((long) memory));
            } else {
                tf.setText(String.valueOf(memory));
            }
        } else {
            tf.setText("0");
        }
    }
    
    private void handleMemoryClear() {
        memory = 0;
        showMemoryStatus();
    }
    
    private void showMemoryStatus() {
        // Optional: Update a status label or just clear text
        if (memory != 0) {
            setTitle("Simple Calculator [M=" + memory + "]");
        } else {
            setTitle("Simple Calculator");
        }
    }

    // Event Handling
    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        
        // Number buttons
        for (int i = 0; i <= 9; i++) {
            if (src == numBtns[i]) {
                appendNumber(i);
                return;
            }
        }
        
        // Decimal button
        if (src == decimal) {
            handleDecimal();
            return;
        }
        
        // Clear button
        if (src == clr) {
            handleClear();
            return;
        }
        
        // Backspace button
        if (src == backspace) {
            handleBackspace();
            return;
        }
        
        // Operator buttons
        if (src == add) {
            handleOperator("+");
        } else if (src == sub) {
            handleOperator("-");
        } else if (src == mul) {
            handleOperator("×");
        } else if (src == div) {
            handleOperator("÷");
        }
        // Equals button
        else if (src == eq) {
            calculate();
        }
        // Memory buttons
        else if (src == mAdd) {
            handleMemoryAdd();
        } else if (src == mSub) {
            handleMemorySubtract();
        } else if (src == mRecall) {
            handleMemoryRecall();
        } else if (src == mClear) {
            handleMemoryClear();
        }
    }

    // Main Method
    public static void main(String[] args) {
        try {
            // Set system look and feel for better appearance
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            SimpleCalculator calculator = new SimpleCalculator();
            calculator.setVisible(true);
        });
    }
}