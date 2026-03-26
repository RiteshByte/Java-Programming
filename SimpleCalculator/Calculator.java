import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.sql.*;
import java.time.format.DateTimeFormatter;

public class Calculator extends JFrame {

    // ── DB CONFIG ── change these to match your MySQL setup ──────────────────
    private static final String DB_URL  = "jdbc:mysql://localhost:3306/calculator_db";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "10109";

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final Color BG            = new Color(28, 28, 30);
    private static final Color BTN_DARK      = new Color(58, 58, 60);
    private static final Color BTN_LIGHT     = new Color(210, 210, 215);
    private static final Color BTN_ORANGE    = new Color(255, 159, 10);
    private static final Color BTN_DH        = new Color(82, 82, 86);
    private static final Color BTN_LH        = new Color(232, 232, 238);
    private static final Color BTN_OH        = new Color(255, 185, 60);
    private static final Color BTN_RED       = new Color(200, 55, 55);
    private static final Color BTN_RH        = new Color(230, 80, 80);
    private static final Color TXT_D         = new Color(28, 28, 30);
    private static final Color TXT_L         = Color.WHITE;
    private static final Color PANEL_BG      = new Color(20, 20, 22);
    private static final Color ROW_A         = new Color(30, 30, 34);
    private static final Color ROW_B         = new Color(38, 38, 42);
    private static final Color ACCENT        = new Color(255, 159, 10);

    // ── State ─────────────────────────────────────────────────────────────────
    private JLabel displayLabel, histLabel;
    private String input = "", op = "", histText = "";
    private double first = 0;
    private boolean newInput = false;

    // ── History ───────────────────────────────────────────────────────────────
    private JPanel historyPanel;
    private DefaultTableModel tableModel;
    private boolean historyOpen = false;
    private Connection conn;

