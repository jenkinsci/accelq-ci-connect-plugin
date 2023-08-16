package com.aq.aqconnect;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.util.Secret;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.json.simple.*;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintStream;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.json.simple.parser.ParseException;


public class AQPluginBuilderAction extends Recorder implements SimpleBuildStep {

    private String jobId;
    private Secret apiKey;
    private String appURL;
    private String userName;
    private String tenantCode;
    //run params
    private String runParamStr;
    private String proxyHost;
    private String proxyPort;
    private String stepFailureThreshold;
    private Boolean disableSSLCheck;

    @DataBoundConstructor
    public AQPluginBuilderAction(String jobId, Secret apiKey, String appURL, String runParamStr,
            String tenantCode, String userName, String proxyHost, String proxyPort, String stepFailureThreshold, Boolean disableSSLCheck) {
        this.jobId = jobId;
        this.apiKey = apiKey;
        this.appURL = appURL;
        this.runParamStr = runParamStr;
        this.tenantCode = tenantCode;
        this.userName = userName;
        this.proxyPort = proxyPort;
        this.proxyHost = proxyHost;
        this.stepFailureThreshold = stepFailureThreshold;
        this.disableSSLCheck = disableSSLCheck;
    }

    public Secret getApiKey() {
        return apiKey;
    }
    public String getUserName() {
        return userName;
    }

    public String getJobId() {
        return jobId;
    }

    public String getAppURL() {
        return appURL;
    }

    public String getRunParamStr() {
        return runParamStr;
    }

