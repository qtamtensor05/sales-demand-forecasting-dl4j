import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class SimpleHealthServer {
    private SimpleHealthServer() { }

    public static void main(String[] args) throws IOException {
        String name = System.getenv().getOrDefault("SERVICE_NAME", "bootstrap-service");
        int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", exchange -> respond(exchange, 200,
                "{\"status\":\"UP\",\"service\":\"" + name + "\",\"phase\":\"bootstrap\"}"));
        server.createContext("/", exchange -> respond(exchange, 404,
                "{\"error\":\"Phase 1 bootstrap only\"}"));
        server.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

