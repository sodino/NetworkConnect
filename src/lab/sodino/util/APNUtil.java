package lab.sodino.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.Proxy.Type;

public class APNUtil {
    public static String APN_TYPE_CTNET = "ctnet";
    public static String APN_TYPE_CTWAP = "ctwap";
    public static String APN_TYPE_CMNET = "cmnet";
    public static String APN_TYPE_CMWAP = "cmwap";
    public static String APN_TYPE_UNINET = "uninet";
    public static String APN_TYPE_UNIWAP = "uniwap";
    public static String APN_TYPE_3GNET = "3gnet";
    public static String APN_TYPE_3GWAP = "3gwap";
    
    public static String getApnType(String string){
    	String apntype="nomatch";
    	if (string == null)
    		return apntype;
    	try {
    		
			if (string.startsWith(APN_TYPE_CTNET)) {
				apntype = APN_TYPE_CTNET;
			} else if (string.startsWith(APN_TYPE_CTWAP)) {
				apntype = APN_TYPE_CTWAP;
			} else if (string.startsWith(APN_TYPE_CMNET)) {
				apntype = APN_TYPE_CMNET;
			} else if (string.startsWith(APN_TYPE_CMWAP)) {
				apntype = APN_TYPE_CMWAP;
			} else if (string.startsWith(APN_TYPE_UNINET)) {
				apntype = APN_TYPE_UNINET;
			} else if (string.startsWith(APN_TYPE_UNIWAP)) {
				apntype = APN_TYPE_UNIWAP;
			} else if(string.startsWith(APN_TYPE_3GNET)) {
				apntype = APN_TYPE_3GNET;
			} else if(string.startsWith(APN_TYPE_3GWAP)) {
				apntype = APN_TYPE_3GWAP;
			}
		} catch (Exception e) {
			//e.printStackTrace();
		}
		return apntype;
	}
    public static HttpURLConnection getConnectionWithDefaultProxy(String url,
			String defaultHost, int defaultPort) throws MalformedURLException,
			IOException {
		HttpURLConnection conn;
		java.net.Proxy proxy = new java.net.Proxy(Type.HTTP, new InetSocketAddress(defaultHost, defaultPort));
		URL proxyURL = new URL(url);
		conn = (HttpURLConnection) proxyURL.openConnection(proxy);
		return conn;
	}

    public static HttpURLConnection getConnectionWithXOnlineHost(String url,
			String defaultHost, int defaultPort) throws MalformedURLException,
			IOException {
		HttpURLConnection conn;
		URL hostUrl;
		String host = null;
		String path = null;
		int hostIndex = "http://".length();
		int pathIndex = url.indexOf('/', hostIndex);
		if (pathIndex < 0) {
			host = url.substring(hostIndex);
			path = "";
		} else {
			host = url.substring(hostIndex, pathIndex);
			path = url.substring(pathIndex);
		}
		hostUrl = new URL("http://" + defaultHost + ":" + defaultPort + path);
		conn = (HttpURLConnection) hostUrl.openConnection();
		conn.setRequestProperty("X-Online-Host", host);
		return conn;
	}
}
