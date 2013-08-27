package lab.sodino.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NoHttpResponseException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Proxy;

public class NetworkUtil {
	public static final int MAX_RETRY_COUNT = 2;

	public static final int DOWNLOAD_SUCCESS		 	= 0;
	/**网络链接不可用*/
	public static final int DOWNLOAD_NETWORK_UNUSABLE = 1;
	/**url链接出错，可能是原有的url出错亦可能是替换ip后引起。*/
	public static final int DOWNLOAD_URL_STRING_ILLEGAL = 2;
	/**链接连接超时*/
	public static final int DOWNLOAD_HTTP_CONNECT_TIMEOUT = 3;
	/**链接读取超时*/
	public static final int DOWNLOAD_HTTP_SO_TIMEOUT = 4;
	/**当直接以ip访问时，可能转换的ip不可用，要识别这个坑。*/
	public static final int DOWNLOAD_UNKNOWN_HOST = 5;
	/**下载过程中链接异常。*/
	public static final int DOWNLOAD_SOCKET_EXCEPTION = 6;
	/**返回链接不是HTTP_RESPONSE_OK:200*/
	public static final int DOWNLOAD_URL_RESP_NO_OK = 7;
	/**数据读取不匹配。为在非用户取消的情况下少读了。*/
	public static final int DOWNLOAD_DATA_LOSSY = 8;
	/**存文件时失败。*/
	public static final int DOWNLOAD_SAVE_FILE_FAIL = 9;
	public static final int DOWNLOAD_USER_CANCEL = 10;
	/**所有未明确的下载异常。*/
	public static final int DOWNLOAD_EXCEPTION = 11;

	/**记录最后使用的移动网关名称。<br/>
	 * 当手机当前可用的移动网关与记录不一样时，则当前的链接请求必须切到移动网关来。<br/>
	 * 与移动网关相关。<br/>
	 * */
	public static String lastApn;
	/**
	 * 在有移动网关的情况下，标识是否直接url.openConnection()，因为如果上一次使用网关失败了，则重试时则直接连接。<br/>
	 * 与移动网关相关。<br/>*/
	public static boolean forceDirect;

