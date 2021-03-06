package histori.dao;

import cloudos.model.AccountBase;
import histori.ApiConstants;
import histori.dao.archive.NexusArchiveDAO;
import histori.dao.archive.VoteArchiveDAO;
import histori.dao.internal.AuditLogDAO;
import histori.dao.shard.AccountShardDAO;
import histori.model.Account;
import histori.model.MapImage;
import histori.model.auth.RegistrationRequest;
import histori.server.HistoriConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.mail.SimpleEmailMessage;
import org.cobbzilla.mail.TemplatedMail;
import org.cobbzilla.mail.service.TemplatedMailService;
import org.cobbzilla.wizard.asset.AssetStorageService;
import org.cobbzilla.wizard.auth.AuthenticationException;
import org.cobbzilla.wizard.auth.LoginRequest;
import org.cobbzilla.wizard.dao.BasicAccountDAO;
import org.cobbzilla.wizard.model.HashedPassword;
import org.cobbzilla.wizard.server.config.DatabaseConfiguration;
import org.cobbzilla.wizard.server.config.ShardSetConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;

import static cloudos.model.AccountBase.canonicalizeEmail;
import static histori.ApiConstants.ERR_CAPTCHA_INCORRECT;
import static histori.ApiConstants.anonymousEmail;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.cobbzilla.mail.service.TemplatedMailService.PARAM_ACCOUNT;
import static org.cobbzilla.mail.service.TemplatedMailService.T_WELCOME;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.wizard.resources.ResourceUtil.invalidEx;

@Repository @Slf4j
public class AccountDAO extends ShardedEntityDAO<Account, AccountShardDAO> implements BasicAccountDAO<Account> {

    @Autowired private HistoriConfiguration configuration;
    @Autowired private TemplatedMailService mailService;
    @Autowired private AuditLogDAO audit;

    @Autowired private DatabaseConfiguration database;
    @Override public ShardSetConfiguration getShardConfiguration() { return database.getShard("histori-account"); }

    public Account findByEmail(String email) {
        return findByUniqueField("canonicalEmail", canonicalizeEmail(email), false);
    }

    public Account findByNameOrEmail(String name) {
        final Account account = findByName(name, false);
        return account != null ? account : findByEmail(name);
    }

    @Override public Object preCreate(@Valid Account account) {
        if (empty(account.getName())) account.setName(account.getEmail());
        account.setFirstName(".");
        account.setLastName(".");
        if (account.getHashedPassword() == null) account.setHashedPassword(new HashedPassword(randomAlphanumeric(30)));
        account.setMobilePhone(ApiConstants.PLACEHOLDER_MOBILE_PHONE);
        account.setMobilePhoneCountryCode(1);
        return super.preCreate(account);
    }

    public Account authenticate(LoginRequest login) throws AuthenticationException {

        final String name = login.getName();
        final Account account = findByNameOrEmail(name);

        audit.log(login, "authenticate", "starting for '"+name+"'");
        if (empty(name) || account == null) {
            audit.log(login, "authenticate", "no account found: '"+ name + "', return null");
            return null;
        }

        if (account.getHashedPassword().isCorrectPassword(login.getPassword())) {
            audit.log(login, "authenticate", "successful password login for " + name);
            return account;
        }
        audit.log(login, "authenticate", "unsuccessful login for "+ name+", return null");
        return null;
    }

    @Override public Account findByActivationKey(String key) {
        return findByUniqueField(AccountBase.EMAIL_VERIFICATION_CODE, key);
    }

    @Override public Account findByResetPasswordToken(String token) {
        return findByUniqueField("hashedPassword."+AccountBase.RESET_PASSWORD_TOKEN, token);
    }

    @Override public void setPassword(Account account, String newPassword) {
        account.setResetToken(null);
        account.setPassword(newPassword);
        update(account);
    }

    public Account register(RegistrationRequest request) {
        final String email = request.getEmail();
        final String name = request.getName();
        final String password = request.getPassword();

        audit.log(request, "register", "starting for '"+name+"'");

        if (empty(email) || empty(name) || empty(password)) {
            audit.log(request, "register", "no name ("+name+"), email ("+email+") or password, returning anonymous account");
            return anonymousAccount();
        }

        Account account = findByEmail(email);
        if (account != null) {
            audit.log(request, "register", "email exists ("+email+"), returning error");
            throw invalidEx("err.email.notUnique", "Email was not unique");
        }
        account = findByName(name);
        if (account != null) {
            audit.log(request, "register", "name exists ("+name+"), returning error");
            throw invalidEx("err.name.notUnique", "Name was not unique");
        }

        if (!configuration.getRecaptcha().verify(request.getCaptcha())) {
            log.warn("register: captcha failed, returning invalid");
            throw invalidEx(ERR_CAPTCHA_INCORRECT);
        }

        audit.log(request, "register", "creating account for: '"+ name + "'");

        Account newAccount = (Account) new Account()
                .setSubscriber(request.isSubscribe())
                .setEmail(email)
                .setHashedPassword(new HashedPassword(password))
                .setName(name);
        newAccount.initEmailVerificationCode();

        newAccount = create(newAccount);
        sendWelcomeEmail(newAccount);

        return newAccount;
    }

