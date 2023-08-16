package com.aq.aqconnect;


import java.net.URL;
import java.util.regex.Pattern;

public class AQFormValidate {
    
    public String validateJobID(String value) {
        try {
            int x = Integer.parseInt(value);
            if (x <= 0) {
                return "Must be a number greater than 0";
            }
     } 
     catch (NumberFormatException e) {
         return "Not a number";
     }
        return null;

    }
    public String validateTenantCode(String value) {
        return this.validateGenericField(value);
    }
    public String validateAppURL(String value) {
        try {
            new URL(value);
        } 
        catch (Exception e) {
            return "Not a URL";
        }
        return null;
    }
    public String validateGenericField(String value) {
        try {
            if (value == null || value.length() == 0)return "Cannot be empty";
        } 
        catch (Exception e) {
            return "Cannot be empty";
        }
        return null;
    } 
    public String validateAPIKey(String value) {
        return this.validateGenericField(value);
    }
    public String validateUserId(String value) {
        try {
            String emailRegex = "^(([^<>()[\\]\\\\.,;:\\s@\\\"]+(\\.[^<>()[\\]\\\\.,;:\\s@\\\"]+)*)|(\\\".+\\\"))@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$";
            Pattern pat = Pattern.compile(emailRegex); 
            if (value == null || value.length() == 0) return "Cannot be empty";
            else if (!pat.matcher(value).matches()) return "User ID must be in email format";
        }catch(Exception e) {}
        return null;
    }
}
