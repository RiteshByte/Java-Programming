import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.sql.*;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
public class Calculator extends JFrame {
    // ── DB CONFIG ── change these to match your MySQL setup ──────────────────
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/calculator_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "10109";
    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Color DISPLAY_BG     = new Color(55, 55, 55);
    private static final Color BODY_BG        = new Color(238, 238, 238);
    private static final Color BTN_LIGHT      = new Color(245, 245, 245);
    private static final Color BTN_LIGHT_H    = new Color(225, 225, 225);
    private static final Color BTN_DARK       = new Color(70, 70, 70);
    private static final Color BTN_DARK_H     = new Color(90, 90, 90);
    private static final Color GRID_LINE      = new Color(210, 210, 210);
    private static final Color TXT_DARK       = new Color(50, 50, 50);
    private static final Color TXT_WHITE      = Color.WHITE;
    private static final Color EXPR_GRAY      = new Color(180, 180, 180);
    // History panel colors
    private static final Color PANEL_BG  = new Color(30, 30, 32);
    private static final Color ROW_A     = new Color(40, 40, 44);
    private static final Color ROW_B     = new Color(48, 48, 52);
    private static final Color ACCENT    = new Color(100, 180, 255);
    private static final Color BTN_RED   = new Color(200, 55, 55);
    private static final Color BTN_RH    = new Color(230, 80, 80);
    // ── State ─────────────────────────────────────────────────────────────────
    private JLabel displayLabel, exprLabel;
    private String input = "", op = "", histText = "";
    private double first = 0;
    private boolean newInput = false;
    // ── History ───────────────────────────────────────────────────────────────
    private JPanel historyPanel;
    private DefaultTableModel tableModel;
    private boolean historyOpen = false;
    private Connection conn;
    // ── Number formatter ─────────────────────────────────────────────────────
    private static final DecimalFormat COMMA_FMT = new DecimalFormat("#,###.##########");
    // ─────────────────────────────────────────────────────────────────────────
    public Calculator() {
        initDB();
        setTitle("Calculator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setUndecorated(true);
        setResizable(false);
        JLayeredPane root = new JLayeredPane();
        root.setPreferredSize(new Dimension(320, 520));
        JPanel calc = buildCalcPanel();
        calc.setBounds(0, 0, 320, 520);
        root.add(calc, JLayeredPane.DEFAULT_LAYER);
        historyPanel = buildHistoryPanel();
        historyPanel.setBounds(330, 0, 320, 520);
        root.add(historyPanel, JLayeredPane.PALETTE_LAYER);
        setContentPane(root);
        pack();
        setLocationRelativeTo(null);
        setShape(new RoundRectangle2D.Double(0, 0, 320, 520, 24, 24));
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                char c = e.getKeyChar();
                if (Character.isDigit(c))         digit(String.valueOf(c));
                else if (c == '+')                operate("+");
                else if (c == '-')                operate("-");
                else if (c == '*')                operate("\u00D7");
                else if (c == '/')                operate("\u00F7");
                else if (c == '.' || c == ',')    dot();
                else if (c == '\n' || c == '=')   Calculator.this.equals();
                else if (c == 'c' || c == 'C')    clear();
                else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) backspace();
            }
        });
    }
    // ═══════════════════════════════════════════════════ CALC PANEL ══════════
    private JPanel buildCalcPanel() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BODY_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.add(buildDisplay(), BorderLayout.NORTH);
        p.add(buildButtons(), BorderLayout.CENTER);
        return p;
    }
    private JPanel buildDisplay() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(DISPLAY_BG);
                // Top rounded corners only
                g2.fillRoundRect(0, 0, getWidth(), getHeight() + 24, 24, 24);
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(320, 160));
        p.setBorder(BorderFactory.createEmptyBorder(30, 20, 20, 20));
        // Title bar with drag + close + history
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        JButton close = closeBtn();
        JPanel lft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        lft.setOpaque(false);
        lft.add(close);
        JButton hBtn = historyBtn();
        hBtn.addActionListener(e -> toggleHistory());
        JPanel rgt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rgt.setOpaque(false);
        rgt.add(hBtn);
        titleBar.add(lft, BorderLayout.WEST);
        titleBar.add(rgt, BorderLayout.EAST);
        final Point[] drag = {null};
        titleBar.addMouseListener(new MouseAdapter(){
            public void mousePressed(MouseEvent e){ drag[0] = e.getPoint(); }
        });
        titleBar.addMouseMotionListener(new MouseMotionAdapter(){
            public void mouseDragged(MouseEvent e){
                if(drag[0] != null){
                    Point l = getLocation();
                    setLocation(l.x + e.getX() - drag[0].x, l.y + e.getY() - drag[0].y);
                }
            }
        });
        // Expression label (small, gray)
        exprLabel = new JLabel(" ");
        exprLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        exprLabel.setForeground(EXPR_GRAY);
        exprLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        // Result display (large, white)
        displayLabel = new JLabel("0");
        displayLabel.setFont(new Font("Segoe UI", Font.PLAIN, 48));
        displayLabel.setForeground(TXT_WHITE);
        displayLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        JPanel textArea = new JPanel(new BorderLayout());
        textArea.setOpaque(false);
        textArea.add(exprLabel, BorderLayout.NORTH);
        textArea.add(displayLabel, BorderLayout.CENTER);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(titleBar, BorderLayout.NORTH);
        wrapper.add(textArea, BorderLayout.CENTER);
        p.add(wrapper, BorderLayout.CENTER);
        return p;
    }
    private JPanel buildButtons() {
        JPanel container = new JPanel(new GridLayout(5, 4, 1, 1));
        container.setBackground(GRID_LINE);
        container.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, GRID_LINE));
        String[][] rows = {
            {"C", "\u00F7", "\u00D7", "CE"},
            {"7", "8", "9", "-"},
            {"4", "5", "6", "+"},
            {"1", "2", "3", "="},
            {"%", "0", ".", "="}
        };
        // We need to handle the = button spanning 2 rows
        // Use a different approach: GridBagLayout
        container.removeAll();
        container.setLayout(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.BOTH;
        g.weightx = 1;
        g.weighty = 1;
        g.insets = new Insets(0, 0, 0, 0);
        // Row 0: C, div, mul, CE
        String[] r0 = {"C", "\u00F7", "\u00D7", "CE"};
        for (int c = 0; c < 4; c++) {
            g.gridx = c; g.gridy = 0; g.gridwidth = 1; g.gridheight = 1;
            container.add(flatBtn(r0[c]), g);
        }
        // Row 1: 7 8 9 -
        String[] r1 = {"7", "8", "9", "-"};
        for (int c = 0; c < 4; c++) {
            g.gridx = c; g.gridy = 1; g.gridwidth = 1; g.gridheight = 1;
            container.add(flatBtn(r1[c]), g);
        }
        // Row 2: 4 5 6 +
        String[] r2 = {"4", "5", "6", "+"};
        for (int c = 0; c < 4; c++) {
            g.gridx = c; g.gridy = 2; g.gridwidth = 1; g.gridheight = 1;
            container.add(flatBtn(r2[c]), g);
        }
        // Row 3: 1 2 3 = (= spans 2 rows)
        String[] r3 = {"1", "2", "3"};
        for (int c = 0; c < 3; c++) {
            g.gridx = c; g.gridy = 3; g.gridwidth = 1; g.gridheight = 1;
            container.add(flatBtn(r3[c]), g);
        }
        g.gridx = 3; g.gridy = 3; g.gridwidth = 1; g.gridheight = 2;
        container.add(flatBtn("="), g);
        g.gridheight = 1;
        // Row 4: % 0 .
        String[] r4 = {"%", "0", "."};
        for (int c = 0; c < 3; c++) {
            g.gridx = c; g.gridy = 4; g.gridwidth = 1; g.gridheight = 1;
            container.add(flatBtn(r4[c]), g);
        }
        // Wrap in a panel that clips the bottom rounded corners
        JPanel outer = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics gr) {
                // don't paint anything - let children paint
            }
        };
        outer.setOpaque(false);
        outer.add(container, BorderLayout.CENTER);
        return outer;
    }
    // ═════════════════════════════════════════════ HISTORY PANEL ════════════
    private JPanel buildHistoryPanel() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PANEL_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(20, 16, 16, 16));
        // Header row
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        JLabel title = new JLabel("History");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        JButton clearAll = miniBtn("Clear All", BTN_RED, BTN_RH);
        clearAll.setPreferredSize(new Dimension(80, 26));
        clearAll.addActionListener(e -> clearAll());
        header.add(title, BorderLayout.WEST);
        header.add(clearAll, BorderLayout.EAST);
        // Table
        tableModel = new DefaultTableModel(new String[]{"Expression", "Result", "Time"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel) {
            @Override public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                JComponent c = (JComponent) super.prepareRenderer(r, row, col);
                c.setBackground(row % 2 == 0 ? ROW_A : ROW_B);
                c.setForeground(col == 1 ? ACCENT : new Color(210, 210, 215));
                c.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                return c;
            }
        };
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(34);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.setBackground(ROW_A);
        table.setForeground(new Color(210, 210, 215));
        table.setSelectionBackground(new Color(60, 60, 65));
        table.setSelectionForeground(Color.WHITE);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(45, 45, 50));
        table.getTableHeader().setForeground(new Color(180, 180, 190));
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(60);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        // Double-click to paste result back
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row < 0) return;
                if (e.getClickCount() == 2) {
                    input = (String) tableModel.getValueAt(row, 1);
                    newInput = true;
                    updateDisplay();
                    toggleHistory();
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    table.setRowSelectionInterval(row, row);
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem del = new JMenuItem("Delete this entry");
                    del.addActionListener(ae -> deleteRow(row));
                    menu.add(del);
                    menu.show(table, e.getX(), e.getY());
                }
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 58), 1, true));
        scroll.getVerticalScrollBar().setUI(new DarkScrollUI());
        JLabel dbTag = new JLabel("Persisted in MySQL \u00B7 double-click row to reuse", SwingConstants.CENTER);
        dbTag.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        dbTag.setForeground(new Color(80, 80, 95));
        dbTag.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        p.add(header, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        p.add(dbTag, BorderLayout.SOUTH);
        return p;
    }
    // ═════════════════════════════════════════════ HISTORY TOGGLE ═══════════
    private void toggleHistory() {
        historyOpen = !historyOpen;
        if (historyOpen) {
            loadHistory();
            setSize(320 + 10 + 320, 520);
            setShape(new RoundRectangle2D.Double(0, 0, getWidth(), 520, 24, 24));
            historyPanel.setBounds(328, 0, 320, 520);
        } else {
            setSize(320, 520);
            setShape(new RoundRectangle2D.Double(0, 0, 320, 520, 24, 24));
            historyPanel.setBounds(330, 0, 320, 520);
        }
        revalidate();
        repaint();
    }
    // ═════════════════════════════════════════════════ DATABASE ══════════════
    private void initDB() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
            conn.createStatement().executeUpdate("""
                CREATE TABLE IF NOT EXISTS calc_history (
                    id             INT AUTO_INCREMENT PRIMARY KEY,
                    expression     VARCHAR(255) NOT NULL,
                    result         VARCHAR(100) NOT NULL,
                    calculated_at  DATETIME DEFAULT CURRENT_TIMESTAMP
                )""");
            System.out.println("MySQL connected.");
        } catch (Exception ex) {
            System.err.println("DB not connected: " + ex.getMessage());
            conn = null;
        }
    }
    private void saveRecord(String expr, String result) {
        if (conn == null) return;
        try {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO calc_history (expression, result) VALUES (?,?)");
            ps.setString(1, expr);
            ps.setString(2, result);
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("Save error: " + ex.getMessage());
        }
    }
    private void loadHistory() {
        tableModel.setRowCount(0);
        if (conn == null) {
            tableModel.addRow(new Object[]{"DB not connected", "\u2013", "\u2013"});
            return;
        }
        try {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT expression, result, calculated_at FROM calc_history ORDER BY id DESC LIMIT 300");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss");
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("calculated_at");
                tableModel.addRow(new Object[]{
                    rs.getString("expression"),
                    rs.getString("result"),
                    ts != null ? ts.toLocalDateTime().format(fmt) : "\u2013"
                });
            }
        } catch (SQLException ex) {
            System.err.println("Load error: " + ex.getMessage());
        }
    }
    private void clearAll() {
        if (JOptionPane.showConfirmDialog(this,
                "Delete all history from the database?", "Confirm Clear",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        if (conn != null) try {
            conn.createStatement().executeUpdate("DELETE FROM calc_history");
        } catch (SQLException ex) {
            System.err.println("Clear error: " + ex.getMessage());
        }
        tableModel.setRowCount(0);
    }
    private void deleteRow(int row) {
        String expr = (String) tableModel.getValueAt(row, 0);
        if (conn != null) try {
            PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM calc_history WHERE expression=? LIMIT 1");
            ps.setString(1, expr);
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("Delete error: " + ex.getMessage());
        }
        tableModel.removeRow(row);
    }
    // ═════════════════════════════════════════════ CALC LOGIC ════════════════
    private void handleBtn(String label) {
        switch (label) {
            case "C"       -> clear();
            case "CE"      -> backspace();
            case "+"       -> operate("+");
            case "-"       -> operate("-");
            case "\u00D7"  -> operate("\u00D7");
            case "\u00F7"  -> operate("\u00F7");
            case "="       -> equals();
            case "."       -> dot();
            case "%"       -> percent();
            default        -> digit(label);
        }
    }
    private void digit(String d) {
        if (newInput) { input = ""; newInput = false; }
        input = input.equals("0") ? d : input + d;
        updateDisplay();
    }
    private void dot() {
        if (newInput) { input = "0"; newInput = false; }
        if (!input.contains(".")) input += ".";
        updateDisplay();
    }
    private void operate(String o) {
        if (!input.isEmpty()) {
            if (!op.isEmpty() && !newInput) equals();
            first = parse();
            op = o;
            histText = fmt(first) + " " + o;
            exprLabel.setText(histText);
        } else if (!op.isEmpty()) {
            op = o;
            histText = histText.substring(0, histText.lastIndexOf(" ") + 1) + o;
            exprLabel.setText(histText);
        }
        newInput = true;
    }
    private void equals() {
        if (op.isEmpty() || input.isEmpty()) return;
        double second = parse();
        double res = switch (op) {
            case "+"      -> first + second;
            case "-"      -> first - second;
            case "\u00D7" -> first * second;
            case "\u00F7" -> second != 0 ? first / second : Double.NaN;
            default       -> second;
        };
        String expr = fmt(first) + " " + op + " " + fmt(second);
        String resStr = fmt(res);
        exprLabel.setText(expr + " =");
        input = resStr;
        op = "";
        newInput = true;
        updateDisplay();
        saveRecord(expr, resStr);
        if (historyOpen) loadHistory();
    }
    private void clear() {
        input = "";
        op = "";
        first = 0;
        histText = "";
        exprLabel.setText(" ");
        displayLabel.setText("0");
    }
    private void backspace() {
        if (!input.isEmpty()) {
            input = input.substring(0, input.length() - 1);
            updateDisplay();
        }
    }
    private void percent() {
        if (!input.isEmpty()) {
            input = fmt(parse() / 100.0);
            updateDisplay();
        }
    }
    private double parse() {
        if (input.isEmpty() || input.equals("-")) return 0;
        // Remove commas for parsing
        String clean = input.replace(",", "");
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    private String fmt(double v) {
        if (Double.isNaN(v)) return "Error";
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e12) {
            return COMMA_FMT.format((long) v);
        }
        return COMMA_FMT.format(v);
    }
    private void updateDisplay() {
        String raw = input.isEmpty() ? "0" : input;
        // Format with commas for display
        String display;
        try {
            String clean = raw.replace(",", "");
            if (raw.endsWith(".")) {
                display = COMMA_FMT.format(Long.parseLong(clean.replace(".", ""))) + ".";
            } else if (raw.contains(".")) {
                display = COMMA_FMT.format(Double.parseDouble(clean));
            } else {
                display = COMMA_FMT.format(Long.parseLong(clean));
            }
        } catch (NumberFormatException e) {
            display = raw;
        }
        int sz = display.length() > 12 ? 28 : display.length() > 9 ? 34 : display.length() > 6 ? 40 : 48;
        displayLabel.setFont(new Font("Segoe UI", Font.PLAIN, sz));
        displayLabel.setText(display);
    }
    // ═════════════════════════════════════════════ BUTTON FACTORIES ══════════
    private JButton closeBtn() {
        JButton b = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(255, 80, 80) : new Color(120, 120, 125));
                g2.fillOval(0, 0, getWidth(), getHeight());
                if (getModel().isRollover()) {
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Arial", Font.BOLD, 7));
                    FontMetrics fm = g2.getFontMetrics();
                    String t = "\u2715";
                    g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                }
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(14, 14));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
            try { if (conn != null) conn.close(); } catch (SQLException ignored) {}
            System.exit(0);
        });
        return b;
    }
    private JButton historyBtn() {
        JButton b = new JButton("\u23F0") {
            boolean hov = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                    public void mouseExited(MouseEvent e)  { hov = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hov ? new Color(80, 80, 85) : new Color(65, 65, 70));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(200, 200, 205));
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                FontMetrics fm = g2.getFontMetrics();
                String txt = "\u2630";
                g2.drawString(txt, (getWidth() - fm.stringWidth(txt)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(30, 22));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    private JButton miniBtn(String txt, Color bg, Color hover) {
        JButton b = new JButton(txt) {
            boolean hov = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                    public void mouseExited(MouseEvent e)  { hov = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hov ? hover : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(240, 240, 245));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(80, 22));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    private JButton flatBtn(String label) {
        boolean isEquals = label.equals("=");
        boolean isOp = label.equals("C") || label.equals("CE") ||
                        label.equals("\u00F7") || label.equals("\u00D7") ||
                        label.equals("+") || label.equals("-") || label.equals("%");
        Color norm = isEquals ? BTN_DARK : BTN_LIGHT;
        Color hov  = isEquals ? BTN_DARK_H : BTN_LIGHT_H;
        Color tc   = isEquals ? TXT_WHITE : TXT_DARK;
        JButton b = new JButton(label) {
            boolean h = false;
            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { h = true; repaint(); }
                    public void mouseExited(MouseEvent e)  { h = false; repaint(); }
                });
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = h ? hov : norm;
                if (getModel().isPressed()) {
                    bg = isEquals ? new Color(50, 50, 50) : new Color(200, 200, 200);
                }
                g2.setColor(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Draw grid lines
                g2.setColor(GRID_LINE);
                g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                // Draw text
                g2.setColor(tc);
                int fs = isOp ? 20 : (label.length() > 1 ? 16 : 24);
                g2.setFont(new Font("Segoe UI", Font.PLAIN, fs));
                FontMetrics fm = g2.getFontMetrics();
                // Display lowercase for C and CE
                String displayText = label;
                if (label.equals("C")) displayText = "c";
                else if (label.equals("CE")) displayText = "ce";
                g2.drawString(displayText, (getWidth() - fm.stringWidth(displayText)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> handleBtn(label));
        return b;
    }
    // ═════════════════════════════════════════════ DARK SCROLLBAR ════════════
    static class DarkScrollUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            thumbColor = new Color(80, 80, 90);
            trackColor = new Color(28, 28, 32);
        }
        @Override protected JButton createDecreaseButton(int o) { return zero(); }
        @Override protected JButton createIncreaseButton(int o) { return zero(); }
        private JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }
    }
    // ═════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new Calculator().setVisible(true);
        });
    }
}
