import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.io.PrintStream;

public class ATMApp3 {

    // ====== Domain Models ======
    static class Transaction {
        final Date time = new Date();
        final String description;
        final BigDecimal amount;          // positive for deposit/incoming, negative for withdrawal/outgoing
        final BigDecimal postBalance;

        Transaction(String description, BigDecimal amount, BigDecimal postBalance) {
            this.description = description;
            this.amount = amount;
            this.postBalance = postBalance;
        }

        @Override
        public String toString() {
            NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
            return String.format("%1$tF %1$tT | %-22s | %10s | Bal: %s",
                    time, description, formatAmount(amount), nf.format(postBalance));
        }

        private String formatAmount(BigDecimal a) {
            NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
            String sign = a.signum() >= 0 ? "+" : "-";
            return sign + nf.format(a.abs());
        }
    }

    static class Account {
        final String number;
        final String holder;
        private String pin;
        private BigDecimal balance;
        private final Deque<Transaction> mini = new ArrayDeque<>();

        Account(String number, String holder, String pin, BigDecimal opening) {
            if (!pin.matches("\\d{4}")) throw new IllegalArgumentException("PIN must be 4 digits.");
            this.number = number;
            this.holder = holder;
            this.pin = pin;
            this.balance = opening.setScale(2, RoundingMode.HALF_UP);
            record("Opening Balance", BigDecimal.ZERO);
        }

        boolean verifyPin(String input) {
            return this.pin.equals(input);
        }

        void changePin(String newPin) {
            if (!newPin.matches("\\d{4}")) throw new IllegalArgumentException("PIN must be 4 digits.");
            this.pin = newPin;
        }

        BigDecimal getBalance() {
            return balance;
        }

        void deposit(BigDecimal amount) {
            requirePositive(amount);
            balance = balance.add(amount);
            record("Cash Deposit", amount);
        }

        boolean withdraw(BigDecimal amount) {
            requirePositive(amount);
            if (balance.compareTo(amount) < 0) return false;
            balance = balance.subtract(amount);
            record("Cash Withdrawal", amount.negate());
            return true;
        }

        boolean transferTo(Account other, BigDecimal amount) {
            requirePositive(amount);
            if (this == other || this.number.equals(other.number))
                throw new IllegalArgumentException("Cannot transfer to same account.");
            if (balance.compareTo(amount) < 0) return false;
            balance = balance.subtract(amount);
            record("Transfer to " + other.number, amount.negate());
            other.balance = other.balance.add(amount);
            other.record("Transfer from " + this.number, amount);
            return true;
        }

        List<Transaction> recentTransactions(int n) {
            List<Transaction> out = new ArrayList<>(mini);
            Collections.reverse(out);
            return out.subList(0, Math.min(n, out.size()));
        }

        private void record(String desc, BigDecimal amount) {
            mini.addLast(new Transaction(desc, amount, balance));
            while (mini.size() > 20) mini.removeFirst();
        }

        private void requirePositive(BigDecimal a) {
            if (a == null || a.compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("Amount must be positive.");
        }
    }

    // ====== Data Store (in-memory) ======
    static class Bank {
        private final Map<String, Account> accounts = new HashMap<>();

        void add(Account a) { accounts.put(a.number, a); }

        Account find(String number) { return accounts.get(number); }
    }

    // ====== Main Application ======
    private static final Scanner sc = new Scanner(System.in);
    private static final NumberFormat NF = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    public static void main(String[] args) throws Exception {
        // ✅ Fix: make console output UTF-8 so ₹ symbol displays properly
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));

        Bank bank = seedBank();

