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

    // ── Colors (Dark Theme - iOS style) ─────────────────────────────────────
    private static final Color BG             = new Color(0, 0, 0);
    private static final Color BTN_NUM        = new Color(51, 51, 51);
    private static final Color BTN_NUM_H      = new Color(70, 70, 70);
    private static final Color BTN_FUNC       = new Color(165, 165, 165);
    private static final Color BTN_FUNC_H     = new Color(185, 185, 185);
    private static final Color BTN_ORANGE     = new Color(255, 149, 0);
    private static final Color BTN_ORANGE_H   = new Color(255, 179, 64);
    private static final Color TXT_WHITE      = Color.WHITE;
    private static final Color TXT_BLACK      = new Color(0, 0, 0);
    private static final Color EXPR_GRAY      = new Color(140, 140, 140);

    // History panel colors
    private static final Color PANEL_BG  = new Color(20, 20, 22);
    private static final Color ROW_A     = new Color(30, 30, 34);
    private static final Color ROW_B     = new Color(38, 38, 42);
    private static final Color ACCENT    = new Color(255, 149, 0);
    private static final Color BTN_RED   = new Color(200, 55, 55);
    private static final Color BTN_RH    = new Color(230, 80, 80);

    // ── Dimensions ──────────────────────────────────────────────────────────
    private static final int CALC_W = 380;
    private static final int CALC_H = 700;
    private static final int BTN_SIZE = 80;
    private static final int BTN_GAP = 10;
    private static final int SIDE_PAD = 10;

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
        setBackground(new Color(0, 0, 0, 0));

        JLayeredPane root = new JLayeredPane();
        root.setPreferredSize(new Dimension(CALC_W, CALC_H));

        JPanel calc = buildCalcPanel();
        calc.setBounds(0, 0, CALC_W, CALC_H);
        root.add(calc, JLayeredPane.DEFAULT_LAYER);

        historyPanel = buildHistoryPanel();
        historyPanel.setBounds(CALC_W + 10, 0, 320, CALC_H);
        root.add(historyPanel, JLayeredPane.PALETTE_LAYER);

        setContentPane(root);
        pack();
        setLocationRelativeTo(null);
        setShape(new RoundRectangle2D.Double(0, 0, CALC_W, CALC_H, 36, 36));

        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                char c = e.getKeyChar();
                if (Character.isDigit(c))         digit(String.valueOf(c));
                else if (c == '+')                operate("+");
                else if (c == '-')                operate("\u2212");
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
                g2.setColor(BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 36, 36);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, SIDE_PAD, 20, SIDE_PAD));
        p.add(buildDisplay(), BorderLayout.NORTH);
        p.add(buildButtons(), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildDisplay() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(CALC_W, 220));
        p.setBorder(BorderFactory.createEmptyBorder(30, 8, 10, 8));

        // Title bar with close + history button
        JPanel titleBar = new JPanel(new BorderLayout());
        titleBar.setOpaque(false);
        titleBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        titleBar.setAlignmentX(Component.LEFT_ALIGNMENT);

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
                if (drag[0] != null) {
                    Point l = getLocation();
                    setLocation(l.x + e.getX() - drag[0].x, l.y + e.getY() - drag[0].y);
                }
            }
        });

        p.add(titleBar);
        p.add(Box.createVerticalGlue());

        // Expression label
        exprLabel = new JLabel(" ");
        exprLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        exprLabel.setForeground(EXPR_GRAY);
        exprLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        exprLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        exprLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        p.add(exprLabel);

        p.add(Box.createVerticalStrut(4));

        // Result display
        displayLabel = new JLabel("0");
        displayLabel.setFont(new Font("SansSerif", Font.PLAIN, 72));
        displayLabel.setForeground(TXT_WHITE);
        displayLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        displayLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        displayLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        p.add(displayLabel);

        p.add(Box.createVerticalStrut(10));

        return p;
    }

    private JPanel buildButtons() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.CENTER;
        g.insets = new Insets(BTN_GAP / 2, BTN_GAP / 2, BTN_GAP / 2, BTN_GAP / 2);
        g.weightx = 1;
        g.weighty = 1;

        // Row 0: C, +/-, %, div
        g.gridy = 0; g.gridwidth = 1;
        g.gridx = 0; p.add(roundBtn("C", "func"), g);
        g.gridx = 1; p.add(roundBtn("\u00B1", "func"), g);
        g.gridx = 2; p.add(roundBtn("%", "func"), g);
        g.gridx = 3; p.add(roundBtn("\u00F7", "op"), g);

        // Row 1: 7, 8, 9, mul
        g.gridy = 1;
        g.gridx = 0; p.add(roundBtn("7", "num"), g);
        g.gridx = 1; p.add(roundBtn("8", "num"), g);
        g.gridx = 2; p.add(roundBtn("9", "num"), g);
        g.gridx = 3; p.add(roundBtn("\u00D7", "op"), g);

        // Row 2: 4, 5, 6, minus
        g.gridy = 2;
        g.gridx = 0; p.add(roundBtn("4", "num"), g);
        g.gridx = 1; p.add(roundBtn("5", "num"), g);
        g.gridx = 2; p.add(roundBtn("6", "num"), g);
        g.gridx = 3; p.add(roundBtn("\u2212", "op"), g);

        // Row 3: 1, 2, 3, plus
        g.gridy = 3;
        g.gridx = 0; p.add(roundBtn("1", "num"), g);
        g.gridx = 1; p.add(roundBtn("2", "num"), g);
        g.gridx = 2; p.add(roundBtn("3", "num"), g);
        g.gridx = 3; p.add(roundBtn("+", "op"), g);

        // Row 4: 0 (wide), dot, equals
        g.gridy = 4;
        g.gridx = 0; g.gridwidth = 2;
        p.add(wideBtn("0"), g);
        g.gridwidth = 1;
        g.gridx = 2; p.add(roundBtn(".", "num"), g);
        g.gridx = 3; p.add(roundBtn("=", "op"), g);

        return p;
    }

    // ═════════════════════════════════════════════ HISTORY PANEL ════════════
    private JPanel buildHistoryPanel() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PANEL_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(20, 16, 16, 16));

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
            setSize(CALC_W + 10 + 320, CALC_H);
            setShape(new RoundRectangle2D.Double(0, 0, getWidth(), CALC_H, 36, 36));
            historyPanel.setBounds(CALC_W + 8, 0, 320, CALC_H);
        } else {
            setSize(CALC_W, CALC_H);
            setShape(new RoundRectangle2D.Double(0, 0, CALC_W, CALC_H, 36, 36));
            historyPanel.setBounds(CALC_W + 10, 0, 320, CALC_H);
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
            case "\u00B1"  -> toggleSign();
            case "%"       -> percent();
            case "+"       -> operate("+");
            case "\u2212"  -> operate("\u2212");
            case "\u00D7"  -> operate("\u00D7");
            case "\u00F7"  -> operate("\u00F7");
            case "="       -> equals();
            case "."       -> dot();
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
            histText = fmtDisplay(first) + " " + o;
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
            case "\u2212" -> first - second;
            case "\u00D7" -> first * second;
            case "\u00F7" -> second != 0 ? first / second : Double.NaN;
            default       -> second;
        };
        String expr = fmtDisplay(first) + " " + op + " " + fmtDisplay(second);
        String resStr = fmtRaw(res);
        exprLabel.setText(expr + " =");
        input = resStr;
        op = "";
        newInput = true;
        updateDisplay();
        saveRecord(expr, fmtDisplay(res));
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

    private void toggleSign() {
        if (!input.isEmpty() && !input.equals("0")) {
            if (input.startsWith("-")) {
                input = input.substring(1);
            } else {
                input = "-" + input;
            }
            updateDisplay();
        }
    }

    private void percent() {
        if (!input.isEmpty()) {
            input = fmtRaw(parse() / 100.0);
            updateDisplay();
        }
    }

    private double parse() {
        if (input.isEmpty() || input.equals("-")) return 0;
        String clean = input.replace(",", "");
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String fmtRaw(double v) {
        if (Double.isNaN(v)) return "Error";
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e12) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    private String fmtDisplay(double v) {
        if (Double.isNaN(v)) return "Error";
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e12) {
            return COMMA_FMT.format((long) v);
        }
        return COMMA_FMT.format(v);
    }

    private void updateDisplay() {
        String raw = input.isEmpty() ? "0" : input;
        String display;
        try {
            String clean = raw.replace(",", "");
            if (raw.equals("-")) {
                display = "-";
            } else if (raw.endsWith(".")) {
                long intPart = Long.parseLong(clean.replace(".", "").replace("-", ""));
                display = (raw.startsWith("-") ? "-" : "") + COMMA_FMT.format(intPart) + ".";
            } else if (raw.contains(".")) {
                display = COMMA_FMT.format(Double.parseDouble(clean));
            } else {
                display = COMMA_FMT.format(Long.parseLong(clean));
            }
        } catch (NumberFormatException e) {
            display = raw;
        }

        int len = display.length();
        int sz = len > 11 ? 36 : len > 9 ? 44 : len > 7 ? 54 : 72;
        displayLabel.setFont(new Font("Segoe UI", Font.PLAIN, sz));
        displayLabel.setText(display);
    }

    // ═════════════════════════════════════════════ BUTTON FACTORIES ══════════

    private JButton closeBtn() {
        JButton b = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(255, 80, 80) : new Color(100, 100, 105));
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
        JButton b = new JButton() {
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
                g2.setColor(hov ? new Color(60, 60, 65) : new Color(40, 40, 45));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(180, 180, 185));
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.drawLine(cx - 6, cy - 4, cx + 6, cy - 4);
                g2.drawLine(cx - 6, cy,     cx + 6, cy);
                g2.drawLine(cx - 6, cy + 4, cx + 6, cy + 4);
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
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
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

    /** Circular button for numbers, operators, and functions */
    private JButton roundBtn(String label, String type) {
        Color norm, hov, tc;
        switch (type) {
            case "op" -> {
                norm = BTN_ORANGE;
                hov = BTN_ORANGE_H;
                tc = TXT_WHITE;
            }
            case "func" -> {
                norm = BTN_FUNC;
                hov = BTN_FUNC_H;
                tc = TXT_BLACK;
            }
            default -> {
                norm = BTN_NUM;
                hov = BTN_NUM_H;
                tc = TXT_WHITE;
            }
        }

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
                    float[] hsb = Color.RGBtoHSB(bg.getRed(), bg.getGreen(), bg.getBlue(), null);
                    bg = Color.getHSBColor(hsb[0], hsb[1], hsb[2] * 0.75f);
                }
                int d = Math.min(getWidth(), getHeight());
                int x = (getWidth() - d) / 2;
                int y = (getHeight() - d) / 2;
                g2.setColor(bg);
                g2.fillOval(x, y, d, d);

                g2.setColor(tc);
                boolean isSymbol = label.equals("\u00F7") || label.equals("\u00D7") ||
                                   label.equals("\u2212") || label.equals("+") ||
                                   label.equals("=") || label.equals("\u00B1");
                int fs = isSymbol ? 30 : (label.length() > 1 ? 22 : 30);
                g2.setFont(new Font("SansSerif", Font.PLAIN, fs));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, (getWidth() - fm.stringWidth(label)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() {
                return new Dimension(BTN_SIZE, BTN_SIZE);
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> handleBtn(label));
        return b;
    }

    /** Wide pill-shaped button for "0" */
    private JButton wideBtn(String label) {
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
                Color bg = h ? BTN_NUM_H : BTN_NUM;
                if (getModel().isPressed()) {
                    float[] hsb = Color.RGBtoHSB(bg.getRed(), bg.getGreen(), bg.getBlue(), null);
                    bg = Color.getHSBColor(hsb[0], hsb[1], hsb[2] * 0.75f);
                }
                int ht = getHeight();
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), ht, ht, ht);

                g2.setColor(TXT_WHITE);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 30));
                FontMetrics fm = g2.getFontMetrics();
                int textX = ht / 2;
                g2.drawString(label, textX,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() {
                return new Dimension(BTN_SIZE * 2 + BTN_GAP, BTN_SIZE);
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
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
