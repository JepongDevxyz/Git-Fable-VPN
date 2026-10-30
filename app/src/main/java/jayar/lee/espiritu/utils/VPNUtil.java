package jayar.lee.espiritu.utils;
import java.net.*;

import jayar.lee.espiritu.service.OpenVPNService;

public class VPNUtil
{

	private static VPNUtil.VPNProtectListener Listener;

	private static OpenVPNService mService;
	public interface VPNProtectListener
	{
		boolean protectSocket(Socket socket);
	}
	public static void setVPNProtectListener(VPNProtectListener VPNProtectListener)
	{
		Listener = VPNProtectListener;
	}
	public static boolean isProtected(Socket socket)
	{
		return Listener != null ? Listener.protectSocket(socket) : false;
	}
	public static void setVPNService(OpenVPNService service)
	{
		mService = service;
	}
	public static OpenVPNService getService()
	{
		return mService;
	}
}