    // ─────────────────────────────────────────────────────────────────────────
    public Calculator() {
        initDB();

        setTitle("Calculator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setUndecorated(true);
        setResizable(false);

        JLayeredPane root = new JLayeredPane();
        root.setPreferredSize(new Dimension(380, 640));

        JPanel calc = buildCalcPanel();
        calc.setBounds(0, 0, 380, 640);
        root.add(calc, JLayeredPane.DEFAULT_LAYER);

        historyPanel = buildHistoryPanel();
        historyPanel.setBounds(390, 0, 340, 640);
        root.add(historyPanel, JLayeredPane.PALETTE_LAYER);

        setContentPane(root);
        pack();
        setLocationRelativeTo(null);
        setShape(new RoundRectangle2D.Double(0, 0, 380, 640, 28, 28));

        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                char c = e.getKeyChar();
                if (Character.isDigit(c))         digit(String.valueOf(c));
                else if (c == '+')                operate("+");
                else if (c == '-')                operate("-");
                else if (c == '*')                operate("×");
                else if (c == '/')                operate("÷");
                else if (c == '.' || c == ',')    dot();
                else if (c == '\n' || c == '=')   Calculator.this.equals();
                else if (c == 'c' || c == 'C')    clear();
                else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) backspace();
            }
        });
    }

    // ═════════════════════════════════════════════════ CALC PANEL ═══════════
    private JPanel buildCalcPanel() {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG); g2.fillRoundRect(0, 0, getWidth(), getHeight(), 28, 28);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        p.add(buildTitleBar(),   BorderLayout.NORTH);
        p.add(buildDisplay(),    BorderLayout.CENTER);

        // wrap buttons so CENTER works properly
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(buildButtons(), BorderLayout.CENTER);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(0,0,6,0));

        JButton close = closeBtn();
        JPanel lft = new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
        lft.setOpaque(false); lft.add(close);

        JLabel title = new JLabel("Calculator");
        title.setFont(new Font("Segoe UI", Font.BOLD, 13));
        title.setForeground(new Color(180,180,185));
        title.setHorizontalAlignment(SwingConstants.CENTER);

        JButton hBtn = miniBtn("⏱ History", new Color(50,50,54), new Color(68,68,72));
        hBtn.addActionListener(e -> toggleHistory());
        JPanel rgt = new JPanel(new FlowLayout(FlowLayout.RIGHT,0,0));
        rgt.setOpaque(false); rgt.add(hBtn);

        bar.add(lft,   BorderLayout.WEST);
        bar.add(title, BorderLayout.CENTER);
        bar.add(rgt,   BorderLayout.EAST);

        final Point[] drag = {null};
        bar.addMouseListener(new MouseAdapter(){
            public void mousePressed(MouseEvent e){ drag[0]=e.getPoint(); }
        });
        bar.addMouseMotionListener(new MouseMotionAdapter(){
            public void mouseDragged(MouseEvent e){
                if(drag[0]!=null){
                    Point l=getLocation();
                    setLocation(l.x+e.getX()-drag[0].x, l.y+e.getY()-drag[0].y);
                }
            }
        });
        return bar;
    }

    private JPanel buildDisplay() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(10,10,14,10));

        histLabel = new JLabel(" ");
        histLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        histLabel.setForeground(new Color(130,130,140));
        histLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        displayLabel = new JLabel("0");
        displayLabel.setFont(new Font("Segoe UI", Font.PLAIN, 64));
        displayLabel.setForeground(TXT_L);
        displayLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        p.add(histLabel,    BorderLayout.NORTH);
        p.add(displayLabel, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildButtons() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.fill=GridBagConstraints.BOTH; g.insets=new Insets(5,5,5,5);
        g.weightx=1; g.weighty=1;

        String[] r1={"C","÷","×","CE"}, r2={"7","8","9","-"},
                 r3={"4","5","6","+"}, r4={"1","2","3"}, r5={"%","0","."};

        for(int c=0;c<4;c++){g.gridx=c;g.gridy=0;g.gridwidth=1;g.gridheight=1;p.add(btn(r1[c]),g);}
        for(int c=0;c<4;c++){g.gridx=c;g.gridy=1;p.add(btn(r2[c]),g);}
        for(int c=0;c<4;c++){g.gridx=c;g.gridy=2;p.add(btn(r3[c]),g);}
        for(int c=0;c<3;c++){g.gridx=c;g.gridy=3;g.gridheight=1;p.add(btn(r4[c]),g);}
        g.gridx=3;g.gridy=3;g.gridheight=2; p.add(btn("="),g); g.gridheight=1;
        for(int c=0;c<3;c++){g.gridx=c;g.gridy=4;p.add(btn(r5[c]),g);}
        return p;
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
        p.setBorder(BorderFactory.createEmptyBorder(20,16,16,16));

        // Header row
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0,0,12,0));

        JLabel title = new JLabel("History");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);

        JButton clearAll = miniBtn("🗑 Clear All", BTN_RED, BTN_RH);
        clearAll.setPreferredSize(new Dimension(90,26));
        clearAll.addActionListener(e -> clearAll());

        header.add(title,    BorderLayout.WEST);
        header.add(clearAll, BorderLayout.EAST);

        // Table
        tableModel = new DefaultTableModel(new String[]{"Expression","Result","Time"}, 0) {
            @Override public boolean isCellEditable(int r, int c){ return false; }
        };

        JTable table = new JTable(tableModel) {
            @Override public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                JComponent c = (JComponent) super.prepareRenderer(r, row, col);
                c.setBackground(row%2==0 ? ROW_A : ROW_B);
                c.setForeground(col==1 ? ACCENT : new Color(210,210,215));
                c.setBorder(BorderFactory.createEmptyBorder(6,10,6,10));
                return c;
            }
        };
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(34);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0,2));
        table.setBackground(ROW_A);
        table.setForeground(new Color(210,210,215));
        table.setSelectionBackground(new Color(60,60,65));
        table.setSelectionForeground(Color.WHITE);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(45,45,50));
        table.getTableHeader().setForeground(new Color(180,180,190));
        table.getColumnModel().getColumn(0).setPreferredWidth(130);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(110);

        // Double-click → paste result back to calc
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
                // Right-click → delete row
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
        scroll.setBorder(BorderFactory.createLineBorder(new Color(50,50,58), 1, true));
        scroll.getVerticalScrollBar().setUI(new DarkScrollUI());

        JLabel dbTag = new JLabel("💾 Persisted in MySQL · double-click row to reuse", SwingConstants.CENTER);
        dbTag.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        dbTag.setForeground(new Color(80,80,95));
        dbTag.setBorder(BorderFactory.createEmptyBorder(8,0,0,0));

        p.add(header, BorderLayout.NORTH);
        p.add(scroll,  BorderLayout.CENTER);
        p.add(dbTag,   BorderLayout.SOUTH);
        return p;
    }

    // ═════════════════════════════════════════════ HISTORY TOGGLE ═══════════
    private void toggleHistory() {
        historyOpen = !historyOpen;
        if (historyOpen) {
            loadHistory();
            setSize(380 + 8 + 340, 640);
            setShape(new RoundRectangle2D.Double(0,0,getWidth(),640,28,28));
            historyPanel.setBounds(388, 0, 340, 640);
        } else {
            setSize(380, 640);
            setShape(new RoundRectangle2D.Double(0,0,380,640,28,28));
            historyPanel.setBounds(390, 0, 340, 640);
        }
        revalidate(); repaint();
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
            System.out.println("✅ MySQL connected.");
        } catch (Exception ex) {
            System.err.println("⚠ DB not connected: " + ex.getMessage());
            conn = null;
        }
    }

    private void saveRecord(String expr, String result) {
        if (conn == null) return;
        try {
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO calc_history (expression, result) VALUES (?,?)");
            ps.setString(1, expr); ps.setString(2, result);
            ps.executeUpdate();
        } catch (SQLException ex) { System.err.println("Save error: "+ex.getMessage()); }
    }

    private void loadHistory() {
        tableModel.setRowCount(0);
        if (conn == null) {
            tableModel.addRow(new Object[]{"DB not connected", "–", "–"});
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
                    ts != null ? ts.toLocalDateTime().format(fmt) : "–"
                });
            }
        } catch (SQLException ex) { System.err.println("Load error: "+ex.getMessage()); }
    }

    private void clearAll() {
        if (JOptionPane.showConfirmDialog(this,
                "Delete all history from the database?", "Confirm Clear",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        if (conn != null) try {
            conn.createStatement().executeUpdate("DELETE FROM calc_history");
        } catch (SQLException ex) { System.err.println("Clear error: "+ex.getMessage()); }
        tableModel.setRowCount(0);
    }

    private void deleteRow(int row) {
        String expr = (String) tableModel.getValueAt(row, 0);
        if (conn != null) try {
            PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM calc_history WHERE expression=? LIMIT 1");
            ps.setString(1, expr); ps.executeUpdate();
        } catch (SQLException ex) { System.err.println("Delete error: "+ex.getMessage()); }
        tableModel.removeRow(row);
    }

    // ═════════════════════════════════════════════ CALC LOGIC ════════════════
    private void handleBtn(String label) {
        switch (label) {
            case "C"  -> clear();
            case "CE" -> backspace();
            case "+"  -> operate("+");
            case "-"  -> operate("-");
            case "×"  -> operate("×");
            case "÷"  -> operate("÷");
            case "="  -> equals();
            case "."  -> dot();
            case "%"  -> percent();
            default   -> digit(label);
        }
    }

    private void digit(String d) {
        if (newInput) { input=""; newInput=false; }
        input = input.equals("0") ? d : input+d;
        updateDisplay();
    }
    private void dot() {
        if (newInput) { input="0"; newInput=false; }
        if (!input.contains(".")) input+=".";
        updateDisplay();
    }
    private void operate(String o) {
        if (!input.isEmpty()) {
            if (!op.isEmpty() && !newInput) equals();
            first=parse(); op=o;
            histText=fmt(first)+" "+o;
            histLabel.setText(histText);
        } else if (!op.isEmpty()) {
            op=o;
            histText=histText.substring(0,histText.lastIndexOf(" ")+1)+o;
            histLabel.setText(histText);
        }
        newInput=true;
    }
    private void equals() {
        if (op.isEmpty()||input.isEmpty()) return;
        double second=parse();
        double res = switch(op){
            case "+" -> first+second;
            case "-" -> first-second;
            case "×" -> first*second;
            case "÷" -> second!=0 ? first/second : Double.NaN;
            default  -> second;
        };
        String expr  = fmt(first)+" "+op+" "+fmt(second);
        String resStr= fmt(res);
        histLabel.setText(expr+" =");
        input=resStr; op=""; newInput=true;
        updateDisplay();
        saveRecord(expr, resStr);
        if (historyOpen) loadHistory();
    }
    private void clear()    { input="";op="";first=0;histText="";histLabel.setText(" ");displayLabel.setText("0"); }
    private void backspace(){ if(!input.isEmpty()){ input=input.substring(0,input.length()-1); updateDisplay(); } }
    private void percent()  { if(!input.isEmpty()){ input=fmt(parse()/100.0); updateDisplay(); } }
    private double parse()  {
        if(input.isEmpty()||input.equals("-")) return 0;
        try{ return Double.parseDouble(input); }catch(NumberFormatException e){ return 0; }
    }
    private String fmt(double v) {
        if(Double.isNaN(v)) return "Error";
        if(v==Math.floor(v)&&!Double.isInfinite(v)&&Math.abs(v)<1e12) return String.valueOf((long)v);
        return String.valueOf(v);
    }
    private void updateDisplay() {
        String t=input.isEmpty()?"0":input;
        int sz=t.length()>10?32:t.length()>7?44:64;
        displayLabel.setFont(new Font("Segoe UI",Font.PLAIN,sz));
        displayLabel.setText(t);
    }

    // ═════════════════════════════════════════════ BUTTON FACTORIES ══════════
    private JButton closeBtn() {
        JButton b = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover()?new Color(255,80,80):new Color(80,80,85));
                g2.fillOval(0,0,getWidth(),getHeight());
                if(getModel().isRollover()){
                    g2.setColor(Color.WHITE); g2.setFont(new Font("Arial",Font.BOLD,7));
                    FontMetrics fm=g2.getFontMetrics(); String t="✕";
                    g2.drawString(t,(getWidth()-fm.stringWidth(t))/2,(getHeight()+fm.getAscent()-fm.getDescent())/2);
                }
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(14,14));
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
            try{ if(conn!=null) conn.close(); }catch(SQLException ignored){}
            System.exit(0);
        });
        return b;
    }

    private JButton miniBtn(String txt, Color bg, Color hover) {
        JButton b = new JButton(txt) {
            boolean hov=false;
            { addMouseListener(new MouseAdapter(){
                public void mouseEntered(MouseEvent e){hov=true;repaint();}
                public void mouseExited (MouseEvent e){hov=false;repaint();}
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hov?hover:bg); g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                g2.setColor(new Color(210,210,215)); g2.setFont(new Font("Segoe UI",Font.PLAIN,11));
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString(getText(),(getWidth()-fm.stringWidth(getText()))/2,
                              (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(80,22));
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JButton btn(String label) {
        boolean isOrange = label.equals("=");
        boolean isLight  = label.equals("C")||label.equals("CE")||label.equals("÷")||label.equals("×");
        Color norm = isOrange?BTN_ORANGE:(isLight?BTN_LIGHT:BTN_DARK);
        Color hov  = isOrange?BTN_OH:(isLight?BTN_LH:BTN_DH);
        Color tc   = isLight?TXT_D:TXT_L;

        JButton b = new JButton(label) {
            boolean h=false;
            { addMouseListener(new MouseAdapter(){
                public void mouseEntered(MouseEvent e){h=true;repaint();}
                public void mouseExited (MouseEvent e){h=false;repaint();}
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2=(Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg=h?hov:norm;
                if(getModel().isPressed()){
                    float[] hsb=Color.RGBtoHSB(bg.getRed(),bg.getGreen(),bg.getBlue(),null);
                    bg=Color.getHSBColor(hsb[0],hsb[1],hsb[2]*0.80f);
                }
                g2.setColor(bg); g2.fillRoundRect(0,0,getWidth(),getHeight(),18,18);
                g2.setColor(tc);
                int fs=label.length()>1?18:26;
                g2.setFont(new Font("Segoe UI",Font.PLAIN,fs));
                FontMetrics fm=g2.getFontMetrics();
                g2.drawString(label,(getWidth()-fm.stringWidth(label))/2,
                              (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> handleBtn(label));
        return b;
    }

    // ═════════════════════════════════════════════ DARK SCROLLBAR ════════════
    static class DarkScrollUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors(){
            thumbColor=new Color(80,80,90); trackColor=new Color(28,28,32);
        }
        @Override protected JButton createDecreaseButton(int o){ return zero(); }
        @Override protected JButton createIncreaseButton(int o){ return zero(); }
        private JButton zero(){ JButton b=new JButton(); b.setPreferredSize(new Dimension(0,0)); return b; }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r){
            Graphics2D g2=(Graphics2D)g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x+2,r.y+2,r.width-4,r.height-4,8,8);
            g2.dispose();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try{ UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch(Exception ignored){}
            new Calculator().setVisible(true);
        });
    }
}
