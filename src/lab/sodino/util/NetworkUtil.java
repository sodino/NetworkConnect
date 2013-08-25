package lab.sodino.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Proxy;
import android.os.Environment;

public class NetworkUtil {
	public static final String SAVE_FOLDER_PATH = Environment.getExternalStorageDirectory() + File.separator + "net_save" + File.separator;
	public static final int MAX_RETRY_COUNT = 2;
	
	public static final int DOWNLOAD_SUCCESS		 	= 0;
	/**�������Ӳ�����*/
	public static final int DOWNLOAD_NETWORK_UNUSABLE = 1;
	/**url���ӳ���������ԭ�е�url������������滻ip������*/
	public static final int DOWNLOAD_URL_STRING_ILLEGAL = 2;
	/**�������ӳ�ʱ*/
	public static final int DOWNLOAD_HTTP_CONNECT_TIMEOUT = 3;
	/**���Ӷ�ȡ��ʱ*/
	public static final int DOWNLOAD_HTTP_SO_TIMEOUT = 4;
	/**��ֱ����ip����ʱ������ת����ip�����ã�Ҫʶ������ӡ�*/
	public static final int DOWNLOAD_UNKNOWN_HOST = 5;
	/**���ع����������쳣��*/
	public static final int DOWNLOAD_SOCKET_EXCEPTION = 6;
	/**�������Ӳ���HTTP_RESPONSE_OK:200*/
	public static final int DOWNLOAD_URL_RESP_NO_OK = 7;
	/**���ݶ�ȡ��ƥ�䡣Ϊ�ڷ��û�ȡ����������ٶ��ˡ�*/
	public static final int DOWNLOAD_DATA_LOSSY = 8;
	/**���ļ�ʱʧ�ܡ�*/
	public static final int DOWNLOAD_SAVE_FILE_FAIL = 9;
	public static final int DOWNLOAD_USER_CANCEL = 10;
	/**����δ��ȷ�������쳣��*/
	public static final int DOWNLOAD_EXCEPTION = 11;

	/**��¼���ʹ�õ��ƶ��������ơ�<br/>
	 * ���ֻ���ǰ���õ��ƶ��������¼��һ��ʱ����ǰ��������������е��ƶ���������<br/>
	 * ���ƶ�������ء�<br/>
	 * */
	public static String lastApn;
	/**
	 * �����ƶ����ص�����£���ʶ�Ƿ�ֱ��url.openConnection()����Ϊ�����һ��ʹ������ʧ���ˣ�������ʱ��ֱ�����ӡ�<br/>
	 * ���ƶ�������ء�<br/>*/
	public static boolean forceDirect;
	
