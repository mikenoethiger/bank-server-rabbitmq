package bank;

import java.io.IOException;
import java.util.Date;

/**
 * Server side Account implementation.
 *
 * Use it in your server implementations.
 * (Or copy it to your server implementation code base.)
 */
public class ServerAccount implements Account {

    private static final String IBAN_PREFIX = "CH56";
    private static final Object LOCK = new Object();
    private static long next_account_number = 1000_0000_0000_0000_0L;

    private String number;
    private String owner;
    private double balance;
    private boolean active = true;

    private Date lastModified;

    public ServerAccount() {}

    public ServerAccount(String owner) {
        this.owner = owner;
        synchronized (LOCK) {
            this.number = IBAN_PREFIX + next_account_number++;
        }
        this.balance = 0;
        lastModified = new Date();
    }

    @Override
    public double getBalance() {
        return balance;
    }

    @Override
    public String getOwner() {
        return owner;
    }

    @Override
    public String getNumber() {
        return number;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void deposit(double amount) throws IOException, InactiveException {
        if (!isActive()) throw new InactiveException();
        if (amount < 0) throw new IllegalArgumentException("negative amount not allowed");
        balance += amount;
        lastModified = new Date();
    }

    @Override
    public void withdraw(double amount) throws IOException, InactiveException, OverdrawException {
        if (amount < 0) throw new IllegalArgumentException("negative amount not allowed");
        if (amount > balance) throw new OverdrawException();
        if (!isActive()) throw new InactiveException();
        balance -= amount;
        lastModified = new Date();
    }

    public void setBalance(double balance) {
        this.balance = balance;
        lastModified = new Date();
    }

    void makeInactive() {
        active = false;
        lastModified = new Date();
    }

    @Override
    public String toString() {
        return "ServerAccount{" +
                "number='" + number + '\'' +
                ", owner='" + owner + '\'' +
                ", balance=" + balance +
                ", active=" + active +
                ", lastModified=" + lastModified +
                '}';
    }
}