        while (true) {
            System.out.println("\n===== Welcome to Java ATM =====");
            System.out.print("Enter Account Number (or 0 to exit): ");
            String accNo = nextToken();
            if ("0".equals(accNo)) {
                System.out.println("Thank you! Goodbye.");
                break;
            }

            Account acc = bank.find(accNo);
            if (acc == null) {
                System.out.println("Account not found.");
                continue;
            }

            if (!authenticate(acc)) {
                System.out.println("Too many failed attempts. Session locked.\n");
                continue;
            }

            boolean logout = false;
            while (!logout) {
                try {
                    System.out.println("\n--- ATM Menu ---");
                    System.out.println("1) Balance Inquiry");
                    System.out.println("2) Deposit");
                    System.out.println("3) Withdraw");
                    System.out.println("4) Transfer");
                    System.out.println("5) Mini Statement (last 10)");
                    System.out.println("6) Change PIN");
                    System.out.println("7) Logout");
                    System.out.print("Choose option: ");
                    int choice = readMenuChoice();

                    switch (choice) {
                        case 1 -> showBalance(acc);
                        case 2 -> doDeposit(acc);
                        case 3 -> doWithdraw(acc);
                        case 4 -> doTransfer(acc, bank);
                        case 5 -> showMini(acc);
                        case 6 -> doChangePin(acc);
                        case 7 -> {
                            System.out.println("Logged out.");
                            logout = true;
                        }
                        default -> System.out.println("Invalid option.");
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e.getMessage());
                    sc.nextLine();
                }
            }
        }
    }

    // ====== Actions ======
    private static void showBalance(Account acc) {
        System.out.println("Current Balance: " + NF.format(acc.getBalance()));
    }

    private static void doDeposit(Account acc) {
        System.out.print("Enter amount to deposit: ");
        BigDecimal amt = nextAmount();
        acc.deposit(amt);
        System.out.println("Deposited " + NF.format(amt) + ". New Balance: " + NF.format(acc.getBalance()));
    }

    private static void doWithdraw(Account acc) {
        System.out.print("Enter amount to withdraw: ");
        BigDecimal amt = nextAmount();
        if (acc.withdraw(amt)) {
            System.out.println("Withdrawn " + NF.format(amt) + ". New Balance: " + NF.format(acc.getBalance()));
        } else {
            System.out.println("Insufficient funds.");
        }
    }

    private static void doTransfer(Account acc, Bank bank) {
        System.out.print("Enter beneficiary account number: ");
        String bno = nextToken();
        Account to = bank.find(bno);
        if (to == null) {
            System.out.println("Beneficiary account not found.");
            return;
        }
        System.out.print("Enter amount to transfer: ");
        BigDecimal amt = nextAmount();
        BigDecimal before = acc.getBalance();
        if (acc.transferTo(to, amt)) {
            System.out.println("Transferred " + NF.format(amt) + " to " + to.number +
                               ". New Balance: " + NF.format(acc.getBalance()));
        } else {
            System.out.println("Insufficient funds. You have " + NF.format(before) +
                               " but tried to send " + NF.format(amt) + ".");
        }
    }

    private static void showMini(Account acc) {
        System.out.println("\n--- Mini Statement (last 10) ---");
        System.out.println("Account: " + acc.number + " | Holder: " + acc.holder +
                           " | Balance: " + NF.format(acc.getBalance()));
        List<Transaction> list = acc.recentTransactions(10);
        if (list.isEmpty()) System.out.println("No transactions yet.");
        else list.forEach(System.out::println);
    }

    private static void doChangePin(Account acc) {
        System.out.print("Enter current PIN: ");
        String old = nextToken();
        if (!acc.verifyPin(old)) {
            System.out.println("Incorrect PIN.");
            return;
        }
        System.out.print("Enter new 4-digit PIN: ");
        String np = nextToken();
        acc.changePin(np);
        System.out.println("PIN changed successfully.");
    }

    private static boolean authenticate(Account acc) {
        int tries = 0;
        while (tries < 3) {
            System.out.print("Enter 4-digit PIN: ");
            String pin = nextToken();
            if (acc.verifyPin(pin)) {
                System.out.println("Login successful. Hello, " + acc.holder + "!");
                return true;
            }
            System.out.println("Incorrect PIN.");
            tries++;
        }
        return false;
    }

    // ====== Helpers ======
    private static Bank seedBank() {
        Bank bank = new Bank();
        bank.add(new Account("1001", "Sushmitha", "1111", bd("2500")));
        bank.add(new Account("1002", "Harshal", "2222", bd("5000")));
        bank.add(new Account("1003", "Sanjana", "3333", bd("1500")));
        bank.add(new Account("1004","Shalini","4444",bd("2000")));
        return bank;
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
    }

    private static String nextToken() {
        return sc.next().trim();
    }

    private static BigDecimal nextAmount() {
        String raw = sc.next().trim();
        try {
            BigDecimal amt = new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
            if (amt.compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("Amount must be greater than 0.");
            return amt;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid amount (e.g., 100 or 100.50).");
        }
    }

    private static int readMenuChoice() {
        while (true) {
            String s = sc.next().trim();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.print("Enter a number (1-7): ");
            }
        }
    }
}


class ATMApplet extends Applet implements ActionListener {
    private ATMApp.Bank bank;
    private ATMApp.Account current;

    private CardLayout cards;
    private Panel root;

    // Login UI
    private TextField tfAcc = new TextField(10);
    private TextField tfPin = new TextField(4);
    private Label lblLoginMsg = new Label(" ");

    // Main UI
    private Label lblWelcome = new Label(" ");
    private Label lblBalance = new Label("Balance: ₹0.00");
    private TextField tfAmount = new TextField(10);
    private TextField tfBeneficiary = new TextField(10);
    private TextArea taMini = new TextArea(8, 40);

