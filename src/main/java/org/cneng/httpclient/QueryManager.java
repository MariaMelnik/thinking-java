package org.cneng.httpclient;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.alibaba.fastjson.JSON;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.Args;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.cneng.httpclient.Utils.*;
import static org.cneng.httpclient.ConfigUtil.get;

/**
 * 工商局查询管理器
 *
 * @author XiongNeng
 * @version 1.0
 * @since 2015/2/3
 */
public class QueryManager {
    private static final Logger _log = LoggerFactory.getLogger(QueryManager.class);
    /**
     * 首页地址
     */
    private String homepageUrl = "http://gsxt.gdgs.gov.cn/aiccips/";
    /**
     * 验证码图片生成地址
     */
    private String verifyPicUrl = "http://gsxt.gdgs.gov.cn/aiccips/verify.html";
    /**
     * 验证码服务器验证地址
     */
    private String checkCodeUrl = "http://gsxt.gdgs.gov.cn/aiccips/CheckEntContext/checkCode.html";
    /**
     * 信息显示URL
     */
    private String showInfoUrl = "http://gsxt.gdgs.gov.cn/aiccips/CheckEntContext/showInfo.html";
    /**
     * 下载的验证码图片文件夹
     */
    private String outdir = "D:/work/";

    private static final QueryManager instance = new QueryManager();

    public static QueryManager getInstance() {
        return instance;
    }

    private QueryManager() {
        ConfigUtil.getInstance();
        homepageUrl = get("homepageUrl");
        verifyPicUrl = get("verifyPicUrl");
        checkCodeUrl = get("checkCodeUrl");
        showInfoUrl = get("showInfoUrl");
        outdir = get("outdir");
    }

    /**
     * 工商局网站上面通过关键字搜索企业信息
     *
     * @param keyword 关键字：注册号或者是企业名称
     * @return 企业地址
     */
    public String search(String keyword) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            _log.info(sdf.format(new Date()));
            // 第一步：访问首页获取sessionid
            String jsessionid = fetchSessionId();
            // 第二步：先下载验证码图片到本地
            String[] downloads = downloadVerifyPic(jsessionid);
            // 第三步：获取验证码
            String checkcode = CheckCodeClient.checkCode(downloads[0]);
            // 第四步：调用服务器的验证码认证接口
            CheckCodeResult checkCodeResult = authenticate(keyword, checkcode, jsessionid);
            // 第五步：调用查询接口查询结果列表
            if ("1".equals(checkCodeResult.getFlag())) {
                String searchPage = showInfo(checkCodeResult.getTextfield(), checkcode, jsessionid);
                // 第六步：解析出第一条链接地址
                String link = JSoupUtil.parseLink(searchPage);
                // 第七步：点击详情链接，获取详情HTML页面
                String detailHtml = showDetail(link, jsessionid);
                // 第八步：解析详情HTML页面，获取最后的地址
                return JSoupUtil.parseLocation(detailHtml);
            } else {
                _log.info("-----------------error------------------" );
            }
            _log.info(sdf.format(new Date()));
            // ---------------------------------------------------------------------------------
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * 先访问首页获取SessionID
     *
     * @return http://gsxt.gdgs.gov.cn/
     * @throws Exception
     */
    private String fetchSessionId() throws Exception {
        RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.BEST_MATCH).build();
        CookieStore cookieStore = new BasicCookieStore();
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        CloseableHttpClient httpclient = HttpClients.custom().
                setDefaultRequestConfig(globalConfig).setDefaultCookieStore(cookieStore).build();
        try {
            HttpGet httpget = new HttpGet(homepageUrl);
            _log.info("Executing request " + httpget.getRequestLine());
            // 所有请求的通用header：
            httpget.addHeader("User-Agent",
                    "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:33.0) Gecko/20100101 Firefox/33.0");
            httpget.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            httpget.addHeader("Accept-Language", "zh-cn,zh;q=0.8,en-us;q=0.5,en;q=0.3");
            httpget.addHeader("Accept-Encoding", "gzip, deflate");
            httpget.addHeader("Connection", "keep-alive");

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                public String handleResponse(
                        final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        return null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            httpclient.execute(httpget, responseHandler);
            List<Cookie> cookies = context.getCookieStore().getCookies();
            for (Cookie cookie : cookies) {
                if ("JSESSIONID".equals(cookie.getName())) {
                    _log.info(cookie.getName() + ":" + cookie.getValue());
                    _log.info("******************************");
                    return cookie.getValue();
                }
            }
        } finally {
            httpclient.close();
        }
        return null;
    }

