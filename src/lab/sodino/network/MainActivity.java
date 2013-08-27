package lab.sodino.network;
import java.io.File;

import lab.sodino.util.DownloadInfo;
import lab.sodino.util.LogOut;
import lab.sodino.util.NetworkUtil;
import lab.sodino.util.StringUtil;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
public class MainActivity extends Activity {

	public static final String SAVE_FOLDER_PATH = Environment.getExternalStorageDirectory() + File.separator + "net_save" + File.separator;
	
	public static final int CLEAR_TEXT = 0;
	public static final int APPEND_TEXT = 1;
	public static final int DOWNLOAD_DONE = 2;
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
			case DOWNLOAD_DONE:
				updateView((DownloadInfo)msg.obj);
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
//				String url = "http://img1.gtimg.com/visual_page/72/ab/10033.jpg";
//				int action = DownloadInfo.ACTION_SAVE;
//				String url = "http://icon.solidot.org/js/base.js";
//				int action = DownloadInfo.ACTION_READ;
				String url = "http://imgcache.qq.com/club/item/parcel/9/10019.json";
				int action = DownloadInfo.ACTION_READ;
				
				DownloadInfo info = new DownloadInfo();
				info.urlOriginal = url;
				info.file = new File(SAVE_FOLDER_PATH + StringUtil.getSubffixNameByUrl(info.urlOriginal));
				info.dataAction = action;

//				NetworkUtil.downloadByJava(MainActivity.this, info);
				NetworkUtil.downloadByApache(MainActivity.this, info);
				LogOut.out(this, "info.result=" + info.resultCode);
				Message msg = handler.obtainMessage();
				msg.what = DOWNLOAD_DONE;
				msg.obj = info;
				handler.sendMessage(msg);
//				if(url.endsWith(".gif") || url.endsWith(".png") || url.endsWith(".jpg") || url.endsWith(".jpeg")){
//					
//				}
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
	
	private void updateView(DownloadInfo info) {
		String line = "info resultCode=" + info.resultCode +" error=" + info.errorDetail +" url=" + info.urlOriginal;
		if(info.resultCode == NetworkUtil.DOWNLOAD_SUCCESS){
			String fileName = info.file.getName().toLowerCase();
			if(fileName.endsWith("gif") || fileName.endsWith("jpg") || fileName.endsWith("jpeg") || fileName.endsWith("png")){
				Bitmap bit = BitmapFactory.decodeFile(info.file.getAbsolutePath());
				BitmapDrawable drawable = new BitmapDrawable(bit);
//				txtInfo.setCompoundDrawables(null, null, null, drawable);
				txtInfo.setCompoundDrawablesWithIntrinsicBounds(null, null, null, drawable);
				txtInfo.setText(line);
			} else if(info.dataAction == DownloadInfo.ACTION_READ){
				String content = new String(info.data);
				line = line +"\n\n" + content;
				txtInfo.setText(line);
			}
		}else {
			txtInfo.setText(line);
		}
	}
}