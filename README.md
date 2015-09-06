# CS378-Watson
Basic Java and Python implementations of a RESTful client to interact with IBM's Watson API.

## Use
The Watson API uses basic authentication for all HTTP requests. Thus, each implementation requires
that a username and password be provided. This information is then Base-64 encoded and sent with the
rest of the HTTP request. Watson's response is then returned to the user.