    public String getProxyPort() {
        return proxyPort;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public String getTenantCode() {
        return tenantCode;
    }
    public String getStepFailureThreshold() {
        return stepFailureThreshold;
    }
    public Boolean getSSLChecks() {
        return disableSSLCheck;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher,
            @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PrintStream out = listener.getLogger();
        AQRestClient aqRestClient = null;
        JSONObject summaryObj = null;
        long realJobPid = 0;
        try {
            aqRestClient = new AQRestClient();
            AQUtils aqUtils = new AQUtils();
            aqRestClient.setUpBaseURL(this.appURL, this.tenantCode);
            aqRestClient.disableSSLChecks(this.disableSSLCheck);
            if (this.proxyHost != null && this.proxyPort != null && this.proxyHost.length() > 0 && this.proxyPort.length() > 0) {
                aqRestClient.setUpProxy(this.proxyHost.trim(), Integer.parseInt(this.proxyPort.trim()));
            } else {
                aqRestClient.setUpProxy("", 0);
            }
            out.println("******************************************");
            out.println("*** Begin: ACCELQ Test Automation Step ***");
            out.println("******************************************");
            out.println();
            String runParamJsonPayload = aqUtils.getRunParamJsonPayload(this.runParamStr);
            // Test connection at runtime
            String res = aqRestClient.testConnection(this.apiKey.getPlainText(), this.userName, this.jobId, runParamJsonPayload);
            if (res == null) {
                throw new AQException("Connection Error: Something in plugin went wrong");
            } else if(res.length() > 0) {
                throw new AQException("Connection Error: " + res);
            }

            JSONObject realJobObj = aqRestClient.triggerJob(this.apiKey.getPlainText(), this.userName, this.jobId, runParamJsonPayload);

            if (realJobObj == null) {
                throw new AQException("Unable to submit the Job, check plugin log stack");
            }
            if (realJobObj.get("cause") != null) {
                throw new AQException((String)realJobObj.get("cause"));
            }
            realJobPid = (long) realJobObj.get("pid");
            long passCount = 0, failCount = 0, runningCount = 0, totalCount = 0, notRunCount = 0;
            String jobStatus = "";
            int attempt = 0;
            String threshold = this.stepFailureThreshold;
            if (threshold == null || threshold.equals("")) {
                threshold = "0";
            }
            boolean isDouble = threshold.indexOf(".") != -1;
            int failureThreshold = isDouble ? Double.valueOf(threshold).intValue() : Integer.parseInt(threshold);

            String resultAccessURL = aqRestClient.getResultExternalAccessURL(Long.toString(realJobPid), this.tenantCode);
            do {
                summaryObj = aqRestClient.getJobSummary(realJobPid, this.apiKey.getPlainText(), this.userName);
                if (summaryObj.get("cause") != null) {
                    throw new AQException((String) summaryObj.get("cause"));
                }
                if (summaryObj.get("summary") != null) {
                    summaryObj = (JSONObject) summaryObj.get("summary");
                }
                passCount = (Long) summaryObj.get("pass");
                failCount = (Long) summaryObj.get("fail");
                notRunCount = (Long) summaryObj.get("notRun");
                if (attempt == 0) {
                    String jobPurpose = (String) summaryObj.get("purpose");
                    String scenarioName = (String) summaryObj.get("scnName");
                    String testSuiteName = (String) summaryObj.get("testSuiteName");
                    Long totalTestCases = (Long) summaryObj.get("testcaseCount");
                    if (testSuiteName != null && testSuiteName.length() > 0) {
                        out.println("Test Suite Name: " + testSuiteName);
                    } else {
                        out.println("Scenario Name: " + scenarioName);
                    }
                    out.println("Purpose: " + jobPurpose);
                    out.println("Total Test Cases: " + totalTestCases);
                    out.println("Step Failure threshold: " + threshold);
                    out.println();
                    if (isDouble) {
                        out.println("Warning: Invalid value (" + threshold +") passed for Step Failure Threshold. Truncating the value to " + failureThreshold + " (Only integers between 0 and 100, and -1 are allowed).");
                    }
                    if (failureThreshold <= -2 || failureThreshold >= 101) {
                        out.println("Warning: Ignoring the Step Failure threshold. Invalid value (" + failureThreshold + ") passed. Valid values are 0 to 100, or -1 to ignore threshold.");
                        failureThreshold = 0;
                    }
                    out.println("Results Link: " + resultAccessURL);
                    out.println("Need to abort? Click on the link above, login to ACCELQ and abort the run.");
                    out.println();
                }
                jobStatus = ((String) summaryObj.get("status")).toUpperCase();
                if (jobStatus.equals(AQConstants.TEST_JOB_STATUS.COMPLETED.getStatus().toUpperCase())) {
                    res = " " + aqUtils.getFormattedTime((Long)summaryObj.get("startTimestamp"), (Long)summaryObj.get("completedTimestamp"));
                    out.println("Status: " + summaryObj.get("status").toString().toUpperCase() + " ("+res.trim()+")");
                } else {
                    out.println("Status: " + summaryObj.get("status").toString().toUpperCase());
                }
                totalCount = passCount + failCount + notRunCount;
                out.println("Total " + totalCount + ": "
                        + "" + passCount +" Pass / " + failCount + " Fail");
                out.println();
                if (jobStatus.equals(AQConstants.TEST_JOB_STATUS.SCHEDULED.getStatus().toUpperCase()))
                    ++attempt;
                if (attempt == AQConstants.JOB_PICKUP_RETRY_COUNT) {
                    throw new AQException("No agent available to pickup the job");
                }
                Thread.sleep(AQConstants.JOB_STATUS_POLL_TIME);
            } while (!jobStatus.equals(AQConstants.TEST_JOB_STATUS.COMPLETED.getStatus().toUpperCase())
                    && !jobStatus.equals(AQConstants.TEST_JOB_STATUS.ABORTED.getStatus().toUpperCase())
                    && !jobStatus.equals(AQConstants.TEST_JOB_STATUS.FAILED.getStatus().toUpperCase())
                    && !jobStatus.equals(AQConstants.TEST_JOB_STATUS.ERROR.getStatus().toUpperCase()));
            out.println("Results Link: " + resultAccessURL);
            out.println();

            double failCount_ = Long.valueOf(failCount).doubleValue();
            double totalCount_ = Long.valueOf(totalCount).doubleValue();
            int failedPercentage = (int) ((failCount_ / totalCount_) * 100);

            if (jobStatus.equals(AQConstants.TEST_JOB_STATUS.ABORTED.getStatus().toUpperCase())
                    || jobStatus.equals(AQConstants.TEST_JOB_STATUS.FAILED.getStatus().toUpperCase())
                    || jobStatus.equals(AQConstants.TEST_JOB_STATUS.ERROR.getStatus().toUpperCase())) {
                throw new AQException(AQConstants.LOG_DELIMITER + "Run Failed");
            } else if(failCount > 0) {
                if(failureThreshold != -1 && failedPercentage >= failureThreshold) {
                    throw new AQException(AQConstants.LOG_DELIMITER + "Automation test step failed (test case failure count exceeds the threshold limit)");
                }
            }
            run.setResult(Result.SUCCESS);
        } catch (ParseException e) {
            out.println(e);
            run.setResult(Result.FAILURE);
        } finally {
            out.println("**********************************************");
            out.println("*** Completed: ACCELQ Test Automation Step ***");
            out.println("**********************************************");
            out.println();
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            load();
        }
        
        @POST
        public FormValidation doTestConnection(@QueryParameter("appURL") final String appURL,
                                               @QueryParameter("apiKey") final String apiKey,
                                               @QueryParameter("jobId") final String jobId,
                                               @QueryParameter("userName") final String userName,
                                               @QueryParameter("tenantCode") final String tenantCode,
                                               @QueryParameter("runParamStr") final String runParamStr,
                                               @QueryParameter("proxyHost") final String proxyHost,
                                               @QueryParameter("proxyPort") final String proxyPort,
                                               @QueryParameter("stepFailureThreshold") final String stepFailureThreshold,
                                               @QueryParameter("disableSSLCheck") final Boolean disableSSLCheck,
                                               @AncestorInPath Job job) throws IOException, ServletException {

            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            // basic form validate
            AQFormValidate formValidate = new AQFormValidate();
            String emptyError = "Cannot be empty";
            if (Util.fixEmptyAndTrim(appURL) == null) {
                return FormValidation.error("ACCELQ App URL: " + emptyError);
            }
            String res = formValidate.validateAppURL(appURL);
            if (res != null) {
                return FormValidation.error("ACCELQ App URL: " + res);
            }
            if (Util.fixEmptyAndTrim(userName) == null) {
                return FormValidation.error("ACCELQ User ID: " + emptyError);
            }
            res = formValidate.validateUserId(userName);
            if (res != null) {
                return FormValidation.error("ACCELQ User ID: " + res);
            }
            if (Util.fixEmptyAndTrim(apiKey) == null) {
                return FormValidation.error("API Key: " + emptyError);
            }
            res = formValidate.validateAPIKey(apiKey);
            if (res != null) {
                return FormValidation.error("API Key: " + res);
            }
            if (Util.fixEmptyAndTrim(appURL) == null) {
                return FormValidation.error("Tenant Code: " + emptyError);
            }
            res = formValidate.validateTenantCode(tenantCode);
            if (res != null) {
                return FormValidation.error("Tenant Code: " + res);
            }
            if (Util.fixEmptyAndTrim(appURL) == null) {
                return FormValidation.error("ACCELQ CI Job ID: " + emptyError);
            }
            res = formValidate.validateJobID(jobId);
            if (res != null) {
                return FormValidation.error("ACCELQ CI Job ID: " + res);
            }
            try {
                // make call to backend to validate it
                AQRestClient aqRestClient = null;
                AQUtils aqUtils = new AQUtils();
                aqRestClient = new AQRestClient();
                String payload = aqUtils.getRunParamJsonPayload(runParamStr);
                aqRestClient.setUpBaseURL(appURL, tenantCode);
                aqRestClient.disableSSLChecks(disableSSLCheck);
                if (proxyHost != null && proxyPort != null && proxyHost.length() > 0 && proxyPort.length() > 0) {
                    aqRestClient.setUpProxy(proxyHost.trim(), Integer.parseInt(proxyPort.trim()));
                } else {
                    aqRestClient.setUpProxy("", 0);
                }
                res = aqRestClient.testConnection(apiKey, userName, jobId, payload);
                if (res == null) {
                    return FormValidation.error("Connection Error: Something in plugin went wrong");
                } else if(res.length() > 0) {
                    return FormValidation.error("Connection Error: " + res);
                }
            }catch (Exception e) {
                return FormValidation.error("Connection error: "+e.getMessage());
            }
            return FormValidation.ok("Success");
        }
        
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        public String getDisplayName() {
            return "ACCELQ Connect";
        }

    }

}
