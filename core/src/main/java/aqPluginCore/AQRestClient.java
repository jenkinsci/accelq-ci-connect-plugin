package aqPluginCore;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class AQRestClient {


    private static final AQRestClient aqRESTClient = new AQRestClient();
    private final JSONParser jsonParser = new JSONParser();

    //Base URL and extensions
    private static String BASE_URL;
    private static String API_ENDPOINT;

    private static int PROXY_PORT = 80;
    private static String PROXY_HOST;
    private static Boolean DISABLE_SSL_CHECKS = false;


    public static AQRestClient getInstance() {
        return aqRESTClient;
    }

    public String getBaseURL() {
        return BASE_URL;
    }

    public String getResultExternalAccessURL(String jobPid, String tenantCode, String projectCode) {
        return String.format(getBaseURL() + AQConstants.EXT_JOB_WEB_LINK, tenantCode, projectCode, jobPid);
    }

    public String getAbortURL(String jobPid) {
        return String.format(getBaseURL() + AQConstants.JOB_WEB_LINK, jobPid);
    }

    public void setUpBaseURL(String baseURL, String tenantCode, String projectCode) {
        BASE_URL = baseURL.charAt(baseURL.length() - 1) == '/'?(baseURL):(baseURL + '/');
        API_ENDPOINT =  BASE_URL + "awb/api/" + AQConstants.API_VERSION + "/" + tenantCode + "/projects/" + projectCode;
    }

    private CloseableHttpClient getHttpsClient() {
        try {
            HttpClientBuilder hcb = null;
            if (DISABLE_SSL_CHECKS) {
                SSLContext sslContext = new SSLContextBuilder()
                    .loadTrustMaterial(null, new TrustStrategy() {
                    @Override
                    public boolean isTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                        return true;
                    }
                }).build();
                hcb = HttpClients.custom()
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier(new NoopHostnameVerifier());
            } else {
                hcb = HttpClients.custom()
                    .setSSLContext(SSLContext.getDefault())
                    .setSSLHostnameVerifier(new DefaultHostnameVerifier());
            }
            if (PROXY_HOST != null && !PROXY_HOST.equals("")) {
                HttpHost hh = new HttpHost(PROXY_HOST, PROXY_PORT);
                hcb.setProxy(hh);
            }
            CloseableHttpClient client = hcb.build();
            return client;
        } catch(Exception e) {
            return null;
        }
    }

    public JSONObject getJobSummary(long runPid, String apiKey, String userId) {
        CloseableHttpClient httpClient = getHttpsClient();

        HttpGet httpGet = new HttpGet(API_ENDPOINT + "/runs/" + runPid);
        httpGet.addHeader("User-Agent", AQConstants.USER_AGENT);
        httpGet.addHeader("API_KEY", apiKey);
        httpGet.addHeader("USER_ID", userId);
        httpGet.addHeader("Content-Type", "application/json");
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpGet);

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    httpResponse.getEntity().getContent()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();
            JSONObject summaryObj = (JSONObject) jsonParser.parse(response.toString());
            httpClient.close();
            return summaryObj;
        } catch(IOException ioe) {
            ioe.printStackTrace();
            return null;
        } catch (ParseException pe) {
            pe.printStackTrace();
            return null;
        }
    }

    public JSONObject triggerJob(String apiKey, String userId, String jobId, String runParam) throws IOException,
            ParseException {
        CloseableHttpClient httpClient = getHttpsClient();
        HttpPut httpPut = new HttpPut(API_ENDPOINT + "/jobs/" + jobId + "/trigger-ci-job");
        httpPut.addHeader("User-Agent", AQConstants.USER_AGENT);
        httpPut.addHeader("API_KEY", apiKey);
        httpPut.addHeader("USER_ID", userId);
        httpPut.addHeader("Content-Type", "application/json");
        
        if(runParam != null && !runParam.equals("")) {
            JSONParser parser = new JSONParser();
            JSONObject jsonObj = new JSONObject();
            JSONObject json = (JSONObject) parser.parse(runParam);
            jsonObj.put("jobPid", Integer.parseInt(jobId));
            jsonObj.put("runProperties", json);
            StringEntity requestEntity = new StringEntity(jsonObj.toJSONString(), org.apache.http.entity.ContentType.APPLICATION_JSON);
            httpPut.setEntity(requestEntity);
        } else {
            JSONObject json = new JSONObject();
            json.put("jobPid", Integer.parseInt(jobId));
            StringEntity requestEntity = new StringEntity(json.toJSONString(), org.apache.http.entity.ContentType.APPLICATION_JSON);
            httpPut.setEntity(requestEntity);
        }
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpPut);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    httpResponse.getEntity().getContent()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();
            JSONArray jobInfo = (JSONArray) jsonParser.parse(response.toString());
            if (httpResponse.getStatusLine().getStatusCode() == 200 || httpResponse.getStatusLine().getStatusCode() == 204) {
                JSONObject obj = new JSONObject();
                obj.put("pid", jobInfo.get(0));
                return obj;
            } else {
                // error object
                return (JSONObject) jsonParser.parse(response.toString());                
            }
        } catch(IOException ioe) {
            ioe.printStackTrace();
            return null;
        } catch (Exception pe) {
            pe.printStackTrace();
            return null;
        } finally {
            httpClient.close();
        }
    }
    public String testConnection(String apiKey, String userId, String jobId, String runParam)
            throws ParseException, IOException {
        CloseableHttpClient httpClient = getHttpsClient();
        HttpPost httpPost = new HttpPost(API_ENDPOINT + "/jobs/" + jobId + "/validate-ci-job");
        httpPost.addHeader("User-Agent", AQConstants.USER_AGENT);
        httpPost.addHeader("API_KEY", apiKey);
        httpPost.addHeader("USER_ID", userId);
        httpPost.addHeader("Content-Type", "application/json");
        
        if(runParam != null && !runParam.equals("")) {
            // add it to list
            JSONParser parser = new JSONParser();  
            JSONObject json = (JSONObject) parser.parse(runParam);
            json.put("jobPid", Integer.parseInt(jobId));
            StringEntity requestEntity = new StringEntity(json.toJSONString(), org.apache.http.entity.ContentType.APPLICATION_JSON);
            httpPost.setEntity(requestEntity);
        } else {
            JSONObject json = new JSONObject();
            json.put("jobPid", Integer.parseInt(jobId));
            StringEntity requestEntity = new StringEntity(json.toJSONString(), org.apache.http.entity.ContentType.APPLICATION_JSON);
            httpPost.setEntity(requestEntity);
        }

        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
            if (httpResponse.getStatusLine().getStatusCode() == 200 || httpResponse.getStatusLine().getStatusCode() == 204) {
                return "";
            }
            if(httpResponse.getStatusLine().getStatusCode() == 404) {
                return "Connection request failed. Please check the URL, Project Code and Tenant Code.";
            }
            if (httpResponse.getStatusLine().getStatusCode() == 401) {
                // user id and api key is wrong
                return "Connection request failed. Please check connection parameters.";
            } else if (httpResponse.getStatusLine().getStatusCode() != 200){
                return "Template Job ID does not exist.";
            }
            return "";
        } catch(IOException ioe) {
            ioe.printStackTrace();
            return null;
        } catch (Exception pe) {
            pe.printStackTrace();
            return null;
        } finally {
            httpClient.close();
        }
    }
    
    public void setUpProxy(String proxyHost, int proxyPort) {
        PROXY_HOST = proxyHost;
        PROXY_PORT = proxyPort == 0 ? 80 : proxyPort;
    }

    public void disableSSLChecks(Boolean check) {
        DISABLE_SSL_CHECKS = check || false;
    }
}