	public static void download(Context context, DownloadInfo info){
		info.resultCode = DOWNLOAD_EXCEPTION;
		File fileSaveTmp = null;
		
		// ---->Ԥ����:Ŀ¼���
		if (DownloadInfo.ACTION_SAVE == info.dataAction) {
			// ��Ҫ���ش洢����������Ŀ¼Ԥ����
			fileSaveTmp = new File(SAVE_FOLDER_PATH + StringUtil.getSubffixNameByUrl(info.urlOriginal) +".tmp");
			File parentFolder = fileSaveTmp.getParentFile();
			if (parentFolder != null && parentFolder.exists() == false) {
				parentFolder.mkdirs();
			}
			// ��ʱ�ļ��Ĵ���
			if(fileSaveTmp.exists()){
				fileSaveTmp.delete();
			}
		}
		
		
		int tryCount = 0;	// ���Լ�������ʼΪ0
		boolean need2try = true;
		boolean doneConnect = false; // �Ƿ�ִ��httpURLConnection.connect()
		boolean useProxy = false; // �Ƿ�ʹ���˴���
		
		// ------>>>>>>>>��ʼ������������
		OutputStream os = null;
		InputStream is = null;
		HttpURLConnection httpConn = null;
		
		Object waitTimeObj = new Object();
		do {
			doneConnect = useProxy = false;
			try{
				String urlString = info.urlOriginal;
				// ------>>>>>>>>���������жϣ���Ϊ���ʧ��������5s�Ժ�����ˣ����ʱ���ڿ��������Ѿ�û�ˣ�����Ҫ��������
				NetworkInfo activeNetworkInfo = ((ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
				if (activeNetworkInfo == null) {
					LogOut.out(NetworkUtil.class, "Download failed-----------activeNetworkInfo is null");
					info.resultCode = DOWNLOAD_NETWORK_UNUSABLE;
					return;
				}
				// ------>>>>>>>>�����Ԥ����
				int type = -1;
				String exrea = null;
				if(activeNetworkInfo!=null){
					type = activeNetworkInfo.getType();
					// �����磺3gwap 3gnet cmwap ��apn����
					exrea = activeNetworkInfo.getExtraInfo();
				}
				String apnType = APNUtil.getApnType(exrea);
				if(apnType.equals(lastApn) == false){
					// ʹ���µ�apn�ˣ���Ҫ���Ը�proxy
					forceDirect = false;
					lastApn = apnType;
				}
				String defaultHost = Proxy.getDefaultHost();
				int defaultPort = Proxy.getDefaultPort();
				if((type == ConnectivityManager.TYPE_MOBILE || type == ConnectivityManager.TYPE_MOBILE+50)// +50ֻΪ�˺�HttpCommunicator����һ�£���������..�Ǳ�Ҳ��֪����
						&& defaultHost != null && defaultPort > 0 // 
						&& forceDirect == false
						){
					if(exrea != null){
						if (apnType.equals(APNUtil.APN_TYPE_CMWAP) || 
							apnType.equals(APNUtil.APN_TYPE_UNIWAP) || 
							apnType.equals(APNUtil.APN_TYPE_3GWAP)) {
							httpConn = APNUtil.getConnectionWithXOnlineHost(urlString, defaultHost, defaultPort);
						} else if (apnType.equals(APNUtil.APN_TYPE_CTWAP)) { // ctwap��default proxy
							httpConn = APNUtil.getConnectionWithDefaultProxy(urlString, defaultHost, defaultPort);
						} else { // ��ԭ�ȵ��߼�,net�������Ȼ��default proxy�Ļ���������default proxy
							httpConn = APNUtil.getConnectionWithDefaultProxy(urlString, defaultHost, defaultPort);
						}
					} else { //ĳЩ����extra����null��� �����д���
						httpConn = APNUtil.getConnectionWithDefaultProxy(urlString, defaultHost, defaultPort);
					}
					useProxy = true;
				} else { // ��mobile����û��Ĭ�ϴ����ַ������£�����proxy
					URL hostUrl = new URL(urlString);
					httpConn = (HttpURLConnection) hostUrl.openConnection();
					useProxy = false;
				}
				LogOut.out(NetworkUtil.class, "forceDirect:"+forceDirect+" useProxy:"+ useProxy +" apnType:" + apnType +" defaultHost:" + defaultHost +" defaltPort:" + defaultPort +" url:" + urlString);
				// �����������һ���ص�content-lengthƫС..
				httpConn.setRequestProperty("Accept-Encoding", "identity");
				httpConn.setConnectTimeout(1000*30);
				httpConn.setReadTimeout(1000*30);
				//wap��302��ת�����Լ��������¿���connection����ǰ�Ǹ���������Ϊhttpͷ��X-Online-Host������������
				//��׼��HttpUrlConnection������������X-Online_Host��
				// ���δ��벻���Լ���������Ҫֱ������
				httpConn.setInstanceFollowRedirects(true);
				// ------>>>>>>>>��ʼ�����ȡ
				info.resultCode = DOWNLOAD_EXCEPTION;
				httpConn.connect();
				doneConnect = true;
				
				/////////////////////////////////////////////////////////////////////////
				Map<String,List<String>> mapTest = httpConn.getHeaderFields();
				if(mapTest != null){
					String headLine = "";
					Set<String> set = mapTest.keySet();
					Iterator<String> iterator = set.iterator();
					while(iterator.hasNext()){
						String key = iterator.next();
						List<String> list = mapTest.get(key);
						String valueLine = "";
						if(list != null){
							for(String tmp:list){
								valueLine += tmp+" ";
							}
						}
						headLine += "key["+key + "]value["+valueLine+"] ";
					}
					LogOut.out(NetworkUtil.class, "header " + headLine);
				}
				/////////////////////////////////////////////////////////////////////////
				
				
			}catch(Throwable t){
				t.printStackTrace();
				info.errorDetail = t.toString();
				// ��HttpCommunicator.getConnect()����һ�£�����SocketTimeoutException��ConnectException
				boolean isProxyConnectException = false;
				if(t instanceof MalformedURLException){
					info.resultCode = DOWNLOAD_URL_STRING_ILLEGAL;
				}else if (t instanceof UnknownHostException) {
					info.resultCode = DOWNLOAD_UNKNOWN_HOST;
				}else if(t instanceof NoHttpResponseException){
					info.resultCode = DOWNLOAD_URL_RESP_NO_OK;
				} else if (t instanceof ConnectTimeoutException || t instanceof SocketTimeoutException) {
					info.resultCode = DOWNLOAD_HTTP_CONNECT_TIMEOUT;
					isProxyConnectException = true;
				}else if(t instanceof SocketException){
					info.resultCode = DOWNLOAD_SOCKET_EXCEPTION;
					isProxyConnectException = true;
				}else{
					info.resultCode = DOWNLOAD_SAVE_FILE_FAIL;
				}
				
				if(doneConnect == false && isProxyConnectException){
					if(useProxy){
						// ����ʹ�õ�proxy������ִ��httpURLConnection.connect()ʱ�������쳣
						// ��ʹ��proxy��ֱ��!
						forceDirect = true;
					}else{
						// �����ʹ��proxy��ֱ��ʧ����,�ָ���ʹ�ô���
						forceDirect = false;
					}
					
					LogOut.out(NetworkUtil.class, "change forceDirect:"+forceDirect+" doneConnect:" + doneConnect +" isProxyConnectEx:" + isProxyConnectException+" useProxy:" + useProxy);
				}
				LogOut.out(NetworkUtil.class, "Download fail resultCode="+info.resultCode+". url=" + info.urlOriginal +" exception:"+t.toString());
			}finally{
				
			}
			
			
			tryCount ++;
			LogOut.out(NetworkUtil.class, "Download. result=" + info.resultCode + ", url=" + info.urlOriginal);
			need2try = need2Try(context, info.resultCode, tryCount, MAX_RETRY_COUNT);
			if(need2try && info.resultCode != DOWNLOAD_HTTP_CONNECT_TIMEOUT && info.resultCode != DOWNLOAD_HTTP_SO_TIMEOUT){
				// ��Ҫ�����Ҳ������ӳ�ʱ�Ĵ��󣬲ŵȸ�5s.���ӳ�ʱ����ʱ���Ѿ�����̫���ˣ���Ҫ�ٵ�5s�ˣ��˷�ʱ��
				synchronized (waitTimeObj) {
					try {
						waitTimeObj.wait(5000);
					} catch (InterruptedException e) {
					}
				}
			}
		}while(need2try);
	}

	private static boolean need2Try(Context context, int resultCode, int tryCount,int maxTry){
		boolean bool = resultCode != DOWNLOAD_SUCCESS// ���سɹ���������  
				&& resultCode != DOWNLOAD_USER_CANCEL// �û�ȡ���ģ�������
				&& tryCount < maxTry//
				&& isNetSupport(context);// ������ز��ɹ�,�����������,����������
		return bool;
	}
	public static String ping(String domain){
		String line = "";
		InputStream is = null;
		try {
			line = "/nping -c 1 " + domain;
			// -c 1:��ʾping�Ĵ���Ϊ1�Ρ�
			Process p = Runtime.getRuntime().exec("ping -c 1 " + domain);
			// �ȴ�������ִ����ϡ�
			int status = p.waitFor();
			if (status == 0) {
				// �����˳�
				line += "Pass";
			} else {
				// �쳣�˳�
				line += "Fail: Host unreachable";
			}
			is = p.getInputStream();
			byte[] data = new byte[is.available()];
			is.read(data);
			line += "/n" + new String(data);
		} catch (UnknownHostException e) {
			line += "Fail: Unknown Host";
		} catch (IOException e) {
			line += "Fail: IOException";
		} catch (InterruptedException e) {
			line += "Fail: InterruptedException";
		}
		return line;
	}
	
	
    public static boolean isNetSupport(Context context) {
	    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE); 
		if (cm == null) {
	        return false;
	    }
	    try{
		    NetworkInfo[] info = cm.getAllNetworkInfo(); 
	        if (info != null) {
	            for (int i = 0; i < info.length; i++) {
	                if (info[i].getState() == NetworkInfo.State.CONNECTED){
	                    return true;
	                }
	            }
	        }
	    }catch (Exception e){
	        e.printStackTrace();
	    }
	    return false;
    }
}