    /**
     * 下载验证码图片
     *
     * @return [下载图片地址, Cookie中的JSESSIONID]
     * @throws Exception
     */
    private String[] downloadVerifyPic(String jsessionId) throws Exception {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet httpget = new HttpGet(verifyPicUrl + "?random=" + Math.random());
            _log.info("Executing request " + httpget.getRequestLine());
            // 所有请求的通用header：
            httpget.addHeader("Accept", "image/png,image/*;q=0.8,*/*;q=0.5");
            httpget.addHeader("Cookie", "JSESSIONID=" + jsessionId);

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                public String handleResponse(
                        final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? doDownload(entity, outdir) : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            String doawloadPic = httpclient.execute(httpget, responseHandler);
            return new String[]{doawloadPic, jsessionId};
        } finally {
            httpclient.close();
        }
    }

    private String doDownload(final HttpEntity entity, String outdir) throws IOException {
        String fullname = outdir + randomUUID() + ".png";
        OutputStream outputStream = new FileOutputStream(new File(fullname));
        Args.notNull(entity, "Entity");
        final InputStream instream = entity.getContent();
        if (instream == null) {
            return null;
        }
        try {
            Args.check(entity.getContentLength() <= Integer.MAX_VALUE,
                    "HTTP entity too large to be buffered in memory");
            final byte[] buff = new byte[4096];
            int readed;
            while ((readed = instream.read(buff)) != -1) {
                byte[] temp = new byte[readed];
                System.arraycopy(buff, 0, temp, 0, readed);   // 这句是关键
                outputStream.write(temp);
            }
        } finally {
            instream.close();
            outputStream.close();
        }
        return fullname;
    }

    /**
     * 验证码服务器验证接口
     *
     * @param keywork    关键字
     * @param checkcode  验证码
     * @param jsessionid jsessionid
     * @return 结果
     * @throws Exception
     */
    private CheckCodeResult authenticate(String keywork, String checkcode, String jsessionid) throws Exception {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpPost httppost = new HttpPost(checkCodeUrl);
            _log.info("Executing request " + httppost.getRequestLine());

            // 所有请求的通用header：
            httppost.addHeader("Accept", "application/json, text/javascript, */*; q=0.01");
            httppost.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36" +
                    " (KHTML, like Gecko) Chrome/38.0.2125.111 Safari/537.36");
            httppost.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            httppost.addHeader("Accept-Encoding", "gzip,deflate");
            httppost.addHeader("Accept-Language", "zh-CN,zh;q=0.8,en;q=0.6");
            httppost.addHeader("Cookie", "JSESSIONID=" + jsessionid);

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("textfield", keywork));
            params.add(new BasicNameValuePair("code", checkcode));
            UrlEncodedFormEntity reqEntity = new UrlEncodedFormEntity(params, "UTF-8");
            httppost.setEntity(reqEntity);
            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                public String handleResponse(
                        final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity, "UTF-8") : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            String responseBody = httpclient.execute(httppost, responseHandler);
            _log.info("----------------------------------------");
            _log.info(responseBody);
            return JSON.parseObject(responseBody, CheckCodeResult.class);
        } finally {
            httpclient.close();
        }
    }

    /**
     * 查询列表结果
     *
     * @param textfield  验证码验证后返回的关键字
     * @param checkcode  验证码
     * @param jsessionid sessionid
     * @return 查询页面
     * @throws Exception
     */
    private String showInfo(String textfield, String checkcode, String jsessionid) throws Exception {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpPost httppost = new HttpPost(showInfoUrl);
            _log.info("Executing request " + httppost.getRequestLine());

            // 所有请求的通用header：
            httppost.addHeader("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            httppost.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36" +
                    " (KHTML, like Gecko) Chrome/38.0.2125.111 Safari/537.36");
            httppost.addHeader("Content-Type", "application/x-www-form-urlencoded");
            httppost.addHeader("Accept-Encoding", "gzip,deflate");
            httppost.addHeader("Accept-Language", "zh-CN,zh;q=0.8,en;q=0.6");
            httppost.addHeader("Cookie", "JSESSIONID=" + jsessionid);

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("textfield", textfield));
            params.add(new BasicNameValuePair("code", checkcode));
            _log.info("textfield=" + textfield + "|||" + "code=" + checkcode);
            UrlEncodedFormEntity reqEntity = new UrlEncodedFormEntity(params);
            httppost.setEntity(reqEntity);

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                public String handleResponse(
                        final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity, "UTF-8") : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            String responseBody = httpclient.execute(httppost, responseHandler);
            _log.info("----------------------------------------");
