package jayar.lee.espiritu.thread;

import jayar.lee.espiritu.logger.hLogStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ProxyThread extends Thread {
	boolean LoopingThread = true;
	boolean autoReplace = false;
	String buffReq = "1024";
	String buffRes = "4096";
	boolean clientToServer;
	Socket incoming;
	Socket outgoing;

	public static int status = 0;
	ProxyThread(Socket in, Socket out, boolean clientToServer, String buffReq, String buffRes, boolean autoReplace) {
		this.incoming = in;
		this.outgoing = out;
		this.clientToServer = clientToServer;
		this.buffReq = buffReq;
		this.buffRes = buffRes;
		this.autoReplace = autoReplace;
	}

	public final void run() {
		byte[] buffer;
		if (this.clientToServer) {
			buffer = new byte[Integer.parseInt(this.buffReq)];
		} else {
			buffer = new byte[Integer.parseInt(this.buffRes)];
		}
		try {
			InputStream FromClient = this.incoming.getInputStream();
			OutputStream ToClient = this.outgoing.getOutputStream();
			while (true) {
				int numberRead = FromClient.read(buffer);
				if (numberRead == -1) {
					break;
				}
				try {
					String result = new String(buffer, 0, numberRead);
					if (this.clientToServer) {
						ToClient.write(buffer, 0, numberRead);
						ToClient.flush();
					} else {
						String[] split = result.split("\r\n");
						if (split[0].startsWith("HTTP/")) {
							String line = split[0];
							String msg_success = "OVPN Client: Connection Established";
							int code = Integer.parseInt(line.substring(9, 12));
							String msg = line.substring(13);
							String log = String.format("<b>HTTP/1.1 200 OK</b>", String.valueOf(code), msg);
							Log(log);
							status = code;
							if (code == 200) {
								Log(msg_success);
								ToClient.write(buffer, 0, numberRead);
								ToClient.flush();
							} else {
								//OpenVPNService.reconnect();
								ToClient.write("HTTP/1.0 200 Connection Established\r\n\r\n".getBytes());
								ToClient.flush();
							}
						} else {
							ToClient.write(buffer, 0, numberRead);
							ToClient.flush();
						}
					}
				} catch (Exception e) {
					try {
						if (this.incoming != null) {
							this.incoming.close();
						}
						if (this.outgoing != null) {
							this.outgoing.close();
							return;
						}
						return;
					} catch (IOException e2) {
						return;
					}
				} catch (Throwable th) {
					try {
						if (this.incoming != null) {
							this.incoming.close();
						}
						if (this.outgoing != null) {
							this.outgoing.close();
						}
					} catch (IOException e3) {
					}
				}
			}
		} catch (Exception e) {
		}
	}

	public static void connect(Socket first, Socket second, String buffReq, String buffRes, boolean autoReplace)  {
		new ProxyThread(first, second, true, buffReq, buffRes, autoReplace).start();
		new ProxyThread(second, first, false, buffReq, buffRes, autoReplace).start();
	}

	private void Log(String msg) {
       hLogStatus.logInfo(msg);
		//OpenVPNService.log_message(msg);
	}
}