import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.String;
import java.lang.System;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JSSourceFile;
import com.google.javascript.jscomp.JSError;


// to compile:
// $ javac -cp /usr/share/java/closure-compiler.jar:. Server.java
// to run
// $ java -cp /usr/share/java/closure-compiler.jar:. Server

/**
 * {@link Server} for Closure Compiler.
 *
 * Implement API defined in
 * {@link https://developers.google.com/closure/compiler/docs/api-ref Closure Compiler Service API Reference}
 *
 * @author Elan Ruusamäe <glen@delfi.ee>
 */
public final class Server implements Runnable {
	private final String listenAddress;
	private final int port;

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

				// parse the POST parameters
				Map<String,List<String>> params = new HashMap<String,List<String>>();
				String defs[] = input.split("[&]");
				String encoding = "UTF-8";
				for (String def: defs) {
					int ix = def.indexOf('=');
					String name;
					String value;
					if (ix < 0) {
						name = def;
						value = "";
					} else {
						name = def.substring(0, ix);
						value = URLDecoder.decode(def.substring(ix+1), encoding);
					}
					List<String> list = params.get(name);
					if (list == null) {
						list = new ArrayList<String>();
						params.put(name, list);
					}
					list.add(value);
				}

				System.out.println(input);
				System.out.println(params);
				if (!params.containsKey("js_code")) {
					exchange.sendResponseHeaders(400, -1);
					System.out.println("Bad request: no js_code");
					return;
				}

				if (params.containsKey("output_format") && !params.get("output_format").get(0).equals("text")) {
					exchange.sendResponseHeaders(400, -1);
					System.out.println("only output_format=text supported");
					return;
				}

				String compilation_level = "SIMPLE_OPTIMIZATIONS";
				if (params.containsKey("compilation_level")) {
					compilation_level = params.get("compilation_level").get(0);
				}

				String output_info = "compiled_code";
				if (params.containsKey("output_info")) {
					output_info = params.get("output_info").get(0);
				}

				String source = params.get("js_code").get(0);
				String compiledCode = compile(source, compilation_level, output_info);
				System.out.println(compiledCode);

				Headers responseHeaders = exchange.getResponseHeaders();
				responseHeaders.set("Content-Type", "text/plain");
				if (compiledCode == null) {
					exchange.sendResponseHeaders(204, -1);
					System.out.println("compile error");
					return;
				}

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
	public static String compile(String code, String compilation_level, String output_info) {
		Compiler compiler = new Compiler();
		CompilerOptions options = new CompilerOptions();

		if (compilation_level.equals("WHITESPACE_ONLY")) {
			CompilationLevel.WHITESPACE_ONLY.setOptionsForCompilationLevel(options);

		} else if (compilation_level.equals("SIMPLE_OPTIMIZATIONS")) {
			CompilationLevel.SIMPLE_OPTIMIZATIONS.setOptionsForCompilationLevel(options);

		} else if (compilation_level.equals("ADVANCED_OPTIMIZATIONS")) {
			CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
		}

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
		if (output_info.equals("compiled_code")) {
			if (compiler.getErrorCount() > 0) {
				return null;
			}
			return compiler.toSource();
		} else if (output_info.equals("errors")) {
			String errors = String.format("%d Errors, %d Warnings\n", compiler.getErrorCount(), compiler.getWarningCount());
			for (JSError message : compiler.getWarnings()) {
				errors += "Warning: " + message.toString() + "\n";
			}

			for (JSError message : compiler.getErrors()) {
				errors += "Error: " + message.toString() + "\n";
			}
			return errors;
		} else {
			return null;
		}
	}

	public static void main(String[] args) {
		Properties props = System.getProperties();
		String listenAddress = props.getProperty("address");
		if (listenAddress == null) {
			listenAddress = "0.0.0.0";
		}
		String listenPort = props.getProperty("port");
		if (listenPort == null) {
			listenPort = "8888";
		}
		int port = Integer.parseInt(listenPort);

		System.out.println("Listening on " + listenAddress + ":" + port);
		Server server = new Server(listenAddress, port);
		server.run();
	}
}
