[protocol](https://github.com/mikenoethiger/bank-server-socket#protocol) | [bank-client](https://github.com/mikenoethiger/bank-client) | [bank-server-socket](https://github.com/mikenoethiger/bank-server-socket) | [bank-server-graphql](https://github.com/mikenoethiger/bank-server-graphql) | [bank-server-rabbitmq](https://github.com/mikenoethiger/bank-server-rabbitmq)

# About

RabbitMQ implementation of the bank server backend. The client counterpart can be found [here](https://github.com/mikenoethiger/bank-client/tree/master/src/main/java/bank/rabbitmq).

# Prerequisites

A RabbitMQ server is required, which runs standalone rather than embedded in this program. See [here](https://www.rabbitmq.com/download.html) for installation instructions. The easiest way is to start a docker container:

```bash
$ docker run -it --rm --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

You can check the RabbitMQ management UI in your browser at `localhost:15672`. Default User/Pass is `guest`/`guest`.

> **Notice:** Depending on where you run RabbitMQ, you have to adjust the connection credentials in `bank.Server` (constants at the top.) 
> However, if you use the standard installation on localhost, the default credentials should already be fine.

# Run

From your IDE, run `bank.Server` as a java application.

Or with gradle:

```bash
$ gradle run
```

# Implementation Notices

* Requests are accepted through the `bank.requests` queue and the response is published to the `replyTo` queue declared in the request message. The Client has to take care about request/response synchronization, since the `bank.requests` and `replyTo` queues are decoupled. This is implemented on the client side by creating an empty java `BlockingQueue` for each request, which receives the response from the `replyTo` queue as soon as available (see [MqBankDriver.sendRequest()](https://github.com/mikenoethiger/bank-client/blob/master/src/main/java/bank/rabbitmq/MqBankDriver.java)) 
* Account updates are published to the `bank.updates` fanout exchange. Clients can subscribe to this exchange to receive account updates, thus being notified about changes triggered by other clients.  