//            _log.info(responseBody);
            return responseBody;
        } finally {
            httpclient.close();
        }
    }

    /**
     * 查询详情
     *
     * @param detailUrl  详情页面链接
     * @param jsessionid sessionid
     * @return 详情页面
     * @throws Exception
     */
    private String showDetail(String detailUrl, String jsessionid) throws Exception {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet httpget = new HttpGet(detailUrl);
            _log.info("Executing request " + httpget.getRequestLine());

            // 所有请求的通用header：
            httpget.addHeader("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            httpget.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36" +
                    " (KHTML, like Gecko) Chrome/38.0.2125.111 Safari/537.36");
            httpget.addHeader("Accept-Encoding", "gzip,deflate,sdch");
            httpget.addHeader("Accept-Language", "zh-CN,zh;q=0.8,en;q=0.6");
            httpget.addHeader("Cookie", "JSESSIONID=" + jsessionid);

            // Create a custom response handler
            ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
                public String handleResponse(
                        final HttpResponse response) throws ClientProtocolException, IOException {
                    int status = response.getStatusLine().getStatusCode();
                    if (status >= 200 && status < 300) {
                        HttpEntity entity = response.getEntity();
                        return entity != null ? EntityUtils.toString(entity, "UTF-8") : null;
                    } else {
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                }
            };
            String responseBody = httpclient.execute(httpget, responseHandler);
            _log.info("----------------------------------------");
//            _log.info(responseBody);
            return responseBody;
        } finally {
            httpclient.close();
        }
    }

    private static class CheckCodeResult {
        private String flag;
        private String textfield;
        private String tip;

        /**
         * 获取 textfield.
         *
         * @return textfield.
         */
        public String getTextfield() {
            return textfield;
        }

        /**
         * 获取 flag.
         *
         * @return flag.
         */
        public String getFlag() {
            return flag;
        }

        /**
         * 设置 textfield.
         *
         * @param textfield textfield.
         */
        public void setTextfield(String textfield) {
            this.textfield = textfield;
        }

        /**
         * 设置 flag.
         *
         * @param flag flag.
         */
        public void setFlag(String flag) {
            this.flag = flag;
        }

        /**
         * 设置 tip.
         *
         * @param tip tip.
         */
        public void setTip(String tip) {
            this.tip = tip;
        }

        /**
         * 获取 tip.
         *
         * @return tip.
         */
        public String getTip() {
            return tip;
        }
    }

    public static void main(String[] args) throws Exception {
        instance.search("广州云宏信息科技股份有限公司");
    }
}