	public static void downloadByJava(Context context, DownloadInfo info){
		info.resultCode = DOWNLOAD_EXCEPTION;
		File fileSaveTmp = null;

		// ---->预操作:目录检查
		if (DownloadInfo.ACTION_SAVE == info.dataAction) {
			// 需要本地存储操作，进行目录预处理
			fileSaveTmp = new File(info.file.getAbsolutePath() +".tmp");
			File parentFolder = fileSaveTmp.getParentFile();
			if (parentFolder != null && parentFolder.exists() == false) {
				parentFolder.mkdirs();
			}
		}


		int tryCount = 0;	// 重试计数，初始为0
		boolean need2try = true;
		boolean doneConnect = false; // 是否执行httpURLConnection.connect()
		boolean useProxy = false; // 是否使用了代理

		// ------>>>>>>>>开始处理网络连接
		OutputStream os = null;
		InputStream is = null;
		HttpURLConnection httpConn = null;

		Object waitTimeObj = new Object();
		do {
			doneConnect = useProxy = false;
			try{
				// 临时文件的处理
				if (fileSaveTmp != null) {
					if (fileSaveTmp.exists()) {
						fileSaveTmp.delete();
					}
					// 新创建该文件，避免在new
					// FileOutputStream()时，出现FileNotFoundException:EBUSY (Device or resource busy)
					fileSaveTmp.createNewFile();
				}
				String urlString = info.urlOriginal;
				// ------>>>>>>>>网络有无判断，因为如果失败重试是5s以后的事了，这段时间内可能网络已经没了，不需要再重试了
				NetworkInfo activeNetworkInfo = ((ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
				if (activeNetworkInfo == null) {
					LogOut.out(NetworkUtil.class.getName(), "Download failed-----------activeNetworkInfo is null");
					info.resultCode = DOWNLOAD_NETWORK_UNUSABLE;
					return;
				}
				// ------>>>>>>>>代理的预处理
				String exrea = null;
				if(activeNetworkInfo!=null){
					// 返回如：3gwap 3gnet cmwap 的apn名称
					exrea = activeNetworkInfo.getExtraInfo();
				}
				String apnType = APNUtil.getApnType(exrea);
				if(apnType.equals(lastApn) == false){
					// 使用新的apn了，需要尝试该proxy
					forceDirect = false;
					lastApn = apnType;
				}
				String defaultHost = Proxy.getDefaultHost();
				int defaultPort = Proxy.getDefaultPort();
				if(isMobileNetworkInfo(activeNetworkInfo) && defaultHost != null && defaultPort > 0 && forceDirect == false){
					if(exrea != null){
						if (apnType.equals(APNUtil.APN_TYPE_CMWAP) || 
							apnType.equals(APNUtil.APN_TYPE_UNIWAP) || 
							apnType.equals(APNUtil.APN_TYPE_3GWAP)) {
							httpConn = APNUtil.getConnectionWithXOnlineHost(urlString, defaultHost, defaultPort);
						} else if (apnType.equals(APNUtil.APN_TYPE_CTWAP)) { // ctwap走default proxy
							httpConn = APNUtil.getConnectionWithDefaultProxy(urlString, defaultHost, defaultPort);
						} else { // 照原先的逻辑,net情况下仍然有default proxy的话，还是走default proxy
							httpConn = APNUtil.getConnectionWithDefaultProxy(urlString, defaultHost, defaultPort);
						}
					} else { //某些机型extra会有null情况 并且有代理
						httpConn = APNUtil.getConnectionWithDefaultProxy(urlString, defaultHost, defaultPort);
					}
					useProxy = true;
				} else { // 非mobile或者没有默认代理地址的情况下，不走proxy
					URL hostUrl = new URL(urlString);
					httpConn = (HttpURLConnection) hostUrl.openConnection();
					useProxy = false;
				}
				LogOut.out(NetworkUtil.class.getName(), "forceDirect:"+forceDirect+" useProxy:"+ useProxy +" apnType:" + apnType +" defaultHost:" + defaultHost +" defaltPort:" + defaultPort +" url:" + urlString);
				// 如果不设置这家伙，返回的content-length偏小..
				httpConn.setRequestProperty("Accept-Encoding", "identity");
				httpConn.setConnectTimeout(1000*30);
				httpConn.setReadTimeout(1000*30);
				//wap的302跳转必须自己处理，重新开个connection，以前那个废弃，因为http头的X-Online-Host必须重新设置
				//标准的HttpUrlConnection不会重新设置X-Online_Host的
				// 本段代码不会自己处理，都需要直接连接
				httpConn.setInstanceFollowRedirects(true);
				// ------>>>>>>>>开始处理读取
				info.resultCode = DOWNLOAD_EXCEPTION;
				httpConn.connect();
				//////////////////////////////////////////////////////////////////////////////
//				if(tryCount == 0){
//					throw new ConnectTimeoutException();
//				}
				//////////////////////////////////////////////////////////////////////////////
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
					LogOut.out(NetworkUtil.class.getName(), "header " + headLine);
				}
				/////////////////////////////////////////////////////////////////////////
				
				int respCode = httpConn.getResponseCode();
				int contentLength = httpConn.getContentLength();
				if(respCode == HttpStatus.SC_OK){
					is = httpConn.getInputStream();
					if(info.dataAction == DownloadInfo.ACTION_READ){
						os = new ByteArrayOutputStream();
					} else if(info.dataAction == DownloadInfo.ACTION_SAVE){
						os = new FileOutputStream(fileSaveTmp);
					}
					
					byte[]data = new byte[2048];
					int count = -1;
					int read = 0;
					
					while((count = is.read(data)) > -1){
						os.write(data, 0, count);
						read += count;
						LogOut.out(NetworkUtil.class.getName(), "download all:" + contentLength + " read:" + read +" url:" + urlString);
					}
					
					if(read != contentLength){
						info.resultCode = DOWNLOAD_DATA_LOSSY;
						if(info.dataAction == DownloadInfo.ACTION_SAVE && fileSaveTmp != null){
							fileSaveTmp.delete();
						}
						LogOut.out(NetworkUtil.class.getName(), "DOWNLOAD_DATA_LOSSY result=" + info.resultCode +" url:" + urlString);
						return;
					} else {
						if(info.dataAction == DownloadInfo.ACTION_READ){
							info.data = ((ByteArrayOutputStream)os).toByteArray();
							info.resultCode = DOWNLOAD_SUCCESS;
							LogOut.out(NetworkUtil.class.getName(), "DOWNLOAD_SUCCESS result=" + info.resultCode +" url:" + urlString);
						}else if(info.dataAction == DownloadInfo.ACTION_SAVE){
							boolean bool = fileSaveTmp.renameTo(info.file);
							if(bool){
								info.resultCode = DOWNLOAD_SUCCESS;
								LogOut.out(NetworkUtil.class.getName(), "DOWNLOAD_SUCCESS result=" + info.resultCode +" url:" + urlString);
							}else{
								info.resultCode = DOWNLOAD_SAVE_FILE_FAIL;
								LogOut.out(NetworkUtil.class.getName(), "DOWNLOAD_SAVE_FILE_FAIL result=" + info.resultCode +" url:" + urlString);
							}
						}
					}
					
				} else {
					info.resultCode = DOWNLOAD_URL_RESP_NO_OK;
					LogOut.out(NetworkUtil.class.getName(), "DOWNLOAD_URL_RESP_NO_OK result=" + info.resultCode + " respCode:" + respCode +" url:" + urlString);
				}
			}catch(Throwable t){
				t.printStackTrace();
				info.errorDetail = t.toString();
				// 与HttpCommunicator.getConnect()保持一致，处理SocketTimeoutException、ConnectException
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
					info.resultCode = DOWNLOAD_EXCEPTION;
				}

				if(doneConnect == false && isProxyConnectException){
					if(useProxy){
						// 表明使用的proxy并且在执行httpURLConnection.connect()时发生的异常
						// 则不使用proxy，直连!
						forceDirect = true;
					}else{
						// 如果不使用proxy且直连失败了,恢复到使用代理
						forceDirect = false;
					}

					LogOut.out(NetworkUtil.class.getName(), "change forceDirect:"+forceDirect+" doneConnect:" + doneConnect +" isProxyConnectEx:" + isProxyConnectException+" useProxy:" + useProxy);
				}
				LogOut.out(NetworkUtil.class.getName(), "Download fail resultCode="+info.resultCode+". url=" + info.urlOriginal +" exception:"+t.toString());
			}finally{
				try{
					if(os != null){
						os.close();
					}
					if(is != null){
						is.close();
					}
					if(httpConn != null){
						httpConn.disconnect();
					}
				}catch(Exception e){
					e.printStackTrace();
				}
			}


			tryCount ++;
			LogOut.out(NetworkUtil.class.getName(), "Download. result=" + info.resultCode + ", url=" + info.urlOriginal);
			need2try = need2Try(context, info.resultCode, tryCount, MAX_RETRY_COUNT);
			if(need2try && info.resultCode != DOWNLOAD_HTTP_CONNECT_TIMEOUT && info.resultCode != DOWNLOAD_HTTP_SO_TIMEOUT){
				// 需要重试且不是连接超时的错误，才等个5s.链接超时错误时，已经等了太久了，不要再等5s了，浪费时间
				synchronized (waitTimeObj) {
					try {
						waitTimeObj.wait(5000);
					} catch (InterruptedException e) {
					}
				}
			}
		}while(need2try);
	}
	public static void downloadByApache(Context context, DownloadInfo info){
		info.resultCode = DOWNLOAD_EXCEPTION;
		File fileSaveTmp = null;
		
		// ---->预操作:目录检查
		if (DownloadInfo.ACTION_SAVE == info.dataAction) {
			// 需要本地存储操作，进行目录预处理
			fileSaveTmp = new File(info.file.getAbsolutePath() +".tmp");
			File parentFolder = fileSaveTmp.getParentFile();
			if (parentFolder != null && parentFolder.exists() == false) {
				parentFolder.mkdirs();
			}
		}
		
		
		int tryCount = 0;	// 重试计数，初始为0
		boolean need2try = true;
		boolean doneExecute = false; // 是否执行httpURLConnection.connect()
		boolean useProxy = false; // 是否使用了代理
		String urlString = info.urlOriginal;

		URL url = null;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			e.printStackTrace();
			info.resultCode = DOWNLOAD_URL_STRING_ILLEGAL;
			info.errorDetail = String.valueOf(e);
			return;
		}
		
