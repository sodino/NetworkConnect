package lab.sodino.util;

public class LogOut {
	public static final String LOG_TAG = "ANDROID_LAB";
	/**
	 * 调试输出方法。<br/>
	 * 输出样本：<br/>
	 * 08-10 09:40:24.940: DEBUG/ANDROID_LAB(28558):
	 * lab.sodino.androidlab.MainActivity->call onCreate() <br/>
	 * added by sodino
	 * 
	 * @param Object
	 *            obj 调用调试输出的类。当在静态域中时请直接用该域所属的类名String进行赋值。
	 * @param String
	 *            info 待输出的信息。
	 * */
	public static void out(Object obj, String info) {
		if (obj instanceof String) {
			android.util.Log.d(LOG_TAG, getClassNameByStackIndex(4) + "->" + info);
		} else {
			android.util.Log.d(LOG_TAG, getClassNameByStackIndex(4) + "->" + info);
		}
	}

	/**
	 * 辅助调试输出方法。用于在短时间内有大量相同格式或相同内容的输出，在方法中的第二个参数中设置辅助Tag可将这些类似的大量内容归类到一个全新的Tag中
	 * ，方法Tag信息的查找。<br/>
	 * 输出样本：<br/>
	 * 08-10 09:40:24.940: DEBUG/ANDROID_LAB_TAG(28558):
	 * lab.sodino.androidlab.MainActivity
	 * ->img1[http://www.sodino.com/dsfwetrtssd/lskdfjs.jpg respondCode=200] <br/>
	 * added by sodino
	 * 
	 * @param Object
	 *            obj 调用调试输出的类。当在静态域中时请直接用该域所属的类名String进行赋值。
	 * @param String
	 *            tag 辅助的Tag。
	 * @param String
	 *            info 待输出的信息。
	 * */
	public static void out(Object obj, String tag, String info) {
			if (obj instanceof String) {
				android.util.Log.d(LOG_TAG + "_" + tag, getClassNameByStackIndex(4) + "->" + info);
			} else {
				android.util.Log.d(LOG_TAG + "_" + tag, getClassNameByStackIndex(4) + "->" + info);
			}
	}

	public static String getClassNameByStackIndex(int index) {
		StackTraceElement[] traces = Thread.currentThread().getStackTrace();
		if (index < 0 || index >= traces.length) {
			return "";
		}
		String name = traces[index].getClassName();
		return name;
	}

	// 打log的函数（调用这个函数的函数），是在函数栈的第5层
	public static int e(String msg) {
		return android.util.Log.d(LOG_TAG,getClassNameByStackIndex(4)+"->"+msg);
	}

}