    public Account registerAnonymous(Account account, RegistrationRequest request) {

        final String email = request.getEmail();
        final String name = request.getName();
        final String password = request.getPassword();

        audit.log(request, "registerAnonymous", "starting for '"+name+"'");
        if (empty(name) || empty(password)) {
            audit.log(request, "registerAnonymous", "no name ("+name+") or password, returning null");
            return null;
        }

        final Account withName = findByName(request.getName());
        if (withName != null) {
            audit.log(request, "registerAnonymous", "name exists ("+name+"), returning error");
            throw invalidEx("err.name.notUnique", "Name was not unique");
        }

        final Account withEmail = findByEmail(request.getEmail());
        if (withEmail != null) {
            audit.log(request, "registerAnonymous", "anon account does not exist ("+account.getEmail()+"), returning error");
            throw invalidEx("err.email.notUnique", "Email was not unique");
        }

        final Account anonAccount = findByUuid(account.getUuid());

        if (!anonAccount.isAnonymous()) {
            audit.log(request, "registerAnonymous", "name ("+name+") is not anon user ("+anonAccount.getEmail()+"), returning error");
            throw invalidEx("err.email.notAnonymous", "Account was not anonymous, cannot convert to regular account");
        }

        if (!configuration.getRecaptcha().verify(request.getCaptcha())) {
            log.warn("register: captcha failed, returning invalid");
            throw invalidEx(ERR_CAPTCHA_INCORRECT);
        }

        audit.log(request, "registerAnonymous", "anon user "+anonAccount.getEmail()+" now registered as "+name+"");
        anonAccount.setName(name);
        anonAccount.setEmail(email);
        anonAccount.setPassword(request.getPassword());
        anonAccount.setAnonymous(false);
        anonAccount.initEmailVerificationCode();

        final Account updated = update(anonAccount);
        sendWelcomeEmail(updated);

        return updated;
    }

    private void sendWelcomeEmail(Account account) {
        final SimpleEmailMessage welcomeSender = configuration.getEmailSenderNames().get(T_WELCOME);
        final String code = account.initEmailVerificationCode();
        final TemplatedMail mail = new TemplatedMail()
                .setToEmail(account.getEmail())
                .setToName(account.getName())
                .setFromName(welcomeSender.getFromName())
                .setFromEmail(welcomeSender.getFromEmail())
                .setTemplateName(T_WELCOME)
                .setParameter(PARAM_ACCOUNT, account)
                .setParameter("activationUrl", configuration.getPublicUriBase() + "/#/activate/" + code);
        try {
            mailService.getMailSender().deliverMessage(mail);
        } catch (Exception e) {
            log.error("sendWelcomeEmail: Error sending email: "+e, e);
        }
    }

    public Account anonymousAccount() {
        final Account account = new Account();
        account.setEmail(anonymousEmail());
        account.setAnonymous(true);
        return create(account);
    }

    public boolean adminsExist() { return !findByField("admin", true).isEmpty(); }

    // these are only needed for account removal
    @Autowired private NexusDAO nexusDAO;
    @Autowired private NexusArchiveDAO nexusArchiveDAO;
    @Autowired private VoteDAO voteDAO;
    @Autowired private VoteArchiveDAO voteArchiveDAO;
    @Autowired private BookmarkDAO bookmarkDAO;
    @Autowired private MapImageDAO mapImageDAO;

    public List<ShardedEntityDAO> getAccountOwnedDAOs () {
        return Arrays.asList(new ShardedEntityDAO[]{
                nexusDAO, nexusArchiveDAO, voteDAO, voteArchiveDAO, bookmarkDAO, mapImageDAO
        });
    }

    public void removeAccount(Account account) {
        // delete stored map images
        final List<MapImage> mapImages = mapImageDAO.findByOwner(account);
        final AssetStorageService storageService = configuration.getAssetStorageService();
        for (MapImage image : mapImages) {
            if (storageService.exists(image.getUri())) storageService.delete(image.getUri());
        }

        // delete all other account-owned entities
        for (ShardedEntityDAO dao : getAccountOwnedDAOs()) {
            dao.deleteAll("owner", account.getUuid());
        }

        // delete account
        delete(account.getUuid());
    }
}
