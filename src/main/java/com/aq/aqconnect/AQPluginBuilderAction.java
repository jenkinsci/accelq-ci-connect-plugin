package com.aq.aqconnect;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
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
import aqPluginCore.*;
import org.kohsuke.stapler.QueryParameter;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class AQPluginBuilderAction extends Recorder implements SimpleBuildStep {

    private String jobId;
    private String apiKey;
    private String projectCode;
    private String appURL;
    private String userName;
    private String tenantCode;
    //run params
    private String runParamStr;
    private String proxyHost;
    private String proxyPort;

    @DataBoundConstructor
    public AQPluginBuilderAction(String jobId, String apiKey, String projectCode, String appURL, String runParamStr,
            String tenantCode, String userName, String proxyHost, String proxyPort) {
        this.jobId = jobId;
        this.apiKey = apiKey;
        this.projectCode = projectCode;
        this.appURL = appURL;
        this.runParamStr = runParamStr;
        this.tenantCode = tenantCode;
        this.userName = userName;
        this.proxyPort = proxyPort;
        this.proxyHost = proxyHost;
    }

    public String getApiKey() {
        return apiKey;
    }
    public String getUserName() {
        return userName;
    }

    public String getJobId() {
        return jobId;
    }

    public String getProjectCode() {
        return projectCode;
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
            aqRestClient = AQRestClient.getInstance();
            AQUtils aqUtils = new AQUtils();
            aqRestClient.setUpBaseURL(this.appURL, this.tenantCode, this.projectCode);
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
            JSONObject realJobObj = aqRestClient.triggerJob(this.apiKey, this.userName, this.jobId, runParamJsonPayload);

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
            String resultAccessURL = aqRestClient.getResultExternalAccessURL(Long.toString(realJobPid), this.tenantCode, this.projectCode);
            do {
                summaryObj = aqRestClient.getJobSummary(realJobPid, this.apiKey, this.userName);
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
                    out.println();
                    out.println("Results Link: " + resultAccessURL);
                    out.println("Need to abort? Click on the link above, login to ACCELQ and abort the run.");
                    out.println();
                }
                jobStatus = ((String) summaryObj.get("status")).toUpperCase();
                if (jobStatus.equals(AQConstants.TEST_JOB_STATUS.COMPLETED.getStatus().toUpperCase())) {
                    String res = " " + aqUtils.getFormattedTime((Long)summaryObj.get("startTimestamp"), (Long)summaryObj.get("completedTimestamp"));
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

            if (failCount > 0 || jobStatus.equals(AQConstants.TEST_JOB_STATUS.ABORTED.getStatus().toUpperCase())
                    || jobStatus.equals(AQConstants.TEST_JOB_STATUS.FAILED.getStatus().toUpperCase())
                    || jobStatus.equals(AQConstants.TEST_JOB_STATUS.ERROR.getStatus().toUpperCase())) {
                throw new AQException(AQConstants.LOG_DELIMITER + "Run Failed");
            }
            out.println("**********************************************");
            out.println("*** Completed: ACCELQ Test Automation Step ***");
            out.println("**********************************************");
            out.println();
        } catch (InterruptedException e) {
            out.println("CATCHED ABORT ERROR");
            summaryObj = aqRestClient.getJobSummary(realJobPid, this.apiKey, this.userName);
            if (summaryObj.get("cause") != null) {
                throw new AQException((String) summaryObj.get("cause"));
            }
            if (summaryObj.get("summary") != null) {
                summaryObj = (JSONObject) summaryObj.get("summary");
            }
            out.println("Status: " + summaryObj.get("status"));
            out.println("Pass: " + (Long) summaryObj.get("pass"));
        } catch (Exception e) {
            out.println(e);
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
        
        public FormValidation doTestConnection(@QueryParameter("appURL") final String appURL,
                                               @QueryParameter("apiKey") final String apiKey,
                                               @QueryParameter("projectCode") final String projectCode,
                                               @QueryParameter("jobId") final String jobId,
                                               @QueryParameter("userName") final String userName,
                                               @QueryParameter("tenantCode") final String tenantCode,
                                               @QueryParameter("runParamStr") final String runParamStr,
                                               @QueryParameter("proxyHost") final String proxyHost,
                                               @QueryParameter("proxyPort") final String proxyPort,
                                               @AncestorInPath Job job) throws IOException, ServletException {
            // basic form validate
            AQFormValidate formValidate = new AQFormValidate();
            String res = formValidate.validateAppURL(appURL);
            if (res != null) {
                return FormValidation.error("ACCELQ App URL: " + res);
            }
            res = formValidate.validateUserId(userName);
            if (res != null) {
                return FormValidation.error("ACCELQ User ID: " + res);
            }
            res = formValidate.validateAPIKey(apiKey);
            if (res != null) {
                return FormValidation.error("API Key: " + res);
            }
            res = formValidate.validateProjectCode(projectCode);
            if (res != null) {
                return FormValidation.error("Project Code: " + res);
            }
            res = formValidate.validateTenantCode(tenantCode);
            if (res != null) {
                return FormValidation.error("Tenant Code: " + res);
            }
            res = formValidate.validateJobID(jobId);
            if (res != null) {
                return FormValidation.error("ACCELQ CI Job ID: " + res);
            }
            try {
                // make call to backend to validate it
                AQRestClient aqRestClient = null;
                AQUtils aqUtils = new AQUtils();
                aqRestClient = AQRestClient.getInstance();
                String payload = aqUtils.getRunParamJsonPayload(runParamStr);
                aqRestClient.setUpBaseURL(appURL, tenantCode, projectCode);
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
