package lab.sodino.network;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import lab.sodino.util.DownloadInfo;
import lab.sodino.util.LogOut;
import lab.sodino.util.NetworkUtil;
import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
public class MainActivity extends Activity {
	public static final int CLEAR_TEXT = 0;
	public static final int APPEND_TEXT = 1;
	private TextView txtInfo;
	private Button btnPing;
	private Button btnConnect;
	private Button btnClear;
	private BtnListener btnListener;
	private Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case APPEND_TEXT:
				String content = msg.obj.toString();
				txtInfo.setText("/n" + content);
				break;
			case CLEAR_TEXT:
				txtInfo.setText("");
				break;
			}
		}
	};
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_layout);
		btnListener = new BtnListener();
		txtInfo = (TextView) findViewById(R.id.txtInfo);
		btnPing = (Button) findViewById(R.id.btnPing);
		btnPing.setOnClickListener(btnListener);
		btnConnect = (Button) findViewById(R.id.btnConnect);
		btnConnect.setOnClickListener(btnListener);
		btnClear = (Button) findViewById(R.id.btnClear);
		btnClear.setOnClickListener(btnListener);
	}
	/**
	 * @param domain
	 *            指定的域名如(www.google.com)或IP地址。
	 */
	private void doPing(final String domain) {
		new Thread() {
			public void run() {
				String line = NetworkUtil.ping(domain);
				Message msg = new Message();
				msg.what = APPEND_TEXT;
				msg.obj = line;
				handler.sendMessage(msg);
			}
		}.start();
	}
	private void go2Network() {
		new Thread() {
			public void run() {
				String url = "http://imgcache.qq.com/ac/www_tencent/en-us/images/sitelogo_en-us.gif";
				DownloadInfo info = new DownloadInfo();
				info.urlOriginal = url;
				info.dataAction = DownloadInfo.ACTION_SAVE;
				
				NetworkUtil.download(MainActivity.this, info);
				LogOut.out(this, "info.result=" + info.resultCode);
			}
		}.start();
	}
	class BtnListener implements Button.OnClickListener {
		public void onClick(View view) {
			if (view == btnPing) {
				doPing("www.google.com");
//				doPing("10.0.0.172");
			} else if (view == btnConnect) {
				go2Network();
			} else if (view == btnClear) {
				Message msg = new Message();
				msg.what = CLEAR_TEXT;
				handler.sendMessage(msg);
			}
		}
	}
}