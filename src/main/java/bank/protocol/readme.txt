This package contains java implementations of the protocol specified here:

    https://github.com/mikenoethiger/bank-server-socket#protocol

These implementations were created in the hope that they can be used across
multiple server implementation to reduce the repetitive task of reading,
validating and processing a request to the bank.

Of course the applicability of these classes depend strongly on the server design
and how good this server design can work with the Request and Response object
provided here.

I found it most convenient to just copy the source code to the different code
bases; this way the implementations can be adjusted beyond inheritance to best
fit the server model.