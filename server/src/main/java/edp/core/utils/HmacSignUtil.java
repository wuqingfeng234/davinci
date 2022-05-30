package edp.core.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author zhengmeng
 * @version 01
 * @classname HmacSignUtil
 * @description 签名生成的工具类
 * @date 2021/1/27 13:41
 * @since 2021
 */
public class HmacSignUtil {
    private final static String headerAppKey = "X-HMAC-ACCESS-KEY";
    private final static String headerSign = "X-HMAC-SIGNATURE";
    private final static String headerDate = "Date";
    private final static String headerAlgorithm = "X-HMAC-ALGORITHM";
    private final static String headerAlgorithmVal = "hmac-sha256";
    private final static String hmacAlgorithm = "HmacSHA256";
    private final static String signedHeaders = "X-HMAC-SIGNED-HEADERS";


    /**
     * 获取签名所需的header各项
     * @param appKey
     * @param appSecret
     * @param uri
     * @param method
     * @return 签名相关的map，访问时直接拼到header里
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public static Map<String, String> createSignHeader(String appKey, String appSecret,
                                                       String uri, String method)
            throws NoSuchAlgorithmException, InvalidKeyException, MalformedURLException {
        return createSignHeader(appKey, appSecret, uri, method, null);
    }

    /**
     * 获取签名所需的header各项
     * @param appKey
     * @param appSecret
     * @param uri
     * @param method
     * @param header
     * @return 签名相关的map，访问时直接拼到header里
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public static Map<String, String> createSignHeader(String appKey, String appSecret, String uri, String method, Map<String, String> header) throws NoSuchAlgorithmException, InvalidKeyException, MalformedURLException {

        String dateStr = getDateStr();

        if(uri.startsWith("http://") || uri.startsWith("https://")){
        } else{
            if(!uri.startsWith("/")){
                uri = "/" + uri;
            }
            uri = "http://127.0.0.1" + uri;
        }

        URL url = new URL(uri);
        String signString = Splicing(method.toUpperCase()
                , url.getPath()
                , getSortQuerysString(url.getQuery())
//                , url.getQuery()
                , appKey
                , dateStr
                , getSortHeaderString(header));

        String sign = "";
        Mac hasher = Mac.getInstance(hmacAlgorithm);
        hasher.init(new SecretKeySpec(appSecret.getBytes(), hmacAlgorithm));
        byte[] hash = hasher.doFinal(signString.getBytes());
        sign = DatatypeConverter.printBase64Binary(hash);

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put(headerAppKey, appKey);
        headerMap.put(headerSign, sign);
        headerMap.put(headerDate, dateStr);
        headerMap.put(headerAlgorithm, headerAlgorithmVal);
        if (header != null && header.keySet().size() > 0)
        {
            headerMap.put(signedHeaders, getSignedHeader(header));
            for(String key : header.keySet()){
                String val = header.get(key) == null ? "" : header.get(key);
                headerMap.put(key, val);
            }
        }
        return headerMap;
    }

    private static String getSignedHeader(Map<String, String> header)
    {
        StringBuilder content = new StringBuilder();
        int i = 0;
        for (String key : header.keySet()) {
            if (i != 0)
            {
                content.append(';');
            }
            content.append(key);
            i++;
        }
        String headerStr = content.toString();
        // Console.WriteLine(signStr);
        return headerStr;
    }

    /**
     * 将url中的queryString参数排序后重新拼接
     * @param queryStr
     * @return 已排序和重新拼装的queryString
     */
    private static String getSortQuerysString(String queryStr){
        if(queryStr == null || queryStr.equals("")){
            return "";
        }
        Map<String, String> querys = new HashMap<>();
        String[] arr = queryStr.split("&");
        for(String item : arr){
            if(item == null || item.equals("")){
                continue;
            }
            String[] kvs = item.split("=");
            if(kvs.length > 1){
                querys.put(kvs[0], kvs[1]);
            } else {
                querys.put(kvs[0], "");
            }
        }


        StringBuilder content = new StringBuilder();
        List<String> keys = new ArrayList(querys.keySet());
        Collections.sort(keys);

        for(int i = 0; i < keys.size(); ++i) {
            String key = keys.get(i);
            String value = String.valueOf(querys.get(key));
            content.append(i == 0 ? "" : "&").append(key).append("=");
            if(value != null && !value.equals("")){
                content.append(value);
            }
        }

        return content.toString();
    }

    /**
     * 获取签名时间标签
     * @return 签名时间标签字符串
     */
    public static String getDateStr(){
//        if(true) return "Fri,12 Mar 2021 01:42:18 GMT";
        Calendar cd = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE,dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT")); // 设置时区为GMT
        String dateStr = sdf.format(cd.getTime());
        return dateStr;
    }



    /**
     * 拼接字符串
     * @param method 方法
     * @param uri uri
     * @param queryString 请求uri中的参数串
     * @param accessKey appKey
     * @param date GMT时间
     * @param headersString 需要签名的请求头拼接
     * @return 待签名字符串
     */
    private static String Splicing(String method,String uri,String queryString,String accessKey,String date,String headersString){
        // signing_string =  GET + \n + HTTP URI + \n + canonical_query_string + \n + access_key + \n + Date + \n + signed_headers_string。

        StringBuilder sb = new StringBuilder();
        sb.append(method.toUpperCase());
        sb.append("\n");
        sb.append(uri);
        sb.append("\n");
        // 如果请求数据为url
        if (queryString == null){
            queryString = "";
        }
        sb.append(queryString);
        sb.append("\n");
        sb.append(accessKey);
        sb.append("\n");
        sb.append(date);
        sb.append("\n");
        if (headersString != null && !headersString.equals("")){
            sb.append(headersString);
            sb.append("\n");
        }
        // 拼接字符串
        return sb.toString();
    }

    private static String getSortHeaderString(Map<String, String> header)
    {
        if(header == null || header.keySet().size() == 0) return "";
        List<String> keys = new ArrayList<>();
        for (String key : header.keySet())
        {
            keys.add(key);
        }
        keys.sort((k1, k2) -> k1.compareTo(k2));
        StringBuilder content = new StringBuilder();
        int i = 0;
        for (String key : keys) {
            if (i != 0)
            {
                content.append('\n');
            }

            content.append(key);
            String value = header.get(key);
            if (value == null) value = "";
            content.append(":");
            content.append(value);

            i++;
        }
        String signStr = content.toString();
        return signStr;
    }
}
