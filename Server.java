import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.InputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSSourceFile;

// to compile:
// $ javac -cp /usr/share/java/closure-compiler.jar:. Server.java
// to run
// $ java -cp /usr/share/java/closure-compiler.jar:. Server

/**
 * {@link Server} for Closure Compiler.
 *
 * @author Elan Ruusamäe <glen@delfi.ee>
 */
public final class Server implements Runnable {
	private final String listenAddress;

	private int port;

	public Server(String listenAddress, int port) {
		this.listenAddress = listenAddress;
		this.port = port;
	}

	@Override
	public void run() {
		InetSocketAddress addr = new InetSocketAddress(listenAddress, port);
		HttpServer server;
		try {
			// 0 indicates the system default should be used.
			final int maxQueuedIncomingConnections = 0;
			server = HttpServer.create(addr, maxQueuedIncomingConnections);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		server.createContext("/", new HttpHandler() {
			@Override
			public void handle(HttpExchange exchange) throws IOException {

				String requestMethod = exchange.getRequestMethod();
				if (!"POST".equalsIgnoreCase(requestMethod)) {
						exchange.sendResponseHeaders(400, -1);
						System.out.println("Bad request");
						return;
				}

				// shortest one that does not read by lines from here:
				// http://stackoverflow.com/questions/309424/read-convert-an-inputstream-to-a-string
				InputStream inputStream = exchange.getRequestBody();
				byte[] bytes = new byte[inputStream.available()];
				inputStream.read(bytes);
				String input = new String(bytes);

				System.out.println(input);
				String compiledCode = compile(input);
				System.out.println(compiledCode);

				// TODO: detect errors

				Headers responseHeaders = exchange.getResponseHeaders();
				responseHeaders.set("Content-Type", "text/plain");
				exchange.sendResponseHeaders(200, compiledCode.length());

				Writer responseBody = new OutputStreamWriter(exchange.getResponseBody());
				responseBody.write(compiledCode);
				responseBody.close();
			}
		});

		server.start();
	}

	/**
	 * @param code JavaScript source code to compile.
	 * @return The compiled version of the code.
	 */
	public static String compile(String code) {
		Compiler compiler = new Compiler();

		CompilerOptions options = new CompilerOptions();
		// Advanced mode is used here, but additional options could be set, too.
//		CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
		CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

		// To get the complete set of externs, the logic in
		// CompilerRunner.getDefaultExterns() should be used here.
		JSSourceFile externs = JSSourceFile.fromCode("externs.js", "");

		// The dummy input name "input.js" is used here so that any warnings or
		// errors will cite line numbers in terms of input.js.
		JSSourceFile input = JSSourceFile.fromCode("input.js", code);

		// compile() returns a Result, but it is not needed here.
		compiler.compile(externs, input, options);

		// The compiler is responsible for generating the compiled code; it is not
		// accessible via the Result.
		return compiler.toSource();
	}

	public static void main(String[] args) {
		Server server = new Server("0.0.0.0", 8888);
		server.run();
	}
}
