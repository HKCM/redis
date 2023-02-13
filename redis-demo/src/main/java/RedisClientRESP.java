import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;

public class RedisClientRESP {

    // TODO 使用字节流完成
	static Socket s;
	static PrintWriter writer;
	static BufferedReader reader;

	static final char SUCCESS = '+'; // 代表简单字符串
	static final char ERROR = '-'; // 代表错误类型
	static final char INT = ':'; // 代表整数
	static final char ARRAYS = '*'; // 数组
	static final char BulkStrings = '$'; // 多行字符

	public static void main(String[] args) {
		try {
			// 建立连接
			String host = "127.0.0.1";
			int port = 6380;
			s = new Socket(host,port);
			// 获取输出流
			writer = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
			reader = new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));

			// 发出请求
			sendRequest();

			// 解析响应
			Object obj = handleResponse();

			System.out.println("obj = " + obj);
			// 释放连接
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			if (writer != null) writer.close();
			if (s != null) {
				try {
					s.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	private static Object handleResponse() throws IOException {

		// 读取首字节
		int prefix = reader.read();
		switch (prefix){
			case SUCCESS: // 单行字符串，直接读一行
				return reader.readLine();
				break;
			case ERROR: // 错误信息，直接读一行
				throw new RuntimeException(reader.readLine());
				break;
			case INT: // 数字，转化为数字返回
				return Long.parseLong(reader.readLine());
				break;
			case ARRAYS: // 多行
				return readArray();
				break;
			case BulkStrings: // 多行字符串
				// 先读长度
				int length = Integer.parseInt(reader.readLine());
				if (length == -1) return null;
				if (length == 0) return "";
				// 再读数据,应该读字节,避免特殊字符
				return reader.readLine();
				break;
			default:
				throw new RemoteException("Unsupported Format");
		}
	}

    private static Object readArray() {
        // 获取数组大小
    }

    private static void sendRequest() {
		writer.println("*3");
		writer.println("$3");
		writer.println("set");
		writer.println("$4");
		writer.println("name");
		writer.println("$6");
		writer.println("威武");

		writer.flush();
	}
}
