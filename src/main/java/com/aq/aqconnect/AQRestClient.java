package com.aq.aqconnect;


import org.apache.http.HttpHost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
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
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class AQRestClient {


    private final AQRestClient aqRESTClient = new AQRestClient();
    private final JSONParser jsonParser = new JSONParser();

    //Base URL and extensions
    private String BASE_URL;
    private String API_ENDPOINT;
    private int PROXY_PORT = 80;
    private String PROXY_HOST;
    private Boolean DISABLE_SSL_CHECKS = false;


    public String getBaseURL() {
        return BASE_URL;
    }

    public String getResultExternalAccessURL(String jobPid, String tenantCode) {
        return String.format(getBaseURL() + AQConstants.EXT_JOB_WEB_LINK, tenantCode, jobPid);
    }

    public String getAbortURL(String jobPid) {
        return String.format(getBaseURL() + AQConstants.JOB_WEB_LINK, jobPid);
    }

    public void setUpBaseURL(String baseURL, String tenantCode) {
        BASE_URL = baseURL.charAt(baseURL.length() - 1) == '/'?(baseURL):(baseURL + '/');
        API_ENDPOINT =  BASE_URL + "awb/api/" + AQConstants.API_VERSION + "/" + tenantCode;
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
        }
        return null;
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
                    httpResponse.getEntity().getContent(), StandardCharsets.UTF_8));
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
        httpPut.setEntity(new AQUtils().getRunParam(jobId, runParam));
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpPut);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    httpResponse.getEntity().getContent(), StandardCharsets.UTF_8));
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
        httpPost.setEntity(new AQUtils().getRunParam(jobId, runParam));
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
            if (httpResponse.getStatusLine().getStatusCode() == 200 || httpResponse.getStatusLine().getStatusCode() == 204) {
                return "";
            }
            if(httpResponse.getStatusLine().getStatusCode() == 404) {
                return "Connection request failed. Please check the URL and Tenant Code.";
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
