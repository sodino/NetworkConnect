package lab.sodino.util;

public class StringUtil {
	public static String getSubffixNameByUrl(String url){
		String name = url.substring(url.lastIndexOf("/") +1);
		return name;
	}
}
