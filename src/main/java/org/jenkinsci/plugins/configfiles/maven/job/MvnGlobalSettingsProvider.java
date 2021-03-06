package org.jenkinsci.plugins.configfiles.maven.job;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import jenkins.model.Jenkins;
import jenkins.mvn.GlobalSettingsProvider;
import jenkins.mvn.GlobalSettingsProviderDescriptor;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.common.CleanTempFilesAction;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig;
import org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig.GlobalMavenSettingsConfigProvider;
import org.jenkinsci.plugins.configfiles.maven.security.CredentialsHelper;
import org.kohsuke.stapler.DataBoundConstructor;

import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;

/**
 * This provider delivers the global settings.xml to the job during job/project execution. <br>
 * <b>Important: Do not rename this class!!</b> For backward compatibility, this class is also created via reflection from the maven-plugin.
 * 
 * @author Dominik Bartholdi (imod)
 */
public class MvnGlobalSettingsProvider extends GlobalSettingsProvider {

    private String settingsConfigId;

    /**
     * Default constructor used to load class via reflection by the maven-plugin for backward compatibility
     */
    @Deprecated
    public MvnGlobalSettingsProvider() {
    }

    @DataBoundConstructor
    public MvnGlobalSettingsProvider(String settingsConfigId) {
        this.settingsConfigId = settingsConfigId;
    }

    public String getSettingsConfigId() {
        return settingsConfigId;
    }

    public void setSettingsConfigId(String settingsConfigId) {
        this.settingsConfigId = settingsConfigId;
    }

    @Override
    public FilePath supplySettings(AbstractBuild<?, ?> build, TaskListener listener) {
        if (StringUtils.isNotBlank(settingsConfigId)) {

            Config c = Config.getByIdOrNull(settingsConfigId);

            if (c == null) {
                listener.getLogger().println("ERROR: your Apache Maven build is setup to use a config with id " + settingsConfigId + " but can not find the config");
            } else {

                GlobalMavenSettingsConfig config;
                if (c instanceof GlobalMavenSettingsConfig) {
                    config = (GlobalMavenSettingsConfig) c;
                } else {
                    config = new GlobalMavenSettingsConfig(c.id, c.name, c.comment, c.content,  GlobalMavenSettingsConfig.isReplaceAllDefault, null);
                }

                listener.getLogger().println("using global settings config with name " + config.name);
                listener.getLogger().println("Replacing all maven server entries not found in credentials list is " + config.getIsReplaceAll());
                if (StringUtils.isNotBlank(config.content)) {
                    try {

                        String fileContent = config.content;

                        final Map<String, StandardUsernameCredentials> resolvedCredentials = CredentialsHelper.resolveCredentials(build.getProject(), config.getServerCredentialMappings());
                        final Boolean isReplaceAll = config.getIsReplaceAll();

                        if (!resolvedCredentials.isEmpty()) {
                            fileContent = CredentialsHelper.fillAuthentication(fileContent, isReplaceAll, resolvedCredentials);
                        }

                        final FilePath f = copyConfigContentToFilePath(fileContent, build.getWorkspace());
                        build.getEnvironments().add(new SimpleEnvironment("MVN_GLOBALSETTINGS", f.getRemote()));

                        // Temporarily attach info about the files to be deleted to the build - this action gets removed from the build again by
                        // 'org.jenkinsci.plugins.configfiles.common.CleanTempFilesRunListener'
                        build.addAction(new CleanTempFilesAction(f.getRemote()));
                        return f;
                    } catch (Exception e) {
                        throw new IllegalStateException("the global settings.xml could not be supplied for the current build: " + e.getMessage());
                    }
                }
            }
        }

        return null;
    }

    public static FilePath copyConfigContentToFilePath(String content, FilePath workspace) throws IOException, InterruptedException {
        return workspace.createTextTempFile("global-settings", ".xml", content, false);
    }

    @Extension(ordinal = 10)
    public static class DescriptorImpl extends GlobalSettingsProviderDescriptor {

        @Override
        public String getDisplayName() {
            return "provided global settings.xml";
        }

        public Collection<Config> getAllGlobalMavenSettingsConfigs() {
            final ExtensionList<GlobalMavenSettingsConfigProvider> configProviders = Jenkins.getInstance().getExtensionList(GlobalMavenSettingsConfigProvider.class);
            if (configProviders != null && configProviders.size() > 0) {
                // there is only one implementation...
                return configProviders.get(0).getAllConfigs();
            }
            return Collections.emptyList();
        }

    }

}