		// ------>>>>>>>>开始处理网络连接
		OutputStream os = null;
		InputStream is = null;
		HttpGet httpGet = null;
		HttpEntity entity = null;
		try {
			httpGet = new HttpGet(urlString);
		} catch (IllegalArgumentException ex) {
			// 需要转义
			try{
				httpGet = new HttpGet(urlString);
			}catch(IllegalArgumentException e){
				e.printStackTrace();
				info.resultCode = DOWNLOAD_URL_STRING_ILLEGAL;
				info.errorDetail = String.valueOf(e);
				return ;
			}
		}
		
		Object waitTimeObj = new Object();
		do {
			doneExecute = useProxy = false;
			try{
				// 临时文件的处理
				if (fileSaveTmp != null) {
					if (fileSaveTmp.exists()) {
						fileSaveTmp.delete();
					}
					// 新创建该文件，避免在new
					// FileOutputStream()时，出现FileNotFoundException:EBUSY (Device or resource busy)
					fileSaveTmp.createNewFile();
				}
				// ------>>>>>>>>网络有无判断，因为如果失败重试是5s以后的事了，这段时间内可能网络已经没了，不需要再重试了
				NetworkInfo activeNetworkInfo = ((ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();
				if (activeNetworkInfo == null) {
					LogOut.out(NetworkUtil.class.getName(), "Download failed-----------activeNetworkInfo is null");
					info.resultCode = DOWNLOAD_NETWORK_UNUSABLE;
					return;
				}
				// ------>>>>>>>>代理的预处理
				String exrea = null;
				if(activeNetworkInfo!=null){
					// 返回如：3gwap 3gnet cmwap 的apn名称
					exrea = activeNetworkInfo.getExtraInfo();
				}
				String apnType = APNUtil.getApnType(exrea);
				if(apnType.equals(lastApn) == false){
					// 使用新的apn了，需要尝试该proxy
					forceDirect = false;
					lastApn = apnType;
				}
				String defaultHost = Proxy.getDefaultHost();
				int defaultPort = Proxy.getDefaultPort();
				boolean isMobileNetwork = isMobileNetworkInfo(activeNetworkInfo);
				
				HttpParams httpParams = new BasicHttpParams();
				httpParams.setParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 1000 * 30);
				// 增加读取数据的超时
				httpParams.setParameter(CoreConnectionPNames.SO_TIMEOUT, 1000 * 30);
				int bufferSize = 0;
				if (isMobileNetwork) {
					bufferSize = 2048;
				} else {
					bufferSize = 2048 * 2;
				}
				httpParams.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, bufferSize);
				if(isMobileNetwork && defaultHost != null && defaultPort > 0 && forceDirect == false){
					// 使用代理
					HttpHost httpHost = new HttpHost(defaultHost, defaultPort);
					httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, httpHost);
					useProxy = true;
				} else {
					// 非mobile或者没有默认代理地址的情况下，不走proxy
					HttpHost httpHost = new HttpHost(url.getHost(), url.getPort());
					httpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, httpHost);
					httpGet.setParams(httpParams);
					useProxy = false;
				}
				httpGet.setParams(httpParams);
				