    private final NumberFormat NF = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    public void init() {
        setLayout(new BorderLayout());
        setBackground(new Color(245, 245, 245));
        setFont(new Font("Dialog", Font.PLAIN, 13));

        // Build in-memory bank (same seed as console)
        bank = new ATMApp.Bank();
        bank.add(new ATMApp.Account("1001", "Sushmitha", "1111", bd("2500")));
        bank.add(new ATMApp.Account("1002", "Harshal",   "2222", bd("5000")));
        bank.add(new ATMApp.Account("1003", "Sanjana",   "3333", bd("1500")));
        bank.add(new ATMApp.Account("1004", "Shalini",   "4444", bd("2000")));

        cards = new CardLayout();
        root = new Panel(cards);

        root.add(buildLoginPanel(), "login");
        root.add(buildMainPanel(), "main");

        add(root, BorderLayout.CENTER);
        cards.show(root, "login");
    }

    private Panel buildLoginPanel() {
        Panel p = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        Label title = new Label("Java ATM — Applet Login", Label.CENTER);
        title.setFont(new Font("Dialog", Font.BOLD, 16));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL;
        p.add(title, c);

        c.gridwidth = 1; c.fill = GridBagConstraints.HORIZONTAL;

        c.gridy = 1; c.gridx = 0; p.add(new Label("Account No:"), c);
        c.gridx = 1; p.add(tfAcc, c);

        c.gridy = 2; c.gridx = 0; p.add(new Label("PIN:"), c);
        tfPin.setEchoChar('*');
        c.gridx = 1; p.add(tfPin, c);

        Button btnLogin = new Button("Login");
        btnLogin.setActionCommand("login");
        btnLogin.addActionListener(this);
        c.gridy = 3; c.gridx = 0; c.gridwidth = 2;
        p.add(btnLogin, c);

        lblLoginMsg.setForeground(Color.red);
        c.gridy = 4; c.gridx = 0; c.gridwidth = 2;
        p.add(lblLoginMsg, c);

        return p;
    }

