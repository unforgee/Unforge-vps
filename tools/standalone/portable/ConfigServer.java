import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Minimal static file server for the RuneLite jav_config / world_list / gamepack files. Uses only
 * the JDK's built-in HttpServer so the portable package needs nothing but the bundled JRE - no
 * Python, no PowerShell HttpListener, no admin rights (loopback bind only).
 *
 * <p>Usage: {@code java -cp tools ConfigServer <port> <directory> [defaultFile]}
 */
public class ConfigServer {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        Path root = Paths.get(args[1]).toAbsolutePath().normalize();
        String defaultFile = args.length > 2 ? args[2] : "jav_local_239.ws";

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext(
                "/",
                exchange -> {
                    try {
                        String rel = exchange.getRequestURI().getPath();
                        if (rel == null || rel.equals("/")) {
                            rel = "/" + defaultFile;
                        }
                        Path file = root.resolve(rel.substring(1)).normalize();
                        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                            exchange.sendResponseHeaders(404, -1);
                            return;
                        }
                        byte[] bytes = Files.readAllBytes(file);
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        System.out.println("UnForge config server on 127.0.0.1:" + port + " serving " + root);
    }
}
