package bank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Start RabbitMQ server:
 * docker run -it --rm --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
 * User/Pass: guest/guest
 */
public class Server {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5672;
    private static final String DEFAULT_USER = "guest";
    private static final String DEFAULT_PASS = "guest";
    private static final String DEFAULT_VIRTUAL_HOST = "/";

    public static void main(String[] argv) throws IOException, TimeoutException {
        new Server().start();
    }

    static final String RPC_QUEUE_NAME = "bank.requests";
    static final String UPDATES_EXCHANGE_NAME = "bank.updates";
    static final int STATUS_OK = 0;

    /* errors according https://github.com/mikenoethiger/bank-server#status-codes */
    private static final Response ERROR_ACCOUNT_DOES_NOT_EXIST = new Response(1, new String[]{"Account does not exist."});
    private static final Response ERROR_ACCOUNT_COULD_NOT_BE_CREATED = new Response(2, new String[]{"Account could not be created."});
    private static final Response ERROR_ACCOUNT_COULD_NOT_BE_CLOSED = new Response(3, new String[]{"Account could not be closed."});
    private static final Response ERROR_INACTIVE_ACCOUNT = new Response(4, new String[]{"Inactive account."});
    private static final Response ERROR_ACCOUNT_OVERDRAW = new Response(5, new String[]{"Account overdraw."});
    private static final Response ERROR_ILLEGAL_ARGUMENT = new Response(6, new String[]{"Illegal argument."});
    private static final Response ERROR_BAD_REQUEST = new Response(7, new String[]{"Bad request."});
    private static final Response ERROR_INTERNAL_ERROR = new Response(8, new String[]{"Internal error."});

    private final Bank bank = new ServerBank();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Connection connection;
    private Channel channel;

    public void start() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(DEFAULT_HOST);
        factory.setPort(DEFAULT_PORT);
        factory.setUsername(DEFAULT_USER);
        factory.setPassword(DEFAULT_PASS);
        factory.setVirtualHost(DEFAULT_VIRTUAL_HOST);


        connection = factory.newConnection();
        channel = connection.createChannel();

        /* server queue used to serve clients requests */
        channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null);
        /* updates exchange used to broadcast account updates */
        channel.exchangeDeclare(UPDATES_EXCHANGE_NAME, "fanout");

