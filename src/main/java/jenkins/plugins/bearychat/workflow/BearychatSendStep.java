package jenkins.plugins.bearychat.workflow;

//import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
//import com.cloudbees.plugins.credentials.domains.HostnameRequirement;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Project;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.bearychat.Messages;
import jenkins.plugins.bearychat.BearychatNotifier;
import jenkins.plugins.bearychat.BearychatService;
import jenkins.plugins.bearychat.StandardBearychatService;
//import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.inject.Inject;

//import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

/**
 * Workflow step to send a Bearychat channel notification.
 */
public class BearychatSendStep extends AbstractStepImpl {

    private final @Nonnull String message;
    private String color;
    private String token;
    private String tokenCredentialId;
    private boolean botUser;
    private String channel;
    private String teamDomain;
    private boolean failOnError;


    @Nonnull
    public String getMessage() {
        return message;
    }

    public String getColor() {
        return color;
    }

    @DataBoundSetter
    public void setColor(String color) {
        this.color = Util.fixEmpty(color);
    }

    public String getToken() {
        return token;
    }

    @DataBoundSetter
    public void setToken(String token) {
        this.token = Util.fixEmpty(token);
    }

    public String getTokenCredentialId() {
        return tokenCredentialId;
    }

    @DataBoundSetter
    public void setTokenCredentialId(String tokenCredentialId) {
        this.tokenCredentialId = Util.fixEmpty(tokenCredentialId);
    }

    public boolean getBotUser() {
        return botUser;
    }

    @DataBoundSetter
    public void setBotUser(boolean botUser) {
        this.botUser = botUser;
    }

    public String getChannel() {
        return channel;
    }

    @DataBoundSetter
    public void setChannel(String channel) {
        this.channel = Util.fixEmpty(channel);
    }

    public String getTeamDomain() {
        return teamDomain;
    }

    @DataBoundSetter
    public void setTeamDomain(String teamDomain) {
        this.teamDomain = Util.fixEmpty(teamDomain);
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    @DataBoundSetter
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    @DataBoundConstructor
    public BearychatSendStep(@Nonnull String message) {
        this.message = message;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(BearychatSendStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "bearychatSend";
        }

        @Override
        public String getDisplayName() {
            return Messages.BearychatSendStepDisplayName();
        }

        //WARN users that they should not use the plain/exposed token, but rather the token credential id
        public FormValidation doCheckToken(@QueryParameter String value) {
            //always show the warning - TODO investigate if there is a better way to handle this
            return FormValidation.warning("Exposing your Integration Token is a security risk. Please use the Integration Token Credential ID");
        }
    }

    public static class BearychatSendStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @Inject
        transient BearychatSendStep step;

        @StepContextParameter
        transient TaskListener listener;

        @Override
        protected Void run() throws Exception {

            //default to global config values if not set in step, but allow step to override all global settings
            Jenkins jenkins;
            //Jenkins.getInstance() may return null, no message sent in that case
            try {
                jenkins = Jenkins.getInstance();
            } catch (NullPointerException ne) {
                listener.error(Messages.NotificationFailedWithException(ne));
                return null;
            }
            BearychatNotifier.DescriptorImpl bearychatDesc = jenkins.getDescriptorByType(BearychatNotifier.DescriptorImpl.class);
            listener.getLogger().println("run BearychatSendStep, step " + step.token+":" + step.botUser+", desc ");
            String team = step.teamDomain != null ? step.teamDomain : bearychatDesc.getTeamDomain();
            //String tokenCredentialId = step.tokenCredentialId != null ? step.tokenCredentialId : bearychatDesc.getTokenCredentialId();
            
            String token;
            if (step.token != null) {
                token = step.token;
            } else {
                token = bearychatDesc.getToken();
            }
            String channel = step.channel != null ? step.channel : bearychatDesc.getRoom();
            
            String color = step.color != null ? step.color : "";

            //placing in console log to simplify testing of retrieving values from global config or from step field; also used for tests
            listener.getLogger().println(Messages.BearychatSendStepConfig(step.teamDomain == null, step.token == null, step.channel == null, step.color == null));

            BearychatService bearychatService = getBearychatService(team, token, channel);
            boolean publishSuccess = bearychatService.publish(step.message, color);
            if (!publishSuccess && step.failOnError) {
                throw new AbortException(Messages.NotificationFailed());
            } else if (!publishSuccess) {
                listener.error(Messages.NotificationFailed());
            }
            return null;
        }
        //streamline unit testing
        
        BearychatService getBearychatService(String team, String token, String channel) {
            return new StandardBearychatService(team, token, channel);
        }
    }
}