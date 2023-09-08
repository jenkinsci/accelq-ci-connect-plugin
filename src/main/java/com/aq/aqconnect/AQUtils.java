package com.aq.aqconnect;
import org.apache.http.entity.StringEntity;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.Date;


public class AQUtils {
    public StringEntity getRunParam(String jobId, String runParam, int expireTimeInMinutes) throws ParseException {
        JSONObject jsonObj = new JSONObject();
        if(runParam != null && !runParam.equals("")) {
            jsonObj.put("runProperties", (JSONObject) new JSONParser().parse(runParam));
        }
        jsonObj.put("jobPid", Integer.parseInt(jobId));
        jsonObj.put("expireTimeInMinutes", expireTimeInMinutes);
        StringEntity requestEntity = new StringEntity(jsonObj.toJSONString(), org.apache.http.entity.ContentType.APPLICATION_JSON);
        return requestEntity;
    }
    public String getRunParamJsonPayload(String runParamStr) {
        if(runParamStr == null || runParamStr.trim().length() == 0)
            return null;
        try {
          new JSONParser().parse(runParamStr);
          return runParamStr;
        }catch(Exception e) {
            JSONObject json = new JSONObject();
            String[] splitOnAmp = runParamStr.split("&");
            for(String split: splitOnAmp) {
                String[] splitOnEquals = split.split("=");
                if(splitOnEquals.length == 2) {
                    String key = splitOnEquals[0].trim(), value = splitOnEquals[1].trim();
                    if(!key.equals("") && !value.equals("")) {
                        json.put(key, value);
                    }
                }
            }
            return json.toJSONString();
        }

    }
    public String getFormattedTime(long a, long b) {
        Date startDate = new Date(a);
        Date endDate = new Date(b);
        long difference_In_Time
            = endDate.getTime() - startDate.getTime();
        long difference_In_Seconds
            = (difference_In_Time
            / 1000)
            % 60;
        long difference_In_Minutes
            = (difference_In_Time
            / (1000 * 60))
            % 60;
        long difference_In_Hours
            = (difference_In_Time
            / (1000 * 60 * 60))
            % 24;
        long difference_In_Days
            = (difference_In_Time
            / (1000 * 60 * 60 * 24))
            % 365;
        String res = "";
        if (difference_In_Days != Long.valueOf(0)) {
            res += (difference_In_Days > Long.valueOf(1) ? (difference_In_Days + " days") : (difference_In_Days + " day"));
        }
        if (difference_In_Hours != Long.valueOf(0)) {
            res += (difference_In_Hours > Long.valueOf(1) ? (difference_In_Hours + " hrs") : (difference_In_Hours + " hr"));
        }
        if (difference_In_Minutes != Long.valueOf(0)) {
            res += " " + (difference_In_Minutes > Long.valueOf(1) ? (difference_In_Minutes + " mins") : (difference_In_Minutes + " min"));
        }
        if (difference_In_Seconds != Long.valueOf(0)) {
            res += " " + (difference_In_Seconds > Long.valueOf(1) ? (difference_In_Seconds + " seconds") : (difference_In_Seconds + " second"));
        }
        return res;
    }
    public boolean isWaitTimeExceeded(long start, int maxWait) {
        return Math.floor((double)(System.currentTimeMillis() - start) / (1000 * 60)) > maxWait;
    }
}