    private Panel buildMainPanel() {
        Panel outer = new Panel(new BorderLayout(8, 8));

        // Top bar
        Panel top = new Panel(new GridLayout(2, 1));
        lblWelcome.setFont(new Font("Dialog", Font.BOLD, 14));
        top.add(lblWelcome);
        top.add(lblBalance);
        outer.add(top, BorderLayout.NORTH);

        // Center actions
        Panel center = new Panel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; center.add(new Label("Amount:"), c);
        c.gridx = 1; center.add(tfAmount, c);

        Button btnDeposit = new Button("Deposit");
        btnDeposit.setActionCommand("deposit");
        btnDeposit.addActionListener(this);
        c.gridx = 2; center.add(btnDeposit, c);

        Button btnWithdraw = new Button("Withdraw");
        btnWithdraw.setActionCommand("withdraw");
        btnWithdraw.addActionListener(this);
        c.gridx = 3; center.add(btnWithdraw, c);

        c.gridx = 0; c.gridy = 1; center.add(new Label("Beneficiary Acc:"), c);
        c.gridx = 1; center.add(tfBeneficiary, c);

        Button btnTransfer = new Button("Transfer");
        btnTransfer.setActionCommand("transfer");
        btnTransfer.addActionListener(this);
        c.gridx = 2; center.add(btnTransfer, c);

        Button btnBalance = new Button("Balance");
        btnBalance.setActionCommand("balance");
        btnBalance.addActionListener(this);
        c.gridx = 3; center.add(btnBalance, c);

        Button btnMini = new Button("Mini Statement");
        btnMini.setActionCommand("mini");
        btnMini.addActionListener(this);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 4;
        center.add(btnMini, c);

        Button btnChangePin = new Button("Change PIN");
        btnChangePin.setActionCommand("changepin");
        btnChangePin.addActionListener(this);
        c.gridy = 3; center.add(btnChangePin, c);

        Button btnLogout = new Button("Logout");
        btnLogout.setActionCommand("logout");
        btnLogout.addActionListener(this);
        c.gridy = 4; center.add(btnLogout, c);

        outer.add(center, BorderLayout.CENTER);

        // Mini statement area
        taMini.setEditable(false);
        outer.add(taMini, BorderLayout.SOUTH);

        return outer;
    }

    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        try {
            switch (cmd) {
                case "login" -> doLogin();
                case "deposit" -> {
                    ensureLoggedIn();
                    BigDecimal amt = readAmount();
                    current.deposit(amt);
                    flash("Deposited " + NF.format(amt));
                    refreshBalances();
                }
                case "withdraw" -> {
                    ensureLoggedIn();
                    BigDecimal amt = readAmount();
                    if (current.withdraw(amt)) {
                        flash("Withdrawn " + NF.format(amt));
                    } else {
                        flash("Insufficient funds.");
                    }
                    refreshBalances();
                }
                case "transfer" -> {
                    ensureLoggedIn();
                    String bno = tfBeneficiary.getText().trim();
                    ATMApp.Account to = bank.find(bno);
                    if (to == null) { flash("Beneficiary not found."); return; }
                    BigDecimal amt = readAmount();
                    BigDecimal before = current.getBalance();
                    if (current.transferTo(to, amt)) {
                        flash("Transferred " + NF.format(amt) + " to " + to.number);
                    } else {
                        flash("Insufficient funds. You have " + NF.format(before)
                                + " but tried to send " + NF.format(amt) + ".");
                    }
                    refreshBalances();
                }
                case "balance" -> {
                    ensureLoggedIn();
                    flash("Balance: " + NF.format(current.getBalance()));
                    refreshBalances();
                }
                case "mini" -> {
                    ensureLoggedIn();
                    var list = current.recentTransactions(10);
                    StringBuilder sb = new StringBuilder("--- Mini Statement (last 10) ---\n");
                    for (var t : list) sb.append(t).append('\n');
                    taMini.setText(sb.toString());
                }
                case "changepin" -> {
                    ensureLoggedIn();
                    String old = prompt("Enter current PIN:");
                    if (old == null) return;
                    if (!current.verifyPin(old.trim())) { flash("Incorrect PIN."); return; }
                    String np = prompt("Enter new 4-digit PIN:");
                    if (np == null) return;
                    current.changePin(np.trim());
                    flash("PIN changed successfully.");
                }
                case "logout" -> {
                    current = null;
                    tfAcc.setText("");
                    tfPin.setText("");
                    taMini.setText("");
                    lblLoginMsg.setText("Logged out.");
                    cards.show(root, "login");
                }
            }
        } catch (Exception ex) {
            flash("Error: " + ex.getMessage());
        }
    }

    private void doLogin() {
        String accNo = tfAcc.getText().trim();
        String pin = tfPin.getText().trim();
        ATMApp.Account a = bank.find(accNo);
        if (a == null) {
            lblLoginMsg.setText("Account not found.");
            return;
        }
        if (!a.verifyPin(pin)) {
            lblLoginMsg.setText("Incorrect PIN.");
            return;
        }
        current = a;
        lblWelcome.setText("Hello, " + current.holder + " (Acc " + current.number + ")");
        refreshBalances();
        lblLoginMsg.setText(" ");
        cards.show(root, "main");
    }

    private void ensureLoggedIn() {
        if (current == null) throw new IllegalStateException("Please login first.");
    }

    private BigDecimal readAmount() {
        String raw = tfAmount.getText().trim();
        if (raw.isEmpty()) throw new IllegalArgumentException("Enter an amount.");
        BigDecimal amt = new BigDecimal(raw).setScale(2, RoundingMode.HALF_UP);
        if (amt.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be greater than 0.");
        return amt;
    }

    private void refreshBalances() {
        if (current != null) {
            lblBalance.setText("Balance: " + NF.format(current.getBalance()));
        }
    }

    private void flash(String msg) {
        showStatus(msg); // status bar of Applet
    }

    private String prompt(String message) {
        return java.awt.DialogPassword.prompt(this, message);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
    }
}

/**
 * Small helper for simple input prompts without Swing, purely AWT.
 */
class DialogPassword {
    static String prompt(Applet parent, String message) {
        final Dialog d = new Dialog((Frame) null, "Input", true);
        d.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6,6,6,6);
        c.fill = GridBagConstraints.HORIZONTAL;

        Label lbl = new Label(message);
        TextField tf = new TextField(12);
        Button ok = new Button("OK");
        Button cancel = new Button("Cancel");

        c.gridx=0; c.gridy=0; c.gridwidth=2; d.add(lbl, c);
        c.gridy=1; c.gridwidth=2; d.add(tf, c);

        Panel btns = new Panel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(ok); btns.add(cancel);
        c.gridy=2; c.gridwidth=2; d.add(btns, c);

        final String[] result = new String[1];

        ok.addActionListener(e -> { result[0] = tf.getText(); d.setVisible(false); d.dispose(); });
        cancel.addActionListener(e -> { result[0] = null; d.setVisible(false); d.dispose(); });

        d.pack();
        d.setLocationRelativeTo(null);
        d.setVisible(true);
        return result[0];
    }
}
