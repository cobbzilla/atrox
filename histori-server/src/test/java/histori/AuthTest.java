package histori;

import cloudos.resources.AuthResourceBase;
import histori.model.Account;
import histori.model.auth.HistoriLoginRequest;
import histori.model.auth.RegistrationRequest;
import histori.model.support.AccountAuthResponse;
import org.cobbzilla.mail.sender.mock.MockTemplatedMailSender;
import org.cobbzilla.util.http.HttpStatusCodes;
import org.cobbzilla.wizard.auth.ResetPasswordRequest;
import org.junit.Test;

import static cloudos.resources.AuthResourceBase.*;
import static histori.ApiConstants.ACCOUNTS_ENDPOINT;
import static histori.ApiConstants.EP_LOGIN;
import static org.cobbzilla.util.json.JsonUtil.toJson;
import static org.cobbzilla.wizardtest.RandomUtil.randomEmail;
import static org.cobbzilla.wizardtest.RandomUtil.randomName;
import static org.junit.Assert.*;

public class AuthTest extends ApiClientTestBase {

    public static final String DOC_TARGET = "Authentication";

    @Test public void testCreateAnonymousAccount () throws Exception {
        apiDocs.startRecording(DOC_TARGET, "Register an anonymous account");
        newAnonymousAccount();
    }

    @Test public void testCreateAnonymousAccountThenRegisterAndVerify () throws Exception {

        apiDocs.startRecording(DOC_TARGET, "Register an anonymous account, then register it as a normal account and verify the email address");
        AccountAuthResponse anonResponse = newAnonymousAccount();
        final MockTemplatedMailSender mockSender = getTemplatedMailSender();
        mockSender.reset();

        String email = randomEmail();
        String password = randomName();

        apiDocs.addNote("Call register again, and because we are logged in anonymously, this should register the current account as a normal account");
        AccountAuthResponse normalResponse = register(new RegistrationRequest(email, password), false);

        assertNotEquals(anonResponse.getSessionId(), normalResponse.getSessionId()); // a new session
        assertEquals(anonResponse.getAccount().getUuid(), normalResponse.getAccount().getUuid()); // but the same account uuid
        assertFalse(normalResponse.getAccount().isAnonymous()); // and is no longer anonymous

        assertEquals(1, mockSender.messageCount());
        assertEquals(email, mockSender.first().getToEmail());
        final String url = mockSender.first().getParameters().get("activationUrl").toString();
        final String token = url.substring(url.lastIndexOf("/")+1);

        apiDocs.addNote("Verify email address using token from welcome message");
        get(ACCOUNTS_ENDPOINT + EP_ACTIVATE + "/" + token);

        apiDocs.addNote("Retrieve account information, email should now be activated");
        final Account account = get(ACCOUNTS_ENDPOINT, Account.class);
        assertEquals(email, account.getEmail());
        assertTrue(account.isEmailVerified());
    }

    @Test public void testForgotPassword () throws Exception {

        apiDocs.startRecording(DOC_TARGET, "Register a normal account, use forgot-password function to reset password and login again");

        String email = randomEmail();
        String origPassword = randomName();

        apiDocs.addNote("Register a normal account");
        AccountAuthResponse response = register(new RegistrationRequest(email, origPassword));
        assertTrue(response.hasSessionId());
        assertFalse(response.getAccount().isAnonymous());
        assertFalse(response.getAccount().isEmailVerified());
        final Account account = response.getAccount();

        // reset mock mail sender -- discards welcome message
        final MockTemplatedMailSender mockSender = getTemplatedMailSender();
        mockSender.reset();

        logout(); // discards session token

        apiDocs.addNote("Trigger 'forgot password' to send us an email");
        assertTrue(emptyObject(post(ACCOUNTS_ENDPOINT + EP_FORGOT_PASSWORD, email).json));

        assertEquals(1, mockSender.messageCount());
        assertEquals(email, mockSender.first().getToEmail());
        final String url = mockSender.first().getParameters().get(AuthResourceBase.PARAM_RESETPASSWORD_URL).toString();
        final String token = getConfiguration().getTokenFromResetPasswordUrl(url);

        apiDocs.addNote("Reset password using token found in email");
        final String newPassword = randomName();
        final ResetPasswordRequest resetPasswordRequest = new ResetPasswordRequest(token, newPassword);
        assertTrue(emptyObject(post(ACCOUNTS_ENDPOINT + EP_RESET_PASSWORD, toJson(resetPasswordRequest)).json));

        apiDocs.addNote("Try to login with the old password, does not work");
        final HistoriLoginRequest loginRequest = new HistoriLoginRequest(email, origPassword);
        assertEquals(HttpStatusCodes.NOT_FOUND, doPost(ACCOUNTS_ENDPOINT + EP_LOGIN, toJson(loginRequest)).status);

        apiDocs.addNote("Try to login with the new password, success");
        loginRequest.setPassword(newPassword);
        response = post(ACCOUNTS_ENDPOINT + EP_LOGIN, loginRequest, AccountAuthResponse.class);
        assertEquals(account.getUuid(), response.getAccount().getUuid());
    }

    protected boolean emptyObject(String json) {
        return json.replaceAll("\\s+", "").equals("{}");
    }

}
