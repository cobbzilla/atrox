package atrox;

import org.junit.Test;

public class DbInit extends ApiClientTestBase {

    @Override protected boolean skipAdminCreation() { return true; }

    @Test public void init () throws Exception {
        docsEnabled = false;
        System.out.println("foo");
    }

}
