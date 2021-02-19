package aqPluginCore;

public class AQConstants {
    public static final String LOG_DELIMITER = ">>> ";
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36";

    public static final long JOB_STATUS_POLL_TIME = 30 * 1000;
    public static final int JOB_PICKUP_RETRY_COUNT = 30;

    public static final String JOB_WEB_LINK = "#/forward?entityType=9&resultId=%s";
    public static final String EXT_JOB_WEB_LINK = "#/resultext?tenant=%s&project=%s&resultId=%s";


    public static final String API_VERSION = "1.0";
    public static final String AQ_RESULT_INFO_KEY = "AQReportInfo";



    public enum TEST_CASE_STATUS {
        PASS("pass"),
        FAIL("fail"),
        NOT_RUN("notRun"),
        RUNNING("running"),
        INFO("info"),
        FATAL("fatal"),
        WARN("warn"),
        ALL("all");

        private String status;
        TEST_CASE_STATUS(String status) {
            this.status = status;
        }
        public String getStatus() { return status; }
    }

    public enum TEST_JOB_STATUS {
        NOT_APPLICABLE("Not Applicable"),
        SCHEDULED("Scheduled"),
        IN_PROGRESS("In Progress"),
        COMPLETED("Completed"),
        ABORTED("Aborted"),
        FAILED("Failed To Start"),
        RECURRING("Recurring"),
        ERROR("Error"),
        CONTINUOUS_INTEGRATION("Continuous Integration");

        private String status;
        TEST_JOB_STATUS(String status) {
            this.status = status;
        }
        public String getStatus() { return status; }

    }
}