				LogOut.out(NetworkUtil.class.getName(), "forceDirect:"+forceDirect+" useProxy:"+ useProxy +" apnType:" + apnType +" defaultHost:" + defaultHost +" defaltPort:" + defaultPort +" url:" + urlString);
				// ------>>>>>>>>开始处理读取
				info.resultCode = DOWNLOAD_EXCEPTION;
				HttpResponse httpRes = new DefaultHttpClient().execute(httpGet);
				//////////////////////////////////////////////////////////////////////////////
//				if(tryCount == 0){
//					throw new ConnectTimeoutException();
//				}
				//////////////////////////////////////////////////////////////////////////////
				doneExecute = true;
				
				/////////////////////////////////////////////////////////////////////////
				Header[] headers = httpRes.getAllHeaders();
				if(headers != null){
					String headLine = "";
					for(Header h : headers){
						headLine += "k["+h.getName() +"]v["+ h.getValue() +"] ";
					}
					LogOut.out(NetworkUtil.class.getName(), "header " + headLine);
				}
				/////////////////////////////////////////////////////////////////////////
				StatusLine statusLine = httpRes.getStatusLine();
				int statuscode = statusLine.getStatusCode();
				if(statuscode == HttpStatus.SC_OK){
					entity = httpRes.getEntity();
					long contentLength = entity.getContentLength();
					is = entity.getContent();
					if(info.dataAction == DownloadInfo.ACTION_READ){
						os = new ByteArrayOutputStream();
					} else if(info.dataAction == DownloadInfo.ACTION_SAVE){
						os = new FileOutputStream(fileSaveTmp);
					}
					
					byte[]data = new byte[2048];
					int count = -1;
					int read = 0;
					
					while((count = is.read(data)) > -1){
						os.write(data, 0, count);
						read += count;
						LogOut.out(NetworkUtil.class.getName(), "download all:" + contentLength + " read:" + read +" url:" + urlString);
					}
					
					if(read != contentLength){
						info.resultCode = DOWNLOAD_DATA_LOSSY;
						if(info.dataAction == DownloadInfo.ACTION_SAVE && fileSaveTmp != null){
							fileSaveTmp.delete();
						}
						LogOut.out(NetworkUtil.class.getName(), "DOWNLOAD_DATA_LOSSY result=" + info.resultCode +" url:" + urlString);
						return;
					} else {
						if(info.dataAction == DownloadInfo.ACTION_READ){
							info.data = ((ByteArrayOutputStream)os).toByteArray();
							info.resultCode = DOWNLOAD_SUCCESS;
							LogOut.out(NetworkUtil.class.getName(), "DOWNLOAD_SUCCESS result=" + info.resultCode +" url:" + urlString);
						}else if(info.dataAction == DownloadInfo.ACTION_SAVE){
							boolean bool = fileSaveTmp.renameTo(info.file);
							if(bool){
								info.resultCode = DOWNLOAD_SUCCESS;
								LogOut.out(NetworkUtil.class.getName(), "DOWNLOAD_SUCCESS result=" + info.resultCode +" url:" + urlString);
							}else{
								info.resultCode = DOWNLOAD_SAVE_FILE_FAIL;
								LogOut.out(NetworkUtil.class.getName(), "DOWNLOAD_SAVE_FILE_FAIL result=" + info.resultCode +" url:" + urlString);
							}
						}
					}
					
				} else {
					info.resultCode = DOWNLOAD_URL_RESP_NO_OK;
					LogOut.out(NetworkUtil.class.getName(), "DOWNLOAD_URL_RESP_NO_OK result=" + info.resultCode + " respCode:" + statuscode +" url:" + urlString);
				}
			}catch(Throwable t){
				t.printStackTrace();
				info.errorDetail = t.toString();
				// 与HttpCommunicator.getConnect()保持一致，处理SocketTimeoutException、ConnectException
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
					info.resultCode = DOWNLOAD_EXCEPTION;
				}
				
				if(doneExecute == false && isProxyConnectException){
					if(useProxy){
						// 表明使用的proxy并且在执行httpURLConnection.connect()时发生的异常
						// 则不使用proxy，直连!
						forceDirect = true;
					}else{
						// 如果不使用proxy且直连失败了,恢复到使用代理
						forceDirect = false;
					}
					
					LogOut.out(NetworkUtil.class.getName(), "change forceDirect:"+forceDirect+" doneConnect:" + doneExecute +" isProxyConnectEx:" + isProxyConnectException+" useProxy:" + useProxy);
				}
				LogOut.out(NetworkUtil.class.getName(), "Download fail resultCode="+info.resultCode+". url=" + info.urlOriginal +" exception:"+t.toString());
			}finally{
				try{
					if(os != null){
						os.close();
					}
					if(is != null){
						is.close();
					}
				}catch(Exception e){
					e.printStackTrace();
				}
			}
			
			
			tryCount ++;
			LogOut.out(NetworkUtil.class.getName(), "Download. result=" + info.resultCode + ", url=" + info.urlOriginal);
			need2try = need2Try(context, info.resultCode, tryCount, MAX_RETRY_COUNT);
			if(need2try && info.resultCode != DOWNLOAD_HTTP_CONNECT_TIMEOUT && info.resultCode != DOWNLOAD_HTTP_SO_TIMEOUT){
				// 需要重试且不是连接超时的错误，才等个5s.链接超时错误时，已经等了太久了，不要再等5s了，浪费时间
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
		boolean bool = resultCode != DOWNLOAD_SUCCESS// 下载成功，不重试  
				&& resultCode != DOWNLOAD_USER_CANCEL// 用户取消的，不重试
				&& tryCount < maxTry//
				&& isNetSupport(context);// 如果下载不成功,并且网络可用,才重新下载
		return bool;
	}
	public static String ping(String domain){
		String line = "";
		InputStream is = null;
		try {
			line = "/nping -c 1 " + domain;
			// -c 1:表示ping的次数为1次。
			Process p = Runtime.getRuntime().exec("ping -c 1 " + domain);
			// 等待该命令执行完毕。
			int status = p.waitFor();
			if (status == 0) {
				// 正常退出
				line += "Pass";
			} else {
				// 异常退出
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
    
	/**判断此网络是否为mobile
	 * 注：适应三星新定义的双卡双待mobile类型
	 * @param netInfo
	 * @return
	 */
	public static boolean isMobileNetworkInfo(final NetworkInfo netInfo) {
		if(ConnectivityManager.TYPE_MOBILE == netInfo.getType() || ConnectivityManager.TYPE_MOBILE + 50 == netInfo.getType()){
			return true;
		}else {
			return false;
		}
	}
}