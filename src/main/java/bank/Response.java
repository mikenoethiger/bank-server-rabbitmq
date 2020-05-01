package bank;

import java.util.Arrays;

/**
 * Response according to https://github.com/mikenoethiger/bank-server-socket#response
 */
public class Response {

    /* response documentation can be found here https://github.com/mikenoethiger/bank-server-socket#actions */
    private final int statusCode; /* https://github.com/mikenoethiger/bank-server-socket#status-codes */
    private final String[] data;

    public Response(int statusCode, String[] data) {
        this.statusCode = statusCode;
        this.data = data;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String[] getData() {
        return data;
    }

    @Override
    public String toString() {
        return "Response{" +
                "statusCode=" + statusCode +
                ", data=" + Arrays.toString(data) +
                '}';
    }
}
