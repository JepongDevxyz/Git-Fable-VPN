package jayar.lee.espiritu.thread;

import java.net.*;
import java.io.*;
import android.util.*;

public class BackServer extends Thread
{

	private ServerSocket ss;
	private Socket client;

	private boolean isAlive = true;
	//private HttpRequest mHttpRequest;
	public BackServer()
	{
		//mHttpRequest = HttpRequest;
	}
	@Override
	public void run()
	{
		// TODO: Implement this method
		try {
			ss = new ServerSocket();
			ss.setReuseAddress(true);
			ss.bind(new InetSocketAddress(0));
			log("[Back Server]", "Started on port " + ss.getLocalPort());
			while (isAlive) {
				try {
					client = ss.accept();
					BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
					String line;
					while ((line = reader.readLine()) != null) {
						log("[Back Server]",  line);
						OutputStream outputStream = client.getOutputStream();
						outputStream.write("HTTP/1.1 200 CONNECTED\r\n\r\n".getBytes());
						outputStream.flush();
						if (!client.isClosed()) {
							client.close();
						}
					}
				} catch (Exception e) {
					try {
						OutputStream outputStream = client.getOutputStream();
						outputStream.write("HTTP/1.1 200 CONNECTED\r\n\r\n".getBytes());
						outputStream.flush();
						if (!client.isClosed()) {
							client.close();
						}
					} catch (Exception e2) {

					}
				}
			}
		} catch (Exception e) {
			log("[Back Server]", e.getMessage());
		}
		super.run();
	}
	public SocketAddress getLocalSocketAddr()
	{
		return ss.getLocalSocketAddress();
	}
	public void Stop() throws Exception
	{
		if (ss != null) {
			ss.close();
		}
		if (client != null) {
			client.close();
		}
		isAlive = false;
		interrupt();
	}
	private void log(String tag, String msg)
	{
		Log.i(tag, msg);
	}
}
