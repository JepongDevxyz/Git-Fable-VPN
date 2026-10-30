package jayar.lee.espiritu.utils;

import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.io.*;
import java.util.*;
import android.text.*;
import android.annotation.*;
import javax.net.ssl.*;

import jayar.lee.espiritu.service.InjectorService;

public class SSLUtil extends SSLSocketFactory
{
	private SSLContext mSSLContext;

	private InjectorService mInjector;

    public SSLUtil(InjectorService mInjector) throws Exception
	{
		this.mInjector = mInjector;
		mSSLContext = SSLContext.getInstance("TLS");
        mSSLContext.init(null, new TrustManager[]{new MyX509TrustManager()}, new SecureRandom());
	}

	private void createSSLSocket(String host, int port, boolean z) throws IOException
	{
        mInjector.mSSLSocket = (SSLSocket) mSSLContext.getSocketFactory().createSocket(mInjector.server, host, port, z);
		LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
		String protocol = "TLS";
		if (protocol.equals("SSL") || protocol.equals("TLS")) {
			Collections.addAll(linkedHashSet, mInjector.mSSLSocket.getEnabledProtocols());
        } else {
			linkedHashSet.add(protocol);
		}
		mInjector.mSSLSocket.setEnabledProtocols((String[]) linkedHashSet.toArray(new String[linkedHashSet.size()]));
		log("Enabled Protocols: " + TextUtils.join(", ", mInjector.mSSLSocket.getEnabledProtocols()));
        mInjector.mSSLSocket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
				public void handshakeCompleted(HandshakeCompletedEvent handshakeCompletedEvent)
				{
					SSLSession session = handshakeCompletedEvent.getSession();
					log(String.format("<b>Cipher Suite: %s</b>", session.getCipherSuite()));
                	log("HandshakeCompleted!");
				}
			});
	}
	private void log(String msg)
	{
		mInjector.log(msg);
	}
	public Socket createSocket(String host, int port) throws IOException
	{
		createSSLSocket(host, port, true);
		return mInjector.mSSLSocket;
	}

	public Socket createSocket(String str, int i, InetAddress inetAddress, int i2)
	{
		return null;
	}

    public Socket createSocket(InetAddress inetAddress, int i)
	{
		return null;
    }

	public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress2, int i2)
	{
		return null;
	}

	public Socket createSocket(Socket socket, String host, int port, boolean z) throws IOException
	{
		createSSLSocket(host, port, z);
		return mInjector.mSSLSocket;
	}

	public String[] getDefaultCipherSuites()
	{
		return new String[0];
	}

	public String[] getSupportedCipherSuites()
	{
        return new String[0];
	}
	public class MyX509TrustManager implements X509TrustManager {
		@SuppressLint({"TrustAllX509TrustManager"})
		public void checkClientTrusted(X509Certificate[] x509CertificateArr, String str) {
		}

		@SuppressLint({"TrustAllX509TrustManager"})
		public void checkServerTrusted(X509Certificate[] x509CertificateArr, String str) {
		}

		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}
	}
}
