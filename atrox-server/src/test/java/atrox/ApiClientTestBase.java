package atrox;

import atrox.server.AtroxConfiguration;
import atrox.server.AtroxServer;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.mail.sender.mock.MockTemplatedMailSender;
import org.cobbzilla.mail.sender.mock.MockTemplatedMailService;
import org.cobbzilla.util.collection.SingletonList;
import org.cobbzilla.wizard.server.config.factory.ConfigurationSource;
import org.cobbzilla.wizard.server.config.factory.StreamConfigurationSource;
import org.cobbzilla.wizardtest.resources.ApiDocsResourceIT;

import java.util.List;

@Slf4j
public class ApiClientTestBase extends ApiDocsResourceIT<AtroxConfiguration, AtroxServer> {

    protected String getTestConfig() { return "atrox-config-test.yml"; }

    @Override protected List<ConfigurationSource> getConfigurations() {
        return new SingletonList<ConfigurationSource>(new StreamConfigurationSource(getTestConfig()));
    }

    @Override protected String getTokenHeader() { return ApiConstants.API_TOKEN; }

    public MockTemplatedMailService getTemplatedMailService() { return getBean(MockTemplatedMailService.class); }
    public MockTemplatedMailSender getTemplatedMailSender() { return (MockTemplatedMailSender) getTemplatedMailService().getMailSender(); }

    protected boolean skipAdminCreation() { return false; }

}
