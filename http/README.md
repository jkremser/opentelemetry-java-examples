# HTTP Example

**Note:** This is an advanced scenario useful for people that want to *manually* instrument their own code. 

This is a simple example that demonstrates how to use the OpenTelemetry SDK 
to *manually* instrument a simple HTTP based Client/Server application. 
The example creates the **Root Span** on the client and sends the context
over the HTTP request. On the server side, the example shows how to extract the context
and create a **Child Span** with attached a **Span Event**. 

# How to run

## Prerequisites
* Java 1.8.231
* Be on the project root folder

## 1 - Compile 
```shell script
../gradlew shadowJar
```

## 2 - Start the Server
```shell script
java -cp ./build/libs/opentelemetry-examples-http-0.1.0-SNAPSHOT-all.jar io.opentelemetry.example.http.HttpServer
```
 
## 3 - Start the Client
```shell script
java -cp ./build/libs/opentelemetry-examples-http-0.1.0-SNAPSHOT-all.jar io.opentelemetry.example.http.HttpClient
```

## 4 - Check the HTTP headers
```bash
sudo tcpdump -vv  -l -n -s 5655 -i lo0 tcp port 8080
..
097769688 ecr 501694531], length 172: HTTP, length: 172
	GET / HTTP/1.1
	traceparent: 00-5dae07aef8e2e0312e726231879efa3b-b4bc78d1b2de31e9-01
..
```