//		channel.basicQos(1);
        boolean autoAck = true;

        System.out.println(" [x] Awaiting RPC requests");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            AMQP.BasicProperties replyProps = new AMQP.BasicProperties.Builder()
                    .correlationId(delivery.getProperties().getCorrelationId()).build();

            Response response = ERROR_INTERNAL_ERROR;

            try {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                response = processRequest(message);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                String responseJson = gson.toJson(response);
                channel.basicPublish("", delivery.getProperties().getReplyTo(), replyProps, responseJson.getBytes("UTF-8"));
//				channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };
        channel.basicConsume(RPC_QUEUE_NAME, autoAck, deliverCallback, (consumerTag -> {
        }));
    }

    private void publishUpdate(String accountNumber) throws IOException {
        channel.basicPublish(UPDATES_EXCHANGE_NAME, "", null, accountNumber.getBytes(StandardCharsets.UTF_8));
        System.out.println(String.format("Update for %s sent", accountNumber));
    }

    private Response processRequest(String message) throws IOException {
        Request request = gson.fromJson(message, Request.class);

        switch (request.getActionId()) {
            case 1:
                return getAccountNumbers(request);
            case 2:
                return getAccount(request);
            case 3:
                return createAccount(request);
            case 4:
                return closeAccount(request);
            case 5:
                return transfer(request);
            case 6:
            case 7:
                return transaction(request);
            default:
                return ERROR_BAD_REQUEST;
        }
    }

    /* https://github.com/mikenoethiger/bank-server-socket#get-account-numbers-1 */
    private Response getAccountNumbers(Request request) throws IOException {
        Set<String> accounts = bank.getAccountNumbers();
        String[] accs = accounts.toArray(new String[accounts.size()]);
        return new Response(STATUS_OK, accs);
    }

    /* https://github.com/mikenoethiger/bank-server-socket#get-account-2 */
    private Response getAccount(Request request) throws IOException {
        Account acc = bank.getAccount(request.getArgs()[0]);

        if (acc == null) return ERROR_ACCOUNT_DOES_NOT_EXIST;

        return new Response(STATUS_OK, new String[]{acc.getNumber(), acc.getOwner(), String.valueOf(acc.getBalance()), acc.isActive() ? "1" : "0"});
    }

    /* https://github.com/mikenoethiger/bank-server-socket/blob/master/readme.md#create-account-3 */
    private Response createAccount(Request request) throws IOException {
        if (request.getArgs().length < 1) return ERROR_BAD_REQUEST;

        String number = bank.createAccount(request.getArgs()[0]);
        if (number == null) return ERROR_ACCOUNT_COULD_NOT_BE_CREATED;

        Account acc = bank.getAccount(number);
        publishUpdate(acc.getNumber());
        return new Response(STATUS_OK, new String[]{acc.getNumber(), acc.getOwner(), String.valueOf(acc.getBalance()), acc.isActive() ? "1" : "0"});
    }

    /* https://github.com/mikenoethiger/bank-server-socket/blob/master/readme.md#close-account-4 */
    private Response closeAccount(Request request) throws IOException {
        if (request.getArgs().length < 1) return ERROR_BAD_REQUEST;

        String number = request.getArgs()[0];
        boolean success = bank.closeAccount(number);

        if (!success) return ERROR_ACCOUNT_COULD_NOT_BE_CLOSED;
        publishUpdate(number);
        return new Response(STATUS_OK, new String[]{});
    }

    /* https://github.com/mikenoethiger/bank-server-socket/blob/master/readme.md#transfer-5 */
    private Response transfer(Request request) throws IOException {
        if (request.getArgs().length < 3) return ERROR_BAD_REQUEST;

        String from = request.getArgs()[0];
        String to = request.getArgs()[1];
        double amount;
        try {
            amount = Double.parseDouble(request.getArgs()[2]);
        } catch (NumberFormatException e) {
            return ERROR_BAD_REQUEST;
        }

        try {
            bank.transfer(bank.getAccount(from), bank.getAccount(to), amount);
        } catch (OverdrawException e) {
            return ERROR_ACCOUNT_OVERDRAW;
        } catch (InactiveException e) {
            return ERROR_INACTIVE_ACCOUNT;
        } catch (IllegalArgumentException e) {
            return ERROR_ILLEGAL_ARGUMENT;
        }

        publishUpdate(from);
        publishUpdate(to);
        return new Response(STATUS_OK, new String[]{String.valueOf(bank.getAccount(from).getBalance()), String.valueOf(bank.getAccount(to).getBalance())});
    }

    /* Generic transaction handler (deposit and withdraw) */
    /* https://github.com/mikenoethiger/bank-server-socket/blob/master/readme.md#deposit-6 */
    /* https://github.com/mikenoethiger/bank-server-socket/blob/master/readme.md#withdraw-7 */
    private Response transaction(Request request) throws IOException {
        if (request.getArgs().length < 2) return ERROR_BAD_REQUEST;

        String number = request.getArgs()[0];
        double amount;
        try {
            amount = Double.parseDouble(request.getArgs()[1]);
        } catch (NumberFormatException e) {
            return ERROR_BAD_REQUEST;
        }
        Account acc = bank.getAccount(number);
        if (acc == null) return ERROR_ACCOUNT_DOES_NOT_EXIST;

        try {
            /* actionId 6: deposit, actionId 7: withdraw */
            if (request.getActionId() == 6) acc.deposit(amount);
            else acc.withdraw(amount);
        } catch (OverdrawException e) {
            return ERROR_ACCOUNT_OVERDRAW;
        } catch (InactiveException e) {
            return ERROR_INACTIVE_ACCOUNT;
        } catch (IllegalArgumentException e) {
            return ERROR_ILLEGAL_ARGUMENT;
        }

        publishUpdate(number);
        return new Response(STATUS_OK, new String[]{String.valueOf(acc.getBalance())});
    }
}
