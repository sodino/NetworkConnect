package lab.sodino.util;

public class DownloadInfo {
	public static final int ACTION_NONE = 0;
	/**����ֱ�Ӷ����ڴ档<br/>*/
	public static final int ACTION_READ = 1;
	/**����ֱ�Ӵ浽���ء�<br/>*/
	public static final int ACTION_SAVE = 2;
	
	/**��¼ԭʼ��url��*/
	public String urlOriginal;
	/**��ʶ��ǰ������*/
	public int task;
	/**��ǰ���ӵĽ����*/
	public int resultCode;
	
	/**�洢ֱ������ʱ��ȡ�������ݡ�<br/>
	 * ���ڶ����ݡ�<br/>*/
	public byte[] data;
	/**
	 * @see ACTION_READ
	 * @see ACTION_SAVE
	 * */
	public int dataAction;
	
	/**��¼���ع����з������쳣���顣*/
	public String errorDetail;